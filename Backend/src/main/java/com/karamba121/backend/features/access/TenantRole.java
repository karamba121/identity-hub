package com.karamba121.backend.features.access;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.karamba121.backend.features.tenancy.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "tenant_role",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_role_tenant_code",
                        columnNames = {"tenant_id", "code"}),
                @UniqueConstraint(
                        name = "uk_tenant_role_id_tenant",
                        columnNames = {"id", "tenant_id"})
        })
public class TenantRole {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(length = 80, nullable = false)
    private String code;

    @Column(name = "display_name", length = 160, nullable = false)
    private String displayName;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToMany
    @JoinTable(
            name = "tenant_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_code"))
    private Set<PermissionDefinition> permissions = new LinkedHashSet<>();

    protected TenantRole() {
    }

    public TenantRole(Tenant tenant, String code, String displayName, boolean systemRole) {
        if (tenant == null) {
            throw new IllegalArgumentException("Tenant do papel é obrigatório");
        }
        this.id = UUID.randomUUID().toString();
        this.tenant = tenant;
        this.code = normalizeCode(code);
        this.displayName = required(displayName, "Nome do papel");
        this.systemRole = systemRole;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public Tenant getTenant() {
        return tenant;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public Set<PermissionDefinition> getPermissions() {
        return Set.copyOf(permissions);
    }

    public void grant(PermissionDefinition permission) {
        if (permission == null) {
            throw new IllegalArgumentException("Permissão é obrigatória");
        }
        permissions.add(permission);
    }

    private static String normalizeCode(String value) {
        String code = required(value, "Código do papel").toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Código do papel inválido");
        }
        return code;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.trim();
    }
}
