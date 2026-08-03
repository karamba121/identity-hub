package com.karamba121.backend.features.access;

import java.util.Comparator;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karamba121.backend.features.tenancy.MembershipStatus;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantStatus;

@RestController
@RequestMapping("/api/v1/admin/context")
public class AdminTenantContextController {

    private final TenantMembershipRepository memberships;

    public AdminTenantContextController(TenantMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @GetMapping
    List<AdminTenantContextView> list(@AuthenticationPrincipal Jwt jwt) {
        return memberships.findAllByUserIdAndStatusAndTenantStatusOrderByTenantDisplayNameAsc(
                        jwt.getSubject(), MembershipStatus.ACTIVE, TenantStatus.ACTIVE)
                .stream()
                .filter(membership -> membership.getRole() != null)
                .map(membership -> new AdminTenantContextView(
                        membership.getTenant().getId(),
                        membership.getTenant().getSlug(),
                        membership.getTenant().getDisplayName(),
                        membership.getRole().getCode(),
                        membership.getRole().getDisplayName(),
                        membership.getRole().getPermissions().stream()
                                .sorted(Comparator.comparingInt(PermissionDefinition::getSortOrder))
                                .map(PermissionDefinition::getCode)
                                .toList()))
                .toList();
    }

    record AdminTenantContextView(
            String tenantId,
            String slug,
            String displayName,
            String roleCode,
            String roleDisplayName,
            List<String> permissions) {
    }
}
