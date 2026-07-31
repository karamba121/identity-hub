package com.karamba121.backend.features.tenancy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, String> {

    boolean existsByTenantIdAndUserId(String tenantId, String userId);

    Optional<TenantMembership> findByTenantIdAndUserId(String tenantId, String userId);

    @EntityGraph(attributePaths = {"tenant", "role", "role.permissions"})
    List<TenantMembership> findAllByUserIdAndStatusAndTenantStatusOrderByTenantDisplayNameAsc(
            String userId,
            MembershipStatus membershipStatus,
            TenantStatus tenantStatus);
}
