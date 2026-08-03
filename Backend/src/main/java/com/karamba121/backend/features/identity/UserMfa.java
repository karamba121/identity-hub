package com.karamba121.backend.features.identity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_mfa")
public class UserMfa {

    @Id
    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "encrypted_secret", length = 512, nullable = false)
    private String encryptedSecret;

    @Column(name = "enabled_at")
    private Instant enabledAt;

    @Column(name = "last_used_step")
    private Long lastUsedStep;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserMfa() {
    }

    public UserMfa(String userId, String encryptedSecret) {
        this.userId = userId;
        this.encryptedSecret = encryptedSecret;
        this.createdAt = Instant.now();
    }

    public String getUserId() { return userId; }
    public String getEncryptedSecret() { return encryptedSecret; }
    public Instant getEnabledAt() { return enabledAt; }
    public Long getLastUsedStep() { return lastUsedStep; }
    public boolean isEnabled() { return enabledAt != null; }

    public void enable(Instant now) { this.enabledAt = now; }
    public void useStep(long step) { this.lastUsedStep = step; }
}
