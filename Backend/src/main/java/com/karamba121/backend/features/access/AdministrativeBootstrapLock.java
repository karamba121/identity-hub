package com.karamba121.backend.features.access;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "administrative_bootstrap_lock")
public class AdministrativeBootstrapLock {

    @Id
    @Column(name = "lock_name", length = 80, nullable = false)
    private String lockName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "tenant_id", length = 36)
    private String tenantId;

    @Column(name = "user_id", length = 36)
    private String userId;

    protected AdministrativeBootstrapLock() {
    }

    public String getLockName() {
        return lockName;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getUserId() {
        return userId;
    }

    public void complete(String tenantId, String userId) {
        if (isCompleted()) {
            throw new IllegalStateException("Bootstrap administrativo já concluído");
        }
        this.tenantId = tenantId;
        this.userId = userId;
        this.completedAt = Instant.now();
    }
}
