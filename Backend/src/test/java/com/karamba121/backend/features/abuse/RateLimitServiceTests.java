package com.karamba121.backend.features.abuse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.karamba121.backend.config.IdentityHubProperties;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class RateLimitServiceTests {

    @Test
    void combinesSubjectOriginAndPairWithoutStoringRawSignalsInMetrics() {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        RateLimitService limiter = limiter(metrics, new MutableClock(), 3, 5, 2, 100);

        limiter.check(RateLimitedOperation.LOGIN, "person@example.test", "192.0.2.10");
        limiter.check(RateLimitedOperation.LOGIN, " PERSON@example.test ", "192.0.2.10");
        assertThatThrownBy(() -> limiter.check(
                RateLimitedOperation.LOGIN, "person@example.test", "192.0.2.10"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Muitas tentativas. Aguarde antes de tentar novamente.");

        assertThat(metrics.get(RateLimitService.REJECTION_METRIC)
                .tag("operation", "login")
                .tag("signal", "combination")
                .counter()
                .count()).isEqualTo(1);
        assertThat(metrics.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue()))
                .doesNotContain("person@example.test", "192.0.2.10");
    }

    @Test
    void limitsARepeatedSubjectAcrossOriginsAndAnOriginAcrossSubjects() {
        RateLimitService subjectLimiter = limiter(new SimpleMeterRegistry(), new MutableClock(), 3, 10, 5, 100);
        subjectLimiter.check(RateLimitedOperation.REGISTRATION, "person@example.test", "192.0.2.1");
        subjectLimiter.check(RateLimitedOperation.REGISTRATION, "person@example.test", "192.0.2.2");
        subjectLimiter.check(RateLimitedOperation.REGISTRATION, "person@example.test", "192.0.2.3");
        assertThatThrownBy(() -> subjectLimiter.check(
                RateLimitedOperation.REGISTRATION, "person@example.test", "192.0.2.4"))
                .isInstanceOf(RateLimitExceededException.class);

        RateLimitService originLimiter = limiter(new SimpleMeterRegistry(), new MutableClock(), 10, 4, 5, 100);
        for (int index = 0; index < 4; index++) {
            originLimiter.check(
                    RateLimitedOperation.PASSWORD_RECOVERY_REQUEST,
                    "person-" + index + "@example.test",
                    "192.0.2.20");
        }
        assertThatThrownBy(() -> originLimiter.check(
                RateLimitedOperation.PASSWORD_RECOVERY_REQUEST,
                "another@example.test",
                "192.0.2.20"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void resetsExpiredWindowsAndFailsClosedAtTheMemoryBound() {
        MutableClock clock = new MutableClock();
        RateLimitService expiring = limiter(new SimpleMeterRegistry(), clock, 1, 1, 1, 100);
        expiring.check(RateLimitedOperation.EMAIL_VERIFICATION, "token-a", "192.0.2.30");
        assertThatThrownBy(() -> expiring.check(
                RateLimitedOperation.EMAIL_VERIFICATION, "token-a", "192.0.2.30"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(exception -> assertThat(((RateLimitExceededException) exception)
                        .getRetryAfterSeconds()).isEqualTo(60));
        clock.advance(Duration.ofSeconds(61));
        expiring.check(RateLimitedOperation.EMAIL_VERIFICATION, "token-a", "192.0.2.30");

        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        RateLimitService bounded = limiter(metrics, new MutableClock(), 10, 10, 10, 3);
        bounded.check(RateLimitedOperation.PASSWORD_RECOVERY_COMPLETE, "token-a", "192.0.2.40");
        assertThatThrownBy(() -> bounded.check(
                RateLimitedOperation.PASSWORD_RECOVERY_COMPLETE, "token-b", "192.0.2.41"))
                .isInstanceOf(RateLimitExceededException.class);
        assertThat(metrics.get(RateLimitService.REJECTION_METRIC)
                .tag("operation", "password_recovery_complete")
                .tag("signal", "capacity")
                .counter()
                .count()).isEqualTo(1);
    }

    private static RateLimitService limiter(
            SimpleMeterRegistry metrics,
            MutableClock clock,
            int subjectLimit,
            int originLimit,
            int combinationLimit,
            int maximumBuckets) {
        return new RateLimitService(
                new IdentityHubProperties.AbuseProtection(
                        Duration.ofMinutes(1),
                        subjectLimit,
                        originLimit,
                        combinationLimit,
                        maximumBuckets),
                metrics,
                clock);
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-03T12:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
