package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.access.AdminResourceContract;
import com.karamba121.backend.features.access.PermissionCode;
import com.karamba121.backend.features.access.PermissionDefinitionRepository;
import com.karamba121.backend.features.access.TenantRole;
import com.karamba121.backend.features.access.TenantRoleRepository;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.resource.DemoResourceContract;
import com.karamba121.backend.features.tenancy.MembershipStatus;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TenantAdministrationEndpointTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TenantRoleRepository roles;

    @Autowired
    private PermissionDefinitionRepository permissions;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void requiresBearerToken() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tenants/tenant/memberships/membership/suspend"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenForAnotherAudience() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tenants/tenant/memberships/membership/suspend")
                        .header("Authorization", "Bearer " + token(
                                "user", DemoResourceContract.AUDIENCE, AdminResourceContract.SCOPE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAdministrativeScope() throws Exception {
        mockMvc.perform(post("/api/v1/admin/tenants/tenant/memberships/membership/suspend")
                        .header("Authorization", "Bearer " + token(
                                "user", AdminResourceContract.AUDIENCE, DemoResourceContract.SCOPE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsActorWithoutEffectivePermission() throws Exception {
        Tenant tenant = tenant("without-permission");
        TenantRole reader = role(tenant, "reader", "Leitor", PermissionCode.TENANT_ACCESS_READ);
        TenantRole operator = role(tenant, "operator", "Operador");
        TenantMembership actor = membership(tenant, reader, "actor-reader");
        TenantMembership target = membership(tenant, operator, "target-reader");

        mockMvc.perform(post(url(tenant, target) + "/suspend")
                        .header("Authorization", "Bearer " + adminToken(actor)))
                .andExpect(status().isForbidden());

        assertThat(memberships.findById(target.getId()).orElseThrow().getStatus())
                .isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void rejectsCrossTenantAdministrationEvenWhenActorManagesAnotherTenant() throws Exception {
        Tenant actorTenant = tenant("actor-tenant");
        Tenant targetTenant = tenant("target-tenant");
        TenantRole manager = role(
                actorTenant, "manager", "Gerente", PermissionCode.TENANT_ACCESS_MANAGE);
        TenantRole operator = role(targetTenant, "operator", "Operador");
        TenantMembership actor = membership(actorTenant, manager, "actor-manager");
        TenantMembership target = membership(targetTenant, operator, "foreign-target");

        mockMvc.perform(delete(url(targetTenant, target))
                        .header("Authorization", "Bearer " + adminToken(actor)))
                .andExpect(status().isForbidden());

        assertThat(memberships.existsById(target.getId())).isTrue();
    }

    @Test
    void managesMembershipsOnlyAfterScopeAudienceAndPermissionChecks() throws Exception {
        Tenant tenant = tenant("managed");
        TenantRole administrator = role(
                tenant,
                "administrator",
                "Administrador",
                PermissionCode.TENANT_ACCESS_MANAGE,
                PermissionCode.SECURITY_AUDIT_READ);
        TenantRole operator = role(tenant, "operator", "Operador");
        TenantMembership actor = membership(tenant, administrator, "actor-admin");
        TenantMembership roleTarget = membership(tenant, null, "role-target");
        TenantMembership suspensionTarget = membership(tenant, operator, "suspension-target");
        TenantMembership removalTarget = membership(tenant, operator, "removal-target");

        mockMvc.perform(put(url(tenant, roleTarget) + "/role")
                        .header("Authorization", "Bearer " + adminToken(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"" + operator.getId() + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(url(tenant, suspensionTarget) + "/suspend")
                        .header("Authorization", "Bearer " + adminToken(actor)))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(url(tenant, removalTarget))
                        .header("Authorization", "Bearer " + adminToken(actor)))
                .andExpect(status().isNoContent());

        TenantMembership assigned = memberships.findByIdAndTenantId(
                roleTarget.getId(), tenant.getId()).orElseThrow();
        TenantMembership suspended = memberships.findByIdAndTenantId(
                suspensionTarget.getId(), tenant.getId()).orElseThrow();
        assertThat(assigned.getRole().getId()).isEqualTo(operator.getId());
        assertThat(suspended.getStatus()).isEqualTo(MembershipStatus.SUSPENDED);
        assertThat(memberships.existsById(removalTarget.getId())).isFalse();

        mockMvc.perform(get(auditUrl(tenant))
                        .header("Authorization", "Bearer " + adminToken(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[*].eventType", org.hamcrest.Matchers.containsInAnyOrder(
                        "TENANT_MEMBERSHIP_ROLE_ASSIGNED",
                        "TENANT_MEMBERSHIP_SUSPENDED",
                        "TENANT_MEMBERSHIP_REMOVED")))
                .andExpect(jsonPath("$.items[*].result", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("SUCCEEDED"))));
    }

    @Test
    void returnsConflictWhenLastAdministratorWouldBeLost() throws Exception {
        Tenant tenant = tenant("last-admin-api");
        TenantRole administrator = role(
                tenant,
                "administrator",
                "Administrador",
                PermissionCode.TENANT_ACCESS_MANAGE,
                PermissionCode.SECURITY_AUDIT_READ);
        TenantMembership actor = membership(tenant, administrator, "last-admin");

        mockMvc.perform(delete(url(tenant, actor))
                        .header("Authorization", "Bearer " + adminToken(actor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "O último administrador válido do tenant não pode ser removido ou rebaixado"));

        assertThat(memberships.existsById(actor.getId())).isTrue();
        mockMvc.perform(get(auditUrl(tenant))
                        .header("Authorization", "Bearer " + adminToken(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventType").value("TENANT_MEMBERSHIP_REMOVED"))
                .andExpect(jsonPath("$.items[0].result").value("FAILED"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("LAST_ADMINISTRATOR"));
    }

    @Test
    void rejectsInvalidRoleRequestWithoutChangingTheMembership() throws Exception {
        Tenant tenant = tenant("invalid-role");
        TenantRole administrator = role(
                tenant, "administrator", "Administrador", PermissionCode.TENANT_ACCESS_MANAGE);
        TenantMembership actor = membership(tenant, administrator, "invalid-role-actor");
        TenantMembership target = membership(tenant, null, "invalid-role-target");

        mockMvc.perform(put(url(tenant, target) + "/role")
                        .header("Authorization", "Bearer " + adminToken(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Papel é obrigatório"));

        assertThat(memberships.findById(target.getId()).orElseThrow().getRole()).isNull();
    }

    private Tenant tenant(String label) {
        String suffix = UUID.randomUUID().toString();
        return tenants.save(new Tenant(label + "-" + suffix, "Tenant " + label));
    }

    private TenantRole role(
            Tenant tenant,
            String code,
            String displayName,
            PermissionCode... grantedPermissions) {
        TenantRole role = new TenantRole(tenant, code, displayName, false);
        for (PermissionCode permission : grantedPermissions) {
            role.grant(permissions.findById(permission.value()).orElseThrow());
        }
        return roles.save(role);
    }

    private TenantMembership membership(
            Tenant tenant,
            TenantRole role,
            String label) {
        String suffix = UUID.randomUUID().toString();
        IdentityUser user = users.save(new IdentityUser(
                label + "-" + suffix + "@example.test", "Usuário " + label, "not-used"));
        TenantMembership membership = new TenantMembership(tenant, user);
        if (role != null) {
            membership.assignRole(role);
        }
        return memberships.save(membership);
    }

    private String adminToken(TenantMembership actor) {
        return token(actor.getUser().getId(), AdminResourceContract.AUDIENCE, AdminResourceContract.SCOPE);
    }

    private String token(String subject, String audience, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .build();
        return new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
    }

    private static String url(Tenant tenant, TenantMembership membership) {
        return "/api/v1/admin/tenants/" + tenant.getId()
                + "/memberships/" + membership.getId();
    }

    private static String auditUrl(Tenant tenant) {
        return "/api/v1/admin/tenants/" + tenant.getId() + "/audit-events";
    }
}
