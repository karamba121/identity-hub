package com.karamba121.backend.features.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.karamba121.backend.config.IdentityHubProperties;
import com.karamba121.backend.features.access.SecurityAuditEventType;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class AdaptiveAuthenticationPolicyServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-04T12:00:00Z");
    private static final String EMAIL = "adaptive@example.test";

    @Mock IdentityUserRepository users;
    @Mock IdentitySecurityAuditor auditor;

    private SimpleMeterRegistry metrics;
    private AdaptiveAuthenticationPolicyService service;
    private IdentityUser user;

    @BeforeEach
    void setUp() {
        metrics = new SimpleMeterRegistry();
        service = new AdaptiveAuthenticationPolicyService(
                users,
                auditor,
                metrics,
                new IdentityHubProperties.AdaptiveAuthentication(
                        true, 3, Duration.ofMinutes(15), Duration.ofMinutes(5), Set.of("identity.admin")),
                Clock.fixed(NOW, ZoneOffset.UTC));
        user = new IdentityUser(EMAIL, "Adaptive User", "{noop}not-used");
        when(users.findByEmailForUpdate(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void keepsRecentFailureStepUpActiveAfterPasswordAuthenticationResetsTheCounter() {
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        registerFailures(3);
        AdaptiveAuthenticationPolicyService.Signals signals = service.capture(EMAIL);
        user.resetLoginFailures();

        assertThat(service.evaluate(EMAIL, Set.of("openid"), signals, false))
                .isEqualTo(AdaptiveAuthenticationPolicyService.Decision.DENY);
        assertThat(user.getAdaptiveStepUpUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
        assertThat(service.evaluate(
                EMAIL,
                Set.of("openid"),
                new AdaptiveAuthenticationPolicyService.Signals(0, null),
                false))
                .isEqualTo(AdaptiveAuthenticationPolicyService.Decision.DENY);

        verify(auditor).recordAdaptiveDenial(EMAIL, AdaptiveAuthenticationPolicyService.RECENT_FAILURES);
        verify(auditor).recordAdaptiveDenial(EMAIL, AdaptiveAuthenticationPolicyService.ACTIVE_STEP_UP);
        assertThat(counter("deny", "recent_failures")).isEqualTo(1.0);
        assertThat(counter("deny", "active_step_up")).isEqualTo(1.0);
    }

    @Test
    void requiresAndAuditsStrongFactorForSensitiveScope() {
        assertThat(service.evaluate(
                EMAIL,
                Set.of("openid", "identity.admin"),
                new AdaptiveAuthenticationPolicyService.Signals(0, null),
                true))
                .isEqualTo(AdaptiveAuthenticationPolicyService.Decision.REQUIRE_STRONG_FACTOR);

        verify(auditor).recordAdaptiveEvent(EMAIL, SecurityAuditEventType.ADAPTIVE_STEP_UP_REQUIRED);
        service.strongAuthenticationSucceeded(EMAIL);
        assertThat(user.getAdaptiveStepUpUntil()).isNull();
        verify(auditor).recordAdaptiveEvent(EMAIL, SecurityAuditEventType.ADAPTIVE_STEP_UP_SUCCEEDED);
        assertThat(counter("step_up", "sensitive_scope")).isEqualTo(1.0);
        assertThat(counter("success", "strong_factor")).isEqualTo(1.0);
    }

    @Test
    void allowsLowRiskPasswordWithoutPersistingAChallenge() {
        assertThat(service.evaluate(
                EMAIL,
                Set.of("openid", "profile"),
                new AdaptiveAuthenticationPolicyService.Signals(0, null),
                false))
                .isEqualTo(AdaptiveAuthenticationPolicyService.Decision.ALLOW);
        assertThat(user.getAdaptiveStepUpUntil()).isNull();
        assertThat(counter("allow", "low_risk")).isEqualTo(1.0);
    }

    private void registerFailures(int count) {
        for (int attempt = 0; attempt < count; attempt++) {
            user.registerFailedLogin(
                    NOW.minusSeconds(30),
                    5,
                    Duration.ofMinutes(1),
                    Duration.ofMinutes(15));
        }
    }

    private double counter(String outcome, String reason) {
        return metrics.get(AdaptiveAuthenticationPolicyService.METRIC_NAME)
                .tags("outcome", outcome, "reason", reason)
                .counter()
                .count();
    }
}
