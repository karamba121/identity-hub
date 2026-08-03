package com.karamba121.backend.features.identity;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.config.IdentityHubProperties;

@Service
public class LoginAttemptService {

    private final IdentityUserRepository users;
    private final int failureThreshold;
    private final Duration initialLockDuration;
    private final Duration maximumLockDuration;

    public LoginAttemptService(IdentityUserRepository users, IdentityHubProperties properties) {
        this.users = users;
        IdentityHubProperties.LoginProtection protection = properties.loginProtection();
        if (protection.failureThreshold() < 1
                || protection.initialLockDuration().isZero()
                || protection.initialLockDuration().isNegative()
                || protection.maximumLockDuration().compareTo(protection.initialLockDuration()) < 0) {
            throw new IllegalArgumentException("Configuração de bloqueio progressivo inválida");
        }
        this.failureThreshold = protection.failureThreshold();
        this.initialLockDuration = protection.initialLockDuration();
        this.maximumLockDuration = protection.maximumLockDuration();
    }

    @Transactional
    public void failed(String email) {
        users.findByEmailForUpdate(normalize(email)).ifPresent(user -> {
            if (user.isEnabled() && user.isEmailVerified()) {
                user.registerFailedLogin(
                        Instant.now(), failureThreshold, initialLockDuration, maximumLockDuration);
            }
        });
    }

    @Transactional
    public void succeeded(String email) {
        users.findByEmailForUpdate(normalize(email)).ifPresent(IdentityUser::resetLoginFailures);
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
