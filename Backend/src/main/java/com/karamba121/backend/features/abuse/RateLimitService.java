package com.karamba121.backend.features.abuse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.karamba121.backend.config.IdentityHubProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class RateLimitService {

    static final String REJECTION_METRIC = "identity_hub.abuse.rate_limit.rejections";

    private final Map<String, Window> windows = new HashMap<>();
    private final IdentityHubProperties.AbuseProtection policy;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private long requests;

    @Autowired
    public RateLimitService(IdentityHubProperties properties, MeterRegistry meterRegistry) {
        this(properties.abuseProtection(), meterRegistry, Clock.systemUTC());
    }

    RateLimitService(
            IdentityHubProperties.AbuseProtection policy,
            MeterRegistry meterRegistry,
            Clock clock) {
        validate(policy);
        this.policy = policy;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public void check(
            RateLimitedOperation operation,
            HttpServletRequest request,
            String subject) {
        check(operation, subject, request == null ? null : request.getRemoteAddr());
    }

    synchronized void check(RateLimitedOperation operation, String subject, String origin) {
        Instant now = clock.instant();
        if (++requests % 256 == 0 || windows.size() >= policy.maximumBuckets()) {
            windows.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
        }

        String normalizedSubject = normalizeSubject(subject);
        String normalizedOrigin = normalizeOrigin(origin);
        List<Signal> signals = List.of(
                new Signal("subject", normalizedSubject, policy.subjectLimit()),
                new Signal("origin", normalizedOrigin, policy.originLimit()),
                new Signal("combination", normalizedSubject + "\u0000" + normalizedOrigin,
                        policy.combinationLimit()));

        long missingBuckets = signals.stream()
                .map(signal -> key(operation, signal.type(), signal.value()))
                .filter(key -> currentWindow(key, now) == null)
                .count();
        if (windows.size() + missingBuckets > policy.maximumBuckets()) {
            reject(operation, "capacity", Math.max(1, policy.window().toSeconds()));
        }

        for (Signal signal : signals) {
            String key = key(operation, signal.type(), signal.value());
            Window window = currentWindow(key, now);
            if (window != null && window.count() >= signal.limit()) {
                reject(operation, signal.type(), retryAfter(window.expiresAt(), now));
            }
        }

        for (Signal signal : signals) {
            String key = key(operation, signal.type(), signal.value());
            Window window = currentWindow(key, now);
            windows.put(key, window == null
                    ? new Window(1, now.plus(policy.window()))
                    : new Window(window.count() + 1, window.expiresAt()));
        }
    }

    private Window currentWindow(String key, Instant now) {
        Window window = windows.get(key);
        if (window != null && !window.expiresAt().isAfter(now)) {
            windows.remove(key);
            return null;
        }
        return window;
    }

    private void reject(RateLimitedOperation operation, String signal, long retryAfterSeconds) {
        Counter.builder(REJECTION_METRIC)
                .description("Rejeições de operações públicas pelo limitador de abuso")
                .tag("operation", operation.metricTag())
                .tag("signal", signal)
                .register(meterRegistry)
                .increment();
        throw new RateLimitExceededException(retryAfterSeconds);
    }

    private static String key(RateLimitedOperation operation, String type, String value) {
        return hash(operation.name() + "\u0000" + type + "\u0000" + value);
    }

    private static String normalizeSubject(String subject) {
        if (subject == null || subject.isBlank()) {
            return "missing";
        }
        return subject.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return "unknown";
        }
        return origin.trim().toLowerCase(Locale.ROOT);
    }

    private static long retryAfter(Instant expiresAt, Instant now) {
        long millis = Duration.between(now, expiresAt).toMillis();
        return Math.max(1, (millis + 999) / 1_000);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private static void validate(IdentityHubProperties.AbuseProtection policy) {
        if (policy == null
                || policy.window() == null
                || policy.window().isZero()
                || policy.window().isNegative()
                || policy.subjectLimit() < 1
                || policy.originLimit() < 1
                || policy.combinationLimit() < 1
                || policy.maximumBuckets() < 3) {
            throw new IllegalArgumentException("Configuração de rate limiting inválida");
        }
    }

    private record Signal(String type, String value, int limit) {
    }

    private record Window(int count, Instant expiresAt) {
    }
}
