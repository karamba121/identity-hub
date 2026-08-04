package com.karamba121.backend.features.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_user")
public class IdentityUser {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(length = 254, nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", length = 200, nullable = false)
    private String displayName;

    @Column(name = "password_hash", length = 200, nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_failed_login_at")
    private Instant lastFailedLoginAt;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion;

    @Column(name = "local_credentials_enabled", nullable = false)
    private boolean localCredentialsEnabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdentityUser() {
    }

    public IdentityUser(String email, String displayName, String passwordHash) {
        this(email, displayName, passwordHash, true);
    }

    private IdentityUser(String email, String displayName, String passwordHash, boolean emailVerified) {
        this.id = UUID.randomUUID().toString();
        this.email = normalizeEmail(email);
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.emailVerified = emailVerified;
        this.localCredentialsEnabled = true;
        this.createdAt = Instant.now();
    }

    public static IdentityUser federated(
            String email, String displayName, String unusablePasswordHash) {
        IdentityUser user = new IdentityUser(email, displayName, unusablePasswordHash, true);
        user.localCredentialsEnabled = false;
        return user;
    }

    public static IdentityUser provisioned(
            String email, String displayName, String unusablePasswordHash) {
        IdentityUser user = new IdentityUser(email, displayName, unusablePasswordHash, true);
        user.localCredentialsEnabled = false;
        return user;
    }

    public static IdentityUser pendingEmailVerification(
            String email, String displayName, String passwordHash) {
        return new IdentityUser(email, displayName, passwordHash, false);
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public Instant getLastFailedLoginAt() {
        return lastFailedLoginAt;
    }

    public long getCredentialVersion() {
        return credentialVersion;
    }

    public boolean hasLocalCredentials() {
        return localCredentialsEnabled;
    }

    public boolean isTemporarilyLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void registerFailedLogin(
            Instant now,
            int failureThreshold,
            Duration initialLockDuration,
            Duration maximumLockDuration) {
        if (isTemporarilyLocked(now)) {
            return;
        }
        if (failedLoginAttempts < Integer.MAX_VALUE) {
            failedLoginAttempts++;
        }
        lastFailedLoginAt = now;
        if (failedLoginAttempts < failureThreshold) {
            lockedUntil = null;
            return;
        }

        Duration duration = initialLockDuration;
        int escalations = Math.min(failedLoginAttempts - failureThreshold, 20);
        for (int index = 0; index < escalations && duration.compareTo(maximumLockDuration) < 0; index++) {
            duration = duration.multipliedBy(2);
        }
        if (duration.compareTo(maximumLockDuration) > 0) {
            duration = maximumLockDuration;
        }
        lockedUntil = now.plus(duration);
    }

    public void resetLoginFailures() {
        failedLoginAttempts = 0;
        lockedUntil = null;
        lastFailedLoginAt = null;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void updatePasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Hash de senha é obrigatório");
        }
        this.passwordHash = passwordHash;
        this.localCredentialsEnabled = true;
    }

    public void advanceCredentialVersion() {
        if (credentialVersion == Long.MAX_VALUE) {
            throw new IllegalStateException("Versão da credencial esgotada");
        }
        credentialVersion++;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
