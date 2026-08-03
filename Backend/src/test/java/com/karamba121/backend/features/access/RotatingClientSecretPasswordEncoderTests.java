package com.karamba121.backend.features.access;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class RotatingClientSecretPasswordEncoderTests {

    @Test
    void acceptsCurrentAndPreviousSecretOnlyDuringWindow() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-03T12:00:00Z"));
        RotatingClientSecretPasswordEncoder encoder = new RotatingClientSecretPasswordEncoder(
                new BCryptPasswordEncoder(4), clock);
        String original = "original-secret";
        String encodedOriginal = encoder.encode(original);

        var rotation = encoder.rotate("new-secret", encodedOriginal, Duration.ofMinutes(15));

        assertThat(rotation.encodedSecret()).startsWith("{rotating}");
        assertThat(rotation.encodedSecret()).doesNotContain(original, "new-secret");
        assertThat(encoder.matches("new-secret", rotation.encodedSecret())).isTrue();
        assertThat(encoder.matches(original, rotation.encodedSecret())).isTrue();
        assertThat(encoder.previousSecretExpiresAt(rotation.encodedSecret()))
                .contains(Instant.parse("2026-08-03T12:15:00Z"));

        clock.advance(Duration.ofMinutes(15));

        assertThat(encoder.matches("new-secret", rotation.encodedSecret())).isTrue();
        assertThat(encoder.matches(original, rotation.encodedSecret())).isFalse();
    }

    @Test
    void consecutiveRotationRetainsOnlyImmediatePreviousSecret() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-03T12:00:00Z"));
        RotatingClientSecretPasswordEncoder encoder = new RotatingClientSecretPasswordEncoder(
                new BCryptPasswordEncoder(4), clock);
        var first = encoder.rotate("second", encoder.encode("first"), Duration.ofMinutes(30));
        var second = encoder.rotate("third", first.encodedSecret(), Duration.ofMinutes(30));

        assertThat(encoder.matches("third", second.encodedSecret())).isTrue();
        assertThat(encoder.matches("second", second.encodedSecret())).isTrue();
        assertThat(encoder.matches("first", second.encodedSecret())).isFalse();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
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
