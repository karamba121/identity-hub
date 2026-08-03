package com.karamba121.backend.features.access;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface TenantOAuthClientRepository extends JpaRepository<TenantOAuthClient, String> {

    @EntityGraph(attributePaths = "tenant")
    List<TenantOAuthClient> findAllByTenantIdOrderByClientIdAsc(String tenantId);

    @EntityGraph(attributePaths = "tenant")
    Optional<TenantOAuthClient> findByTenantIdAndClientId(String tenantId, String clientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select client from TenantOAuthClient client
            where client.tenant.id = :tenantId and client.clientId = :clientId
            """)
    Optional<TenantOAuthClient> findForSecretRotation(String tenantId, String clientId);
}
