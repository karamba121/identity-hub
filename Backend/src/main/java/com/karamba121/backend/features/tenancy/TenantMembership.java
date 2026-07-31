package com.karamba121.backend.features.tenancy;

import java.time.Instant;
import java.util.UUID;

import com.karamba121.backend.features.identity.IdentityUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "tenant_membership",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tenant_membership_tenant_user",
                columnNames = {"tenant_id", "user_id"}))
public class TenantMembership {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private IdentityUser user;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private MembershipStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TenantMembership() {
    }

    public TenantMembership(Tenant tenant, IdentityUser user) {
        if (tenant == null || user == null) {
            throw new IllegalArgumentException("Tenant e usuário são obrigatórios");
        }
        this.id = UUID.randomUUID().toString();
        this.tenant = tenant;
        this.user = user;
        this.status = MembershipStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public IdentityUser getUser() {
        return user;
    }

    public MembershipStatus getStatus() {
        return status;
    }

    public void suspend() {
        this.status = MembershipStatus.SUSPENDED;
    }
}
