package com.karamba121.backend.config;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;

record SigningKeySet(
        RSAKey current,
        RSAKey next,
        Instant activationAt,
        Duration previousKeyRetention) {

    SigningKeySet {
        Objects.requireNonNull(current, "current");
        if (!current.isPrivate()) {
            throw new IllegalArgumentException("A chave de assinatura atual deve conter material privado");
        }
        if (next == null) {
            activationAt = null;
            previousKeyRetention = null;
        } else {
            Objects.requireNonNull(activationAt, "activationAt");
            Objects.requireNonNull(previousKeyRetention, "previousKeyRetention");
            if (!next.isPrivate()) {
                throw new IllegalArgumentException("A próxima chave de assinatura deve conter material privado");
            }
            if (current.getKeyID().equals(next.getKeyID())) {
                throw new IllegalArgumentException("As chaves atual e próxima devem possuir kids distintos");
            }
            if (current.toPublicJWK().equals(next.toPublicJWK())) {
                throw new IllegalArgumentException("As chaves atual e próxima devem usar materiais distintos");
            }
        }
    }

    static SigningKeySet single(RSAKey key) {
        return new SigningKeySet(key, null, null, null);
    }

    RSAKey signingKeyAt(Instant instant) {
        return next != null && !instant.isBefore(activationAt) ? next : current;
    }

    JWKSet jwkSetAt(Instant instant) {
        if (next == null) {
            return new JWKSet(current);
        }
        if (instant.isBefore(activationAt)) {
            return new JWKSet(List.of(current, next.toPublicJWK()));
        }
        if (instant.isBefore(activationAt.plus(previousKeyRetention))) {
            return new JWKSet(List.of(next, current.toPublicJWK()));
        }
        return new JWKSet(next);
    }
}
