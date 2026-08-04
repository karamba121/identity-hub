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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.karamba121.backend.config.IdentityHubProperties;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class RateLimitService {

    static final String REJECTION_METRIC = "identity_hub.abuse.rate_limit.rejections";
    static final String BACKEND_FAILURE_METRIC = "identity_hub.abuse.rate_limit.backend_failures";
    private static final DefaultRedisScript<String> REDIS_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local maximum = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)

            local missing = 0
            for index = 2, 4 do
              if redis.call('EXISTS', KEYS[index]) == 0 then missing = missing + 1 end
            end
            if redis.call('ZCARD', KEYS[1]) + missing > maximum then
              return 'capacity:' .. window
            end

            for index = 2, 4 do
              local count = tonumber(redis.call('GET', KEYS[index]) or '0')
              local limit = tonumber(ARGV[index + 2])
              if count >= limit then
                local ttl = redis.call('PTTL', KEYS[index])
                if ttl < 1 then ttl = window end
                return tostring(index - 1) .. ':' .. tostring(ttl)
              end
            end

            for index = 2, 4 do
              local count = redis.call('INCR', KEYS[index])
              if count == 1 then
                redis.call('PEXPIRE', KEYS[index], window)
                redis.call('ZADD', KEYS[1], now + window, KEYS[index])
              end
            end
            return 'ok'
            """, String.class);

    private final Map<String, Window> windows = new HashMap<>();
    private final IdentityHubProperties.AbuseProtection policy;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final StringRedisTemplate redis;
    private long requests;

    @Autowired
    public RateLimitService(
            IdentityHubProperties properties,
            MeterRegistry meterRegistry,
            ObjectProvider<StringRedisTemplate> redis) {
        this(properties.abuseProtection(), meterRegistry, Clock.systemUTC(), redis.getIfAvailable());
    }

    RateLimitService(
            IdentityHubProperties.AbuseProtection policy,
            MeterRegistry meterRegistry,
            Clock clock) {
        this(policy, meterRegistry, clock, null);
    }

    RateLimitService(
            IdentityHubProperties.AbuseProtection policy,
            MeterRegistry meterRegistry,
            Clock clock,
            StringRedisTemplate redis) {
        validate(policy);
        this.policy = policy;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.redis = redis;
        if ("redis".equalsIgnoreCase(policy.store()) && redis == null) {
            throw new IllegalArgumentException("Rate limiting Redis exige StringRedisTemplate");
        }
    }

    public void check(
            RateLimitedOperation operation,
            HttpServletRequest request,
            String subject) {
        check(operation, subject, request == null ? null : request.getRemoteAddr());
    }

    synchronized void check(RateLimitedOperation operation, String subject, String origin) {
        if ("redis".equalsIgnoreCase(policy.store())) {
            checkRedis(operation, subject, origin);
            return;
        }
        checkMemory(operation, subject, origin);
    }

    private void checkMemory(RateLimitedOperation operation, String subject, String origin) {
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

    private void checkRedis(RateLimitedOperation operation, String subject, String origin) {
        List<Signal> signals = signals(subject, origin);
        String prefix = "{identity-hub-rate-limit}:" + operation.name().toLowerCase(Locale.ROOT) + ":";
        List<String> keys = new java.util.ArrayList<>();
        keys.add("{identity-hub-rate-limit}:active");
        signals.forEach(signal -> keys.add(prefix + signal.type() + ":" + hash(signal.value())));
        try {
            String result = redis.execute(
                    REDIS_SCRIPT,
                    keys,
                    Long.toString(clock.millis()),
                    Long.toString(policy.window().toMillis()),
                    Integer.toString(policy.maximumBuckets()),
                    Integer.toString(signals.get(0).limit()),
                    Integer.toString(signals.get(1).limit()),
                    Integer.toString(signals.get(2).limit()));
            if (result == null || "ok".equals(result)) return;
            String[] rejection = result.split(":", 2);
            long retryAfter = millisToSeconds(Long.parseLong(rejection[1]));
            if ("capacity".equals(rejection[0])) {
                reject(operation, "capacity", retryAfter);
            }
            int signalIndex = Integer.parseInt(rejection[0]) - 1;
            reject(operation, signals.get(signalIndex).type(), retryAfter);
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            meterRegistry.counter(BACKEND_FAILURE_METRIC).increment();
            throw new RateLimitBackendUnavailableException(exception);
        }
    }

    private List<Signal> signals(String subject, String origin) {
        String normalizedSubject = normalizeSubject(subject);
        String normalizedOrigin = normalizeOrigin(origin);
        return List.of(
                new Signal("subject", normalizedSubject, policy.subjectLimit()),
                new Signal("origin", normalizedOrigin, policy.originLimit()),
                new Signal("combination", normalizedSubject + "\u0000" + normalizedOrigin,
                        policy.combinationLimit()));
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

    private static long millisToSeconds(long millis) {
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
                || !("memory".equalsIgnoreCase(policy.store()) || "redis".equalsIgnoreCase(policy.store()))
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
