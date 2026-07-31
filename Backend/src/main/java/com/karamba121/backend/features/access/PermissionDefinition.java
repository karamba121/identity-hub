package com.karamba121.backend.features.access;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "permission_definition")
public class PermissionDefinition {

    @Id
    @Column(length = 100, nullable = false)
    private String code;

    @Column(name = "display_name", length = 160, nullable = false)
    private String displayName;

    @Column(length = 500, nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(length = 40, nullable = false)
    private PermissionCategory category;

    @Column(name = "sort_order", nullable = false, unique = true)
    private int sortOrder;

    protected PermissionDefinition() {
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public PermissionCategory getCategory() {
        return category;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
