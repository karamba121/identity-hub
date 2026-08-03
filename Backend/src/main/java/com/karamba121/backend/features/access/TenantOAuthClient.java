package com.karamba121.backend.features.access;

import java.time.Instant;

import com.karamba121.backend.features.tenancy.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant_oauth_client")
public class TenantOAuthClient {

    @Id
    @Column(name = "registered_client_id", length = 100, nullable = false)
    private String registeredClientId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "client_id", length = 100, nullable = false, unique = true)
    private String clientId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TenantOAuthClient() {
    }

    public TenantOAuthClient(Tenant tenant, String registeredClientId, String clientId) {
        if (tenant == null || registeredClientId == null || clientId == null) {
            throw new IllegalArgumentException("Tenant e identificadores do cliente são obrigatórios");
        }
        this.tenant = tenant;
        this.registeredClientId = registeredClientId;
        this.clientId = clientId;
        this.createdAt = Instant.now();
    }

    public String getRegisteredClientId() {
        return registeredClientId;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getClientId() {
        return clientId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
