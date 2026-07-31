package com.karamba121.backend.features.session;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "oauth_refresh_token_family")
public class RefreshTokenFamily {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "authorization_id", length = 100, nullable = false, unique = true)
    private String authorizationId;

    @Column(name = "current_token_hash", length = 64, nullable = false)
    private String currentTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RefreshTokenFamilyStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_rotated_at", nullable = false)
    private Instant lastRotatedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected RefreshTokenFamily() {
    }

    public RefreshTokenFamily(String authorizationId, String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.id = UUID.randomUUID().toString();
        this.authorizationId = authorizationId;
        this.currentTokenHash = tokenHash;
        this.status = RefreshTokenFamilyStatus.ACTIVE;
        this.createdAt = issuedAt;
        this.lastRotatedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void rotateTo(String tokenHash, Instant issuedAt, Instant expiresAt) {
        this.currentTokenHash = tokenHash;
        this.lastRotatedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    public void compromise(Instant at) {
        this.status = RefreshTokenFamilyStatus.COMPROMISED;
        this.revokedAt = at;
    }

    public void revoke(Instant at) {
        if (this.status == RefreshTokenFamilyStatus.ACTIVE) {
            this.status = RefreshTokenFamilyStatus.REVOKED;
        }
        this.revokedAt = at;
    }

    public String getId() { return id; }
    public String getAuthorizationId() { return authorizationId; }
    public String getCurrentTokenHash() { return currentTokenHash; }
    public RefreshTokenFamilyStatus getStatus() { return status; }
}
