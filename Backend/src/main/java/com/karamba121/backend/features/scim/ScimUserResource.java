package com.karamba121.backend.features.scim;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembership;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "scim_user_resource",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"tenant_id", "membership_id"}),
                @UniqueConstraint(columnNames = {"tenant_id", "user_name"}),
                @UniqueConstraint(columnNames = {"tenant_id", "external_id"})
        })
public class ScimUserResource {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private TenantMembership membership;

    @Column(name = "user_name", length = 254, nullable = false)
    private String userName;

    @Column(name = "display_name", length = 200, nullable = false)
    private String displayName;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ScimUserResource() {
    }

    public ScimUserResource(
            Tenant tenant,
            TenantMembership membership,
            String userName,
            String displayName,
            String externalId) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.tenant = tenant;
        this.membership = membership;
        this.userName = normalizeUserName(userName);
        this.displayName = required(displayName, "displayName", 200);
        this.externalId = optional(externalId, "externalId", 200);
        this.version = 1;
        this.createdAt = now;
        this.lastModifiedAt = now;
    }

    public void replace(String userName, String displayName, String externalId, boolean active) {
        String normalizedUserName = normalizeUserName(userName);
        if (!this.userName.equals(normalizedUserName)) {
            throw ScimException.mutability("userName é imutável neste provedor");
        }
        this.displayName = required(displayName, "displayName", 200);
        this.externalId = optional(externalId, "externalId", 200);
        setActive(active);
        touch();
    }

    public void patchDisplayName(String value) {
        this.displayName = required(value, "displayName", 200);
    }

    public void patchExternalId(String value) {
        this.externalId = optional(value, "externalId", 200);
    }

    public void patchActive(boolean active) {
        setActive(active);
    }

    public void markModified() {
        touch();
    }

    public void delete() {
        membership.suspend();
        deletedAt = Instant.now();
        touch();
    }

    public void restore(String displayName, String externalId, boolean active) {
        this.displayName = required(displayName, "displayName", 200);
        this.externalId = optional(externalId, "externalId", 200);
        this.deletedAt = null;
        setActive(active);
        touch();
    }

    private void setActive(boolean active) {
        if (active) {
            membership.activate();
        } else {
            membership.suspend();
        }
    }

    private void touch() {
        version++;
        lastModifiedAt = Instant.now();
    }

    public String getId() { return id; }
    public Tenant getTenant() { return tenant; }
    public TenantMembership getMembership() { return membership; }
    public String getUserName() { return userName; }
    public String getDisplayName() { return displayName; }
    public String getExternalId() { return externalId; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastModifiedAt() { return lastModifiedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    private static String normalizeUserName(String value) {
        String normalized = required(value, "userName", 254).toLowerCase(Locale.ROOT);
        if (!normalized.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ScimException.invalidValue("userName deve ser um e-mail válido");
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw ScimException.invalidValue(field + " é obrigatório");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw ScimException.invalidValue(field + " excede o limite permitido");
        }
        return normalized;
    }

    private static String optional(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, field, maxLength);
    }
}
