package com.karamba121.backend.features.tenancy;

import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlugIgnoreCase(String slug);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select tenant from Tenant tenant where tenant.id = :tenantId")
    Optional<Tenant> findByIdForUpdate(@Param("tenantId") String tenantId);
}
