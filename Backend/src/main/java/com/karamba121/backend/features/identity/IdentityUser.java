package com.karamba121.backend.features.identity;

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
        this.createdAt = Instant.now();
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

    public void verifyEmail() {
        this.emailVerified = true;
    }

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
