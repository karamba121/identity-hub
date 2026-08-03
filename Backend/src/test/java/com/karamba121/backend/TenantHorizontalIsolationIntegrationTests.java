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
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.access.AdminResourceContract;
import com.karamba121.backend.features.access.OAuthClientAdministrationService;
import com.karamba121.backend.features.access.OAuthClientAdministrationService.OAuthClientCommand;
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
class TenantHorizontalIsolationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private TenantRoleRepository roles;

    @Autowired
    private PermissionDefinitionRepository permissions;

    @Autowired
    private OAuthClientAdministrationService clientAdministration;

    @Autowired
    private RegisteredClientRepository registeredClients;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void ordinaryUserObservesOnlyTheirOwnActiveTenantContext() throws Exception {
        Tenant visibleTenant = tenant("user-visible");
        Tenant hiddenTenant = tenant("user-hidden");
        TenantMembership visibleMembership = membership(visibleTenant, "visible-user", null);
        membership(hiddenTenant, "hidden-user", null);

        mockMvc.perform(get("/api/v1/demo/tenants")
                        .header("Authorization", demoBearer(visibleMembership)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tenantId").value(visibleTenant.getId()));
    }

    @Test
    void administratorCannotReadOrMutateResourcesThroughAnotherTenantRoute() throws Exception {
        Tenant actorTenant = tenant("admin-actor");
        Tenant targetTenant = tenant("admin-target");
        TenantMembership actor = administrator(actorTenant, "isolated-admin");
        TenantRole targetRole = role(targetTenant, "operator", PermissionCode.TENANT_ACCESS_READ);
        TenantMembership targetMembership = membership(targetTenant, "target-member", targetRole);
        String targetClientId = client(targetTenant, "target-client");
        String targetClientName = registeredClients.findByClientId(targetClientId).getClientName();

        mockMvc.perform(get(oauthClientsUrl(targetTenant))
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(oauthClientsUrl(targetTenant) + "/" + targetClientId)
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(oauthClientsUrl(targetTenant) + "/" + targetClientId)
                        .header("Authorization", adminBearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateClientPayload("Nome adulterado")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(oauthClientsUrl(targetTenant) + "/" + targetClientId)
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(membershipsUrl(targetTenant) + "/" + targetMembership.getId() + "/role")
                        .header("Authorization", adminBearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\"}".formatted(targetRole.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(membershipsUrl(targetTenant) + "/" + targetMembership.getId() + "/suspend")
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(membershipsUrl(targetTenant) + "/" + targetMembership.getId())
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(auditUrl(targetTenant))
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isForbidden());

        RegisteredClient preservedClient = registeredClients.findByClientId(targetClientId);
        assertThat(preservedClient).isNotNull();
        assertThat(preservedClient.getClientName()).isEqualTo(targetClientName);
        TenantMembership preservedMembership = memberships.findById(targetMembership.getId()).orElseThrow();
        assertThat(preservedMembership.getStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(preservedMembership.getRole().getId()).isEqualTo(targetRole.getId());
    }

    @Test
    void authorizedTenantRouteCannotAliasAClientOrRoleOwnedByAnotherTenant() throws Exception {
        Tenant actorTenant = tenant("alias-actor");
        Tenant targetTenant = tenant("alias-target");
        TenantMembership actor = administrator(actorTenant, "alias-admin");
        TenantRole ownRole = role(actorTenant, "reader", PermissionCode.TENANT_ACCESS_READ);
        TenantMembership ownMembership = membership(actorTenant, "own-member", ownRole);
        TenantRole foreignRole = role(targetTenant, "foreign-role", PermissionCode.SECURITY_AUDIT_READ);
        String ownClientId = client(actorTenant, "own-client");
        String foreignClientId = client(targetTenant, "foreign-client");
        String foreignClientName = registeredClients.findByClientId(foreignClientId).getClientName();

        mockMvc.perform(get(oauthClientsUrl(actorTenant))
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientId").value(ownClientId));
        mockMvc.perform(get(oauthClientsUrl(actorTenant) + "/" + foreignClientId)
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(oauthClientsUrl(actorTenant) + "/" + foreignClientId)
                        .header("Authorization", adminBearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateClientPayload("Nome adulterado")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(oauthClientsUrl(actorTenant) + "/" + foreignClientId)
                        .header("Authorization", adminBearer(actor)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(membershipsUrl(actorTenant) + "/" + ownMembership.getId() + "/role")
                        .header("Authorization", adminBearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleId\":\"%s\"}".formatted(foreignRole.getId())))
                .andExpect(status().isNotFound());

        assertThat(registeredClients.findByClientId(foreignClientId).getClientName())
                .isEqualTo(foreignClientName);
        assertThat(memberships.findById(ownMembership.getId()).orElseThrow().getRole().getId())
                .isEqualTo(ownRole.getId());
    }

    @Test
    void oauthClientIdentityDoesNotGrantAdministrativeAccessWithoutMembership() throws Exception {
        Tenant ownerTenant = tenant("client-principal");
        String clientId = client(ownerTenant, "unprivileged-client");

        mockMvc.perform(get(oauthClientsUrl(ownerTenant))
                        .header("Authorization", adminBearer(clientId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(auditUrl(ownerTenant))
                        .header("Authorization", adminBearer(clientId)))
                .andExpect(status().isForbidden());
    }

    private Tenant tenant(String label) {
        String suffix = UUID.randomUUID().toString();
        return tenants.save(new Tenant(label + "-" + suffix, "Tenant " + label));
    }

    private TenantMembership administrator(Tenant tenant, String label) {
        TenantRole role = role(
                tenant,
                label + "-role",
                PermissionCode.TENANT_ACCESS_READ,
                PermissionCode.TENANT_ACCESS_MANAGE,
                PermissionCode.OAUTH_CLIENTS_READ,
                PermissionCode.OAUTH_CLIENTS_MANAGE,
                PermissionCode.SECURITY_AUDIT_READ);
        return membership(tenant, label, role);
    }

    private TenantRole role(Tenant tenant, String code, PermissionCode... grantedPermissions) {
        TenantRole role = new TenantRole(tenant, code, "Papel " + code, false);
        for (PermissionCode permission : grantedPermissions) {
            role.grant(permissions.findById(permission.value()).orElseThrow());
        }
        return roles.save(role);
    }

    private TenantMembership membership(Tenant tenant, String label, TenantRole role) {
        IdentityUser user = users.save(new IdentityUser(
                label + "-" + UUID.randomUUID() + "@example.test", "Usuário " + label, "not-used"));
        TenantMembership membership = new TenantMembership(tenant, user);
        if (role != null) {
            membership.assignRole(role);
        }
        return memberships.save(membership);
    }

    private String client(Tenant tenant, String label) {
        String clientId = label + "-" + UUID.randomUUID();
        return clientAdministration.create(
                tenant.getId(),
                new OAuthClientCommand(
                        clientId,
                        "Cliente " + label,
                        Set.of("https://" + label + ".example.test/callback"),
                        Set.of("https://" + label + ".example.test/logout"),
                        Set.of("openid", "profile")))
                .clientId();
    }

    private String demoBearer(TenantMembership actor) {
        return bearer(actor.getUser().getId(), DemoResourceContract.AUDIENCE, DemoResourceContract.SCOPE);
    }

    private String adminBearer(TenantMembership actor) {
        return adminBearer(actor.getUser().getId());
    }

    private String adminBearer(String subject) {
        return bearer(subject, AdminResourceContract.AUDIENCE, AdminResourceContract.SCOPE);
    }

    private String bearer(String subject, String audience, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject(subject)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", scope)
                .build();
        String token = new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
        return "Bearer " + token;
    }

    private static String oauthClientsUrl(Tenant tenant) {
        return "/api/v1/admin/tenants/" + tenant.getId() + "/oauth-clients";
    }

    private static String membershipsUrl(Tenant tenant) {
        return "/api/v1/admin/tenants/" + tenant.getId() + "/memberships";
    }

    private static String auditUrl(Tenant tenant) {
        return "/api/v1/admin/tenants/" + tenant.getId() + "/audit-events";
    }

    private static String updateClientPayload(String name) {
        return """
                {
                  "clientName":"%s",
                  "redirectUris":["https://changed.example.test/callback"],
                  "postLogoutRedirectUris":[],
                  "scopes":["openid"]
                }
                """.formatted(name);
    }
}
