package com.karamba121.backend.features.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.config.IdentityHubProperties;
import com.karamba121.backend.features.access.SecurityAuditEventType;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Service
public class AdaptiveAuthenticationPolicyService {

    static final String METRIC_NAME = "identity_hub.authentication.adaptive.decisions";
    static final String RECENT_FAILURES = "RECENT_FAILURES";
    static final String SENSITIVE_SCOPE = "SENSITIVE_SCOPE";
    static final String ACTIVE_STEP_UP = "ACTIVE_STEP_UP";

    private final IdentityUserRepository users;
    private final IdentitySecurityAuditor auditor;
    private final MeterRegistry meterRegistry;
    private final boolean enabled;
    private final int failedAttemptThreshold;
    private final Duration signalWindow;
    private final Duration challengeTtl;
    private final Set<String> sensitiveScopes;
    private final Clock clock;

    @Autowired
    public AdaptiveAuthenticationPolicyService(
            IdentityUserRepository users,
            IdentitySecurityAuditor auditor,
            MeterRegistry meterRegistry,
            IdentityHubProperties properties) {
        this(users, auditor, meterRegistry, properties.adaptiveAuthentication(), Clock.systemUTC());
    }

    AdaptiveAuthenticationPolicyService(
            IdentityUserRepository users,
            IdentitySecurityAuditor auditor,
            MeterRegistry meterRegistry,
            IdentityHubProperties.AdaptiveAuthentication properties,
            Clock clock) {
        if (properties == null
                || properties.failedAttemptThreshold() < 1
                || properties.signalWindow() == null
                || properties.signalWindow().isZero()
                || properties.signalWindow().isNegative()
                || properties.challengeTtl() == null
                || properties.challengeTtl().isZero()
                || properties.challengeTtl().isNegative()) {
            throw new IllegalArgumentException("Configuração de autenticação adaptativa inválida");
        }
        this.users = users;
        this.auditor = auditor;
        this.meterRegistry = meterRegistry;
        this.enabled = properties.enabled();
        this.failedAttemptThreshold = properties.failedAttemptThreshold();
        this.signalWindow = properties.signalWindow();
        this.challengeTtl = properties.challengeTtl();
        this.sensitiveScopes = properties.sensitiveScopes() == null
                ? Set.of()
                : properties.sensitiveScopes().stream()
                        .filter(scope -> scope != null && !scope.isBlank())
                        .map(scope -> scope.trim().toLowerCase(Locale.ROOT))
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Signals capture(String email) {
        return users.findByEmailIgnoreCase(normalize(email))
                .map(user -> new Signals(
                        user.getFailedLoginAttempts(),
                        user.getLastFailedLoginAt()))
                .orElse(Signals.NONE);
    }

    @Transactional
    public Decision evaluate(
            String email,
            Set<String> requestedScopes,
            Signals signals,
            boolean strongFactorAvailable) {
        if (!enabled) {
            increment("allow", "disabled");
            return Decision.ALLOW;
        }
        IdentityUser user = users.findByEmailForUpdate(normalize(email))
                .orElseThrow(() -> new IllegalArgumentException("Identidade não encontrada"));
        Instant now = clock.instant();
        String reason = reason(user, requestedScopes, signals == null ? Signals.NONE : signals, now);
        if (reason == null) {
            if (user.getAdaptiveStepUpUntil() != null) user.clearAdaptiveStepUp();
            increment("allow", "low_risk");
            return Decision.ALLOW;
        }

        user.requireAdaptiveStepUpUntil(now.plus(challengeTtl));
        if (strongFactorAvailable) {
            auditor.recordAdaptiveEvent(email, SecurityAuditEventType.ADAPTIVE_STEP_UP_REQUIRED);
            increment("step_up", metricReason(reason));
            return Decision.REQUIRE_STRONG_FACTOR;
        }
        auditor.recordAdaptiveDenial(email, reason);
        increment("deny", metricReason(reason));
        return Decision.DENY;
    }

    @Transactional
    public void strongAuthenticationSucceeded(String email) {
        users.findByEmailForUpdate(normalize(email)).ifPresent(user -> {
            boolean adaptiveChallenge = user.requiresAdaptiveStepUp(clock.instant());
            user.clearAdaptiveStepUp();
            if (adaptiveChallenge) {
                auditor.recordAdaptiveEvent(email, SecurityAuditEventType.ADAPTIVE_STEP_UP_SUCCEEDED);
                increment("success", "strong_factor");
            }
        });
    }

    private String reason(IdentityUser user, Set<String> requestedScopes, Signals signals, Instant now) {
        if (user.requiresAdaptiveStepUp(now)) return ACTIVE_STEP_UP;
        if (signals.failedAttempts() >= failedAttemptThreshold
                && signals.lastFailedAt() != null
                && !signals.lastFailedAt().isBefore(now.minus(signalWindow))) {
            return RECENT_FAILURES;
        }
        if (requestedScopes != null && requestedScopes.stream()
                .filter(scope -> scope != null)
                .map(scope -> scope.toLowerCase(Locale.ROOT))
                .anyMatch(sensitiveScopes::contains)) {
            return SENSITIVE_SCOPE;
        }
        return null;
    }

    private void increment(String outcome, String reason) {
        Counter.builder(METRIC_NAME)
                .tag("outcome", outcome)
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String metricReason(String reason) {
        return reason.toLowerCase(Locale.ROOT);
    }

    public enum Decision {
        ALLOW,
        REQUIRE_STRONG_FACTOR,
        DENY
    }

    public record Signals(int failedAttempts, Instant lastFailedAt) {
        static final Signals NONE = new Signals(0, null);
    }
}
