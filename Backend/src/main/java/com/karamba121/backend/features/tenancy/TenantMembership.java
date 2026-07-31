package com.karamba121.backend.features.tenancy;

import java.time.Instant;
import java.util.UUID;

import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.access.TenantRole;

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
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_membership_tenant_user",
                        columnNames = {"tenant_id", "user_id"}),
                @UniqueConstraint(
                        name = "uk_tenant_membership_id_tenant",
                        columnNames = {"id", "tenant_id"})
        })
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private TenantRole role;

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

    public TenantRole getRole() {
        return role;
    }

    public void assignRole(TenantRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Papel é obrigatório");
        }
        if (!tenant.getId().equals(role.getTenant().getId())) {
            throw new IllegalArgumentException("Papel e membership devem pertencer ao mesmo tenant");
        }
        this.role = role;
    }

    public void suspend() {
        this.status = MembershipStatus.SUSPENDED;
    }
}
