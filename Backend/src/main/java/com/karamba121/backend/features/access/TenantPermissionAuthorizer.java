package com.karamba121.backend.features.access;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.tenancy.MembershipStatus;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantStatus;

@Service
public class TenantPermissionAuthorizer {

    private final TenantMembershipRepository memberships;

    public TenantPermissionAuthorizer(TenantMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @Transactional(readOnly = true)
    public void require(String userId, String tenantId, PermissionCode permission) {
        TenantMembership membership = memberships.findAuthorizationContext(
                        tenantId, userId, MembershipStatus.ACTIVE, TenantStatus.ACTIVE)
                .orElseThrow(() -> denied(permission));
        if (!membership.getUser().isEnabled()
                || membership.getRole() == null
                || membership.getRole().getPermissions().stream()
                        .noneMatch(granted -> permission.value().equals(granted.getCode()))) {
            throw denied(permission);
        }
    }

    private static AccessDeniedException denied(PermissionCode permission) {
        return new AccessDeniedException("Permissão necessária: " + permission.value());
    }
}
