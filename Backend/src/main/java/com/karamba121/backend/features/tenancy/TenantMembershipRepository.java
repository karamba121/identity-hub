package com.karamba121.backend.features.tenancy;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TenantMembershipRepository extends JpaRepository<TenantMembership, String> {

    boolean existsByTenantIdAndUserId(String tenantId, String userId);

    Optional<TenantMembership> findByTenantIdAndUserId(String tenantId, String userId);

    @EntityGraph(attributePaths = {"tenant", "user", "role", "role.permissions"})
    @Query("""
            select membership from TenantMembership membership
            where membership.tenant.id = :tenantId
              and membership.user.id = :userId
              and membership.status = :membershipStatus
              and membership.tenant.status = :tenantStatus
            """)
    Optional<TenantMembership> findAuthorizationContext(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId,
            @Param("membershipStatus") MembershipStatus membershipStatus,
            @Param("tenantStatus") TenantStatus tenantStatus);

    @EntityGraph(attributePaths = {"tenant", "user", "role"})
    Optional<TenantMembership> findByIdAndTenantId(String membershipId, String tenantId);

    @Query("""
            select count(membership) from TenantMembership membership
            where membership.tenant.id = :tenantId
              and membership.status = :status
              and membership.user.enabled = true
              and membership.role.code = :roleCode
            """)
    long countValidAdministrators(
            @Param("tenantId") String tenantId,
            @Param("status") MembershipStatus status,
            @Param("roleCode") String roleCode);

    @EntityGraph(attributePaths = {"tenant", "role", "role.permissions"})
    List<TenantMembership> findAllByUserIdAndStatusAndTenantStatusOrderByTenantDisplayNameAsc(
            String userId,
            MembershipStatus membershipStatus,
            TenantStatus tenantStatus);
}
