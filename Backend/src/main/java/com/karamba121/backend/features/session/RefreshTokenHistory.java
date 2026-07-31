package com.karamba121.backend.features.session;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "oauth_refresh_token_history")
public class RefreshTokenHistory {

    @Id
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;

    @Column(name = "family_id", length = 36, nullable = false)
    private String familyId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private RefreshTokenStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    protected RefreshTokenHistory() {
    }

    public RefreshTokenHistory(String tokenHash, String familyId, Instant issuedAt) {
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.status = RefreshTokenStatus.CURRENT;
        this.issuedAt = issuedAt;
    }

    public void markUsed(Instant at) {
        this.status = RefreshTokenStatus.USED;
        this.consumedAt = at;
    }

    public void revoke(Instant at) {
        this.status = RefreshTokenStatus.REVOKED;
        this.consumedAt = at;
    }

    public String getFamilyId() { return familyId; }
    public RefreshTokenStatus getStatus() { return status; }
}
