package com.karamba121.backend.features.access;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;

public final class RotatingClientSecretPasswordEncoder implements PasswordEncoder {

    private static final String PREFIX = "{rotating}";

    private final PasswordEncoder delegate;
    private final Clock clock;

    public RotatingClientSecretPasswordEncoder(PasswordEncoder delegate, Clock clock) {
        this.delegate = delegate;
        this.clock = clock;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        Optional<RotatingSecret> rotating = parse(encodedPassword);
        if (rotating.isEmpty()) {
            return safeMatches(rawPassword, encodedPassword);
        }
        RotatingSecret secret = rotating.get();
        boolean currentMatches = safeMatches(rawPassword, secret.currentHash());
        boolean previousMatches = clock.instant().isBefore(secret.previousExpiresAt())
                && safeMatches(rawPassword, secret.previousHash());
        return currentMatches || previousMatches;
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword != null
                && !encodedPassword.startsWith(PREFIX)
                && delegate.upgradeEncoding(encodedPassword);
    }

    public Rotation rotate(CharSequence newSecret, String encodedCurrentSecret, Duration previousValidity) {
        if (previousValidity == null || previousValidity.isNegative()) {
            throw new IllegalArgumentException("Janela do segredo anterior não pode ser negativa");
        }
        String previousHash = parse(encodedCurrentSecret)
                .map(RotatingSecret::currentHash)
                .orElse(encodedCurrentSecret);
        Instant previousExpiresAt = clock.instant().plus(previousValidity);
        String encoded = PREFIX
                + previousExpiresAt.getEpochSecond() + ":"
                + delegate.encode(newSecret) + ":"
                + previousHash;
        return new Rotation(encoded, previousExpiresAt);
    }

    public Optional<Instant> previousSecretExpiresAt(String encodedSecret) {
        return parse(encodedSecret).map(RotatingSecret::previousExpiresAt);
    }

    private boolean safeMatches(CharSequence rawPassword, String encodedPassword) {
        try {
            return delegate.matches(rawPassword, encodedPassword);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static Optional<RotatingSecret> parse(String encodedPassword) {
        if (encodedPassword == null || !encodedPassword.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String value = encodedPassword.substring(PREFIX.length());
        int expirySeparator = value.indexOf(':');
        int hashSeparator = expirySeparator < 0 ? -1 : value.indexOf(':', expirySeparator + 1);
        if (expirySeparator <= 0 || hashSeparator <= expirySeparator + 1 || hashSeparator == value.length() - 1) {
            return Optional.empty();
        }
        try {
            Instant expiry = Instant.ofEpochSecond(Long.parseLong(value.substring(0, expirySeparator)));
            return Optional.of(new RotatingSecret(
                    value.substring(expirySeparator + 1, hashSeparator),
                    value.substring(hashSeparator + 1),
                    expiry));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public record Rotation(String encodedSecret, Instant previousSecretExpiresAt) {
    }

    private record RotatingSecret(String currentHash, String previousHash, Instant previousExpiresAt) {
    }
}
