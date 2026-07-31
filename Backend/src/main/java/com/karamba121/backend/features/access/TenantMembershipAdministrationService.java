package com.karamba121.backend.features.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.tenancy.MembershipStatus;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;

@Service
public class TenantMembershipAdministrationService {

    private static final String ADMINISTRATOR_ROLE = "administrator";

    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final TenantRoleRepository roles;

    public TenantMembershipAdministrationService(
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            TenantRoleRepository roles) {
        this.tenants = tenants;
        this.memberships = memberships;
        this.roles = roles;
    }

    @Transactional
    public void assignRole(String tenantId, String membershipId, String roleId) {
        lockTenant(tenantId);
        TenantMembership membership = membership(tenantId, membershipId);
        TenantRole role = roles.findByIdAndTenantId(roleId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Papel não encontrado no tenant"));
        if (isValidAdministrator(membership) && !isAdministrator(role)) {
            requireAnotherValidAdministrator(tenantId);
        }
        membership.assignRole(role);
    }

    @Transactional
    public void suspend(String tenantId, String membershipId) {
        lockTenant(tenantId);
        TenantMembership membership = membership(tenantId, membershipId);
        if (isValidAdministrator(membership)) {
            requireAnotherValidAdministrator(tenantId);
        }
        membership.suspend();
    }

    @Transactional
    public void remove(String tenantId, String membershipId) {
        lockTenant(tenantId);
        TenantMembership membership = membership(tenantId, membershipId);
        if (isValidAdministrator(membership)) {
            requireAnotherValidAdministrator(tenantId);
        }
        memberships.delete(membership);
    }

    private void lockTenant(String tenantId) {
        tenants.findByIdForUpdate(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant não encontrado"));
    }

    private TenantMembership membership(String tenantId, String membershipId) {
        return memberships.findByIdAndTenantId(membershipId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Membership não encontrada no tenant"));
    }

    private void requireAnotherValidAdministrator(String tenantId) {
        if (memberships.countValidAdministrators(
                tenantId, MembershipStatus.ACTIVE, ADMINISTRATOR_ROLE) <= 1) {
            throw new LastTenantAdministratorException();
        }
    }

    private static boolean isValidAdministrator(TenantMembership membership) {
        return membership.getStatus() == MembershipStatus.ACTIVE
                && membership.getUser().isEnabled()
                && membership.getRole() != null
                && isAdministrator(membership.getRole());
    }

    private static boolean isAdministrator(TenantRole role) {
        return ADMINISTRATOR_ROLE.equals(role.getCode());
    }
}
