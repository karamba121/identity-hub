package com.karamba121.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity-hub.signing-key")
public record SigningKeyProperties(
        Source source,
        String keyId,
        String privateKeyLocation,
        String publicKeyLocation) {

    public SigningKeyProperties {
        source = source == null ? Source.GENERATED : source;
        keyId = normalize(keyId);
        privateKeyLocation = normalize(privateKeyLocation);
        publicKeyLocation = normalize(publicKeyLocation);
        if (keyId != null && keyId.length() > 128) {
            throw new IllegalArgumentException("identity-hub.signing-key.key-id excede 128 caracteres");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public enum Source {
        GENERATED,
        PEM
    }
}
