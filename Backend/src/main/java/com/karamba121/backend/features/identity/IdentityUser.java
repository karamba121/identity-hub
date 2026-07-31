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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected IdentityUser() {
    }

    public IdentityUser(String email, String displayName, String passwordHash) {
        this.id = UUID.randomUUID().toString();
        this.email = normalizeEmail(email);
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.createdAt = Instant.now();
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

    private static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
