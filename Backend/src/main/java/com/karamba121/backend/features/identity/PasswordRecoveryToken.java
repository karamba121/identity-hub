package com.karamba121.backend.features.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "password_recovery_token")
public class PasswordRecoveryToken {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private IdentityUser user;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PasswordRecoveryToken() {
    }

    public PasswordRecoveryToken(IdentityUser user, String tokenHash, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public IdentityUser getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void consume(Instant now) {
        if (consumedAt != null || revokedAt != null || !expiresAt.isAfter(now)) {
            throw new InvalidPasswordRecoveryTokenException();
        }
        consumedAt = now;
    }
}
