package com.karamba121.backend.features.tenancy;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlugIgnoreCase(String slug);
}
