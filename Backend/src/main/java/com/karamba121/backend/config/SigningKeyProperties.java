package com.karamba121.backend.config;

import java.time.Duration;
import java.time.Instant;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity-hub.signing-key")
public record SigningKeyProperties(
        Source source,
        String keyId,
        String privateKeyLocation,
        String publicKeyLocation,
        String nextKeyId,
        String nextPrivateKeyLocation,
        String nextPublicKeyLocation,
        Instant activationAt,
        Duration previousKeyRetention) {

    private static final Duration DEFAULT_PREVIOUS_KEY_RETENTION = Duration.ofMinutes(10);
    private static final Duration MINIMUM_PREVIOUS_KEY_RETENTION = Duration.ofMinutes(5);
    private static final Duration MAXIMUM_PREVIOUS_KEY_RETENTION = Duration.ofDays(7);

    public SigningKeyProperties {
        source = source == null ? Source.GENERATED : source;
        keyId = normalize(keyId);
        privateKeyLocation = normalize(privateKeyLocation);
        publicKeyLocation = normalize(publicKeyLocation);
        nextKeyId = normalize(nextKeyId);
        nextPrivateKeyLocation = normalize(nextPrivateKeyLocation);
        nextPublicKeyLocation = normalize(nextPublicKeyLocation);
        previousKeyRetention = previousKeyRetention == null
                ? DEFAULT_PREVIOUS_KEY_RETENTION
                : previousKeyRetention;
        validateKeyId(keyId, "key-id");
        validateKeyId(nextKeyId, "next-key-id");
        if (previousKeyRetention.compareTo(MINIMUM_PREVIOUS_KEY_RETENTION) < 0
                || previousKeyRetention.compareTo(MAXIMUM_PREVIOUS_KEY_RETENTION) > 0) {
            throw new IllegalArgumentException(
                    "identity-hub.signing-key.previous-key-retention deve estar entre 5 minutos e 7 dias");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateKeyId(String value, String field) {
        if (value != null && value.length() > 128) {
            throw new IllegalArgumentException("identity-hub.signing-key." + field + " excede 128 caracteres");
        }
    }

    public enum Source {
        GENERATED,
        PEM,
        ROTATING_PEM
    }
}
