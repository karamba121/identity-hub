package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.access.AdminResourceContract;
import com.karamba121.backend.features.access.PermissionCode;
import com.karamba121.backend.features.access.PermissionDefinitionRepository;
import com.karamba121.backend.features.access.TenantOAuthClientRepository;
import com.karamba121.backend.features.access.TenantRole;
import com.karamba121.backend.features.access.TenantRoleRepository;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
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
class OAuthClientAdministrationEndpointTests {

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
    private TenantOAuthClientRepository ownerships;

    @Autowired
    private RegisteredClientRepository clients;

    @Autowired
    private JdbcOperations jdbc;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void createsReadsUpdatesAndDeletesTenantOwnedPublicClient() throws Exception {
        Tenant tenant = tenant("oauth-crud");
        TenantMembership actor = actor(
                tenant, "oauth-admin", PermissionCode.OAUTH_CLIENTS_READ, PermissionCode.OAUTH_CLIENTS_MANAGE);
        String clientId = "portal-" + shortSuffix();
        String baseUrl = baseUrl(tenant);

        mockMvc.perform(post(baseUrl)
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(
                                clientId,
                                "Portal inicial",
                                "https://portal.example.test/callback",
                                "https://portal.example.test/logout")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/" + clientId)))
                .andExpect(jsonPath("$.clientId").value(clientId))
                .andExpect(jsonPath("$.clientType").value("PUBLIC"))
                .andExpect(jsonPath("$.pkceRequired").value(true))
                .andExpect(jsonPath("$.clientSecret").doesNotExist());

        RegisteredClient created = clients.findByClientId(clientId);
        assertThat(created).isNotNull();
        assertThat(created.getClientSecret()).isNull();
        assertThat(created.getClientAuthenticationMethods()).containsExactly(ClientAuthenticationMethod.NONE);
        assertThat(created.getAuthorizationGrantTypes())
                .contains(AuthorizationGrantType.AUTHORIZATION_CODE, AuthorizationGrantType.REFRESH_TOKEN);
        assertThat(created.getClientSettings().isRequireProofKey()).isTrue();
        assertThat(ownerships.findByTenantIdAndClientId(tenant.getId(), clientId)).isPresent();

        mockMvc.perform(get(baseUrl)
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].clientId").value(clientId));

        mockMvc.perform(put(baseUrl + "/" + clientId)
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload(
                                "Portal atualizado",
                                "https://portal.example.test/oauth/callback")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Portal atualizado"))
                .andExpect(jsonPath("$.redirectUris[0]")
                        .value("https://portal.example.test/oauth/callback"))
                .andExpect(jsonPath("$.postLogoutRedirectUris.length()").value(0));

        mockMvc.perform(get(baseUrl + "/" + clientId)
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes.length()").value(2))
                .andExpect(jsonPath("$.scopes", org.hamcrest.Matchers.hasItems("openid", "profile")));

        String authorizationId = persistRenewableState(created, actor);

        mockMvc.perform(delete(baseUrl + "/" + clientId)
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isNoContent());

        assertThat(clients.findByClientId(clientId)).isNull();
        assertThat(ownerships.findByTenantIdAndClientId(tenant.getId(), clientId)).isEmpty();
        assertThat(count("oauth2_authorization", "id", authorizationId)).isZero();
        assertThat(count("oauth2_authorization_consent", "registered_client_id", created.getId())).isZero();
        assertThat(count("oauth_refresh_token_family", "authorization_id", authorizationId)).isZero();
        assertThat(count("oauth_refresh_token_history", "family_id", authorizationId)).isZero();
        mockMvc.perform(get(baseUrl + "/" + clientId)
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isNotFound());
    }

    @Test
    void separatesReadAndManagePermissions() throws Exception {
        Tenant tenant = tenant("oauth-reader");
        TenantMembership reader = actor(tenant, "oauth-reader", PermissionCode.OAUTH_CLIENTS_READ);

        mockMvc.perform(get(baseUrl(tenant))
                        .header("Authorization", bearer(reader)))
                .andExpect(status().isOk());
        mockMvc.perform(post(baseUrl(tenant))
                        .header("Authorization", bearer(reader))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(
                                "forbidden-" + shortSuffix(),
                                "Cliente proibido",
                                "https://forbidden.example.test/callback",
                                "https://forbidden.example.test/logout")))
                .andExpect(status().isForbidden());
    }

    @Test
    void exposesOnlyTheAuthenticatedActorsAdministrativeTenantContext() throws Exception {
        Tenant visibleTenant = tenant("oauth-context-visible");
        Tenant hiddenTenant = tenant("oauth-context-hidden");
        TenantMembership actor = actor(
                visibleTenant,
                "oauth-context-admin",
                PermissionCode.OAUTH_CLIENTS_READ,
                PermissionCode.OAUTH_CLIENTS_MANAGE);
        actor(hiddenTenant, "another-context-admin", PermissionCode.OAUTH_CLIENTS_READ);

        mockMvc.perform(get("/api/v1/admin/context")
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].tenantId").value(visibleTenant.getId()))
                .andExpect(jsonPath("$[0].permissions", org.hamcrest.Matchers.hasItems(
                        PermissionCode.OAUTH_CLIENTS_READ.value(),
                        PermissionCode.OAUTH_CLIENTS_MANAGE.value())));
    }

    @Test
    void rejectsHorizontalAccessToAnotherTenantClients() throws Exception {
        Tenant actorTenant = tenant("oauth-actor");
        Tenant targetTenant = tenant("oauth-target");
        TenantMembership actor = actor(
                actorTenant, "oauth-manager", PermissionCode.OAUTH_CLIENTS_READ, PermissionCode.OAUTH_CLIENTS_MANAGE);

        mockMvc.perform(get(baseUrl(targetTenant))
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsafeRedirectUrisAndUnknownScopes() throws Exception {
        Tenant tenant = tenant("oauth-validation");
        TenantMembership actor = actor(tenant, "oauth-validator", PermissionCode.OAUTH_CLIENTS_MANAGE);
        String clientId = "invalid-" + shortSuffix();

        mockMvc.perform(post(baseUrl(tenant))
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "clientId":"%s",
                                  "clientName":"Cliente inválido",
                                  "redirectUris":["http://external.example.test/*"],
                                  "postLogoutRedirectUris":[],
                                  "scopes":["openid","unknown.scope"]
                                }
                                """.formatted(clientId)))
                .andExpect(status().isBadRequest());

        assertThat(clients.findByClientId(clientId)).isNull();
    }

    @Test
    void keepsClientIdGloballyUniqueAcrossTenants() throws Exception {
        Tenant firstTenant = tenant("oauth-first");
        Tenant secondTenant = tenant("oauth-second");
        TenantMembership firstActor = actor(firstTenant, "first-manager", PermissionCode.OAUTH_CLIENTS_MANAGE);
        TenantMembership secondActor = actor(secondTenant, "second-manager", PermissionCode.OAUTH_CLIENTS_MANAGE);
        String clientId = "global-" + shortSuffix();
        String payload = createPayload(
                clientId,
                "Cliente global",
                "https://global.example.test/callback",
                "https://global.example.test/logout");

        mockMvc.perform(post(baseUrl(firstTenant))
                        .header("Authorization", bearer(firstActor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());
        mockMvc.perform(post(baseUrl(secondTenant))
                        .header("Authorization", bearer(secondActor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Client ID já cadastrado"));
    }

    @Test
    void recordsSuccessfulClientChangesWithoutSensitiveRequestData() throws Exception {
        Tenant tenant = tenant("oauth-audit-success");
        TenantMembership actor = actor(
                tenant,
                "oauth-auditor",
                PermissionCode.OAUTH_CLIENTS_MANAGE,
                PermissionCode.SECURITY_AUDIT_READ);
        String clientId = "audited-" + shortSuffix();
        String redirectUri = "https://sensitive-location.example.test/callback";

        mockMvc.perform(post(baseUrl(tenant))
                        .header("Authorization", bearer(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(
                                clientId,
                                "Cliente auditado",
                                redirectUri,
                                "https://sensitive-location.example.test/logout")))
                .andExpect(status().isCreated());

        String auditResponse = mockMvc.perform(get(auditUrl(tenant))
                        .header("Authorization", bearer(actor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventType").value("OAUTH_CLIENT_CREATED"))
                .andExpect(jsonPath("$.items[0].result").value("SUCCEEDED"))
                .andExpect(jsonPath("$.items[0].actorId").value(actor.getUser().getId()))
                .andExpect(jsonPath("$.items[0].targetId").value(clientId))
                .andExpect(jsonPath("$.items[0].reasonCode").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(auditResponse).doesNotContain(redirectUri, "Cliente auditado", "refresh_token", "access_token");
    }

    @Test
    void recordsDeniedCrossTenantChangesAndProtectsTheAuditQuery() throws Exception {
        Tenant actorTenant = tenant("oauth-audit-actor");
        Tenant targetTenant = tenant("oauth-audit-target");
        TenantMembership deniedActor = actor(
                actorTenant, "denied-audit-actor", PermissionCode.OAUTH_CLIENTS_MANAGE);
        TenantMembership targetAuditor = actor(
                targetTenant, "target-auditor", PermissionCode.SECURITY_AUDIT_READ);
        String clientId = "denied-" + shortSuffix();

        mockMvc.perform(post(baseUrl(targetTenant))
                        .header("Authorization", bearer(deniedActor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload(
                                clientId,
                                "Cliente negado",
                                "https://denied.example.test/callback",
                                "https://denied.example.test/logout")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(auditUrl(targetTenant))
                        .header("Authorization", bearer(deniedActor)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(auditUrl(targetTenant))
                        .header("Authorization", bearer(targetAuditor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].eventType").value("OAUTH_CLIENT_CREATED"))
                .andExpect(jsonPath("$.items[0].result").value("DENIED"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("MISSING_PERMISSION"))
                .andExpect(jsonPath("$.items[0].actorId").value(deniedActor.getUser().getId()))
                .andExpect(jsonPath("$.items[0].targetId").value(clientId));
    }

    private Tenant tenant(String label) {
        String suffix = UUID.randomUUID().toString();
        return tenants.save(new Tenant(label + "-" + suffix, "Tenant " + label));
    }

    private TenantMembership actor(Tenant tenant, String label, PermissionCode... grantedPermissions) {
        TenantRole role = new TenantRole(tenant, label + "-role", "Papel " + label, false);
        for (PermissionCode permission : grantedPermissions) {
            role.grant(permissions.findById(permission.value()).orElseThrow());
        }
        role = roles.save(role);
        IdentityUser user = users.save(new IdentityUser(
                label + "-" + UUID.randomUUID() + "@example.test", "Usuário " + label, "not-used"));
        TenantMembership membership = new TenantMembership(tenant, user);
        membership.assignRole(role);
        return memberships.save(membership);
    }

    private String bearer(TenantMembership actor) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject(actor.getUser().getId())
                .audience(List.of(AdminResourceContract.AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", AdminResourceContract.SCOPE)
                .build();
        String token = new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims))
                .getTokenValue();
        return "Bearer " + token;
    }

    private String persistRenewableState(RegisteredClient client, TenantMembership actor) {
        String authorizationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        jdbc.update("""
                insert into oauth2_authorization (
                    id, registered_client_id, principal_name, authorization_grant_type, authorized_scopes
                ) values (?, ?, ?, ?, ?)
                """, authorizationId, client.getId(), actor.getUser().getId(), "authorization_code", "openid");
        jdbc.update("""
                insert into oauth2_authorization_consent (
                    registered_client_id, principal_name, authorities
                ) values (?, ?, ?)
                """, client.getId(), actor.getUser().getId(), "SCOPE_openid");
        jdbc.update("""
                insert into oauth_refresh_token_family (
                    id, authorization_id, current_token_hash, status, version,
                    created_at, last_rotated_at, expires_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?)
                """, authorizationId, authorizationId, "a".repeat(64), "ACTIVE", 0, now, now, now.plusSeconds(300));
        jdbc.update("""
                insert into oauth_refresh_token_history (
                    token_hash, family_id, status, issued_at
                ) values (?, ?, ?, ?)
                """, "b".repeat(64), authorizationId, "CURRENT", now);
        return authorizationId;
    }

    private int count(String table, String column, String value) {
        return jdbc.queryForObject(
                "select count(*) from " + table + " where " + column + " = ?",
                Integer.class,
                value);
    }

    private static String baseUrl(Tenant tenant) {
        return "/api/v1/admin/tenants/" + tenant.getId() + "/oauth-clients";
    }

    private static String auditUrl(Tenant tenant) {
        return "/api/v1/admin/tenants/" + tenant.getId() + "/audit-events";
    }

    private static String shortSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String createPayload(
            String clientId,
            String clientName,
            String redirectUri,
            String postLogoutRedirectUri) {
        return """
                {
                  "clientId":"%s",
                  "clientName":"%s",
                  "redirectUris":["%s"],
                  "postLogoutRedirectUris":["%s"],
                  "scopes":["openid","profile","email"]
                }
                """.formatted(clientId, clientName, redirectUri, postLogoutRedirectUri);
    }

    private static String updatePayload(String clientName, String redirectUri) {
        return """
                {
                  "clientName":"%s",
                  "redirectUris":["%s"],
                  "postLogoutRedirectUris":[],
                  "scopes":["openid","profile"]
                }
                """.formatted(clientName, redirectUri);
    }
}
