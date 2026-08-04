package com.karamba121.backend.features.scim;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScimUserResourceRepository extends JpaRepository<ScimUserResource, String> {

    @EntityGraph(attributePaths = {"tenant", "membership", "membership.user"})
    Optional<ScimUserResource> findByIdAndTenantIdAndDeletedAtIsNull(String id, String tenantId);

    @EntityGraph(attributePaths = {"tenant", "membership", "membership.user"})
    Optional<ScimUserResource> findByTenantIdAndUserNameIgnoreCase(String tenantId, String userName);

    @EntityGraph(attributePaths = {"tenant", "membership", "membership.user"})
    Optional<ScimUserResource> findByTenantIdAndUserNameIgnoreCaseAndDeletedAtIsNull(
            String tenantId, String userName);

    @EntityGraph(attributePaths = {"tenant", "membership", "membership.user"})
    Optional<ScimUserResource> findByTenantIdAndExternalIdAndDeletedAtIsNull(
            String tenantId, String externalId);

}
