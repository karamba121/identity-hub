package com.karamba121.backend.features.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "mfa_recovery_code")
public class MfaRecoveryCode {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "code_hash", length = 64, nullable = false, unique = true)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MfaRecoveryCode() {
    }

    public MfaRecoveryCode(String userId, String codeHash) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = Instant.now();
    }

    public void use(Instant now) {
        if (usedAt != null) throw new IllegalStateException("Código de recuperação já utilizado");
        usedAt = now;
    }
}
