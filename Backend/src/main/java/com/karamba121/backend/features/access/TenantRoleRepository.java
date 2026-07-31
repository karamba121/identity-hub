package com.karamba121.backend.features.access;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRoleRepository extends JpaRepository<TenantRole, String> {

    @EntityGraph(attributePaths = "permissions")
    Optional<TenantRole> findByTenantIdAndCode(String tenantId, String code);
}
