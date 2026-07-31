package com.karamba121.backend.features.tenancy;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/tenants")
public class TenantContextController {

    private final TenantMembershipRepository memberships;

    public TenantContextController(TenantMembershipRepository memberships) {
        this.memberships = memberships;
    }

    @GetMapping
    List<TenantContextResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return memberships.findAllByUserIdAndStatusAndTenantStatusOrderByTenantDisplayNameAsc(
                        jwt.getSubject(), MembershipStatus.ACTIVE, TenantStatus.ACTIVE)
                .stream()
                .map(membership -> new TenantContextResponse(
                        membership.getTenant().getId(),
                        membership.getTenant().getSlug(),
                        membership.getTenant().getDisplayName()))
                .toList();
    }

    record TenantContextResponse(String tenantId, String slug, String displayName) {
    }
}
