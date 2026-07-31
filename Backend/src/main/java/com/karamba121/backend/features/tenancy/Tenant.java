package com.karamba121.backend.features.tenancy;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenant")
public class Tenant {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @Column(length = 100, nullable = false, unique = true)
    private String slug;

    @Column(name = "display_name", length = 200, nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private TenantStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Tenant() {
    }

    public Tenant(String slug, String displayName) {
        this.id = UUID.randomUUID().toString();
        this.slug = normalizeSlug(slug);
        this.displayName = required(displayName, "Nome do tenant");
        this.status = TenantStatus.ACTIVE;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TenantStatus getStatus() {
        return status;
    }

    public void suspend() {
        this.status = TenantStatus.SUSPENDED;
    }

    private static String normalizeSlug(String value) {
        String slug = required(value, "Slug do tenant").toLowerCase(Locale.ROOT);
        if (!slug.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("Slug do tenant inválido");
        }
        return slug;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        return value.trim();
    }
}
