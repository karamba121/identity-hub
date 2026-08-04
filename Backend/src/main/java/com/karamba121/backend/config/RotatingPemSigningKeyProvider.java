package com.karamba121.backend.config;

import org.springframework.core.io.ResourceLoader;

import com.nimbusds.jose.jwk.RSAKey;

final class RotatingPemSigningKeyProvider implements SigningKeyProvider {

    private final SigningKeyProperties properties;
    private final PemSigningKeyProvider pemLoader;

    RotatingPemSigningKeyProvider(SigningKeyProperties properties, ResourceLoader resources) {
        this.properties = properties;
        this.pemLoader = new PemSigningKeyProvider(properties, resources);
    }

    @Override
    public SigningKeySet load() {
        if (properties.activationAt() == null) {
            throw new IllegalArgumentException(
                    "identity-hub.signing-key.activation-at é obrigatório no modo ROTATING_PEM");
        }
        RSAKey current = pemLoader.loadKey(
                properties.privateKeyLocation(),
                properties.publicKeyLocation(),
                properties.keyId(),
                "");
        RSAKey next = pemLoader.loadKey(
                properties.nextPrivateKeyLocation(),
                properties.nextPublicKeyLocation(),
                properties.nextKeyId(),
                "next-");
        return new SigningKeySet(
                current,
                next,
                properties.activationAt(),
                properties.previousKeyRetention());
    }
}
