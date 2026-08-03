package com.karamba121.backend.features.access;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantOAuthClientRepository extends JpaRepository<TenantOAuthClient, String> {

    @EntityGraph(attributePaths = "tenant")
    List<TenantOAuthClient> findAllByTenantIdOrderByClientIdAsc(String tenantId);

    @EntityGraph(attributePaths = "tenant")
    Optional<TenantOAuthClient> findByTenantIdAndClientId(String tenantId, String clientId);
}
