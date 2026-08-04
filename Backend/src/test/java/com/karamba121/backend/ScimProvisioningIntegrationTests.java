package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.karamba121.backend.features.access.SecurityAuditEventRepository;
import com.karamba121.backend.features.access.SecurityAuditEventType;
import com.karamba121.backend.features.access.OAuthClientAdministrationService;
import com.karamba121.backend.features.access.TenantOAuthClient;
import com.karamba121.backend.features.access.TenantOAuthClientRepository;
import com.karamba121.backend.features.scim.ScimResourceContract;
import com.karamba121.backend.features.scim.ScimUserResourceRepository;
import com.karamba121.backend.features.tenancy.MembershipStatus;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ScimProvisioningIntegrationTests {

    private static final MediaType SCIM = MediaType.parseMediaType("application/scim+json");

    @Autowired MockMvc mockMvc;
    @Autowired TenantRepository tenants;
    @Autowired TenantOAuthClientRepository ownerships;
    @Autowired RegisteredClientRepository registeredClients;
    @Autowired TenantMembershipRepository memberships;
    @Autowired ScimUserResourceRepository resources;
    @Autowired SecurityAuditEventRepository audit;
    @Autowired JWKSource<SecurityContext> jwkSource;
    @Autowired OAuthClientAdministrationService clientAdministration;
    @Autowired @Qualifier("scimResourceJwtDecoder") JwtDecoder scimDecoder;

    @Test
    void provisionsListsUpdatesSuspendsAndDeletesAUser() throws Exception {
        Tenant tenant = tenant("lifecycle");
        String clientId = client(tenant, ScimResourceContract.READ_SCOPE, ScimResourceContract.WRITE_SCOPE);
        String token = bearer(clientId, ScimResourceContract.AUDIENCE,
                ScimResourceContract.READ_SCOPE + " " + ScimResourceContract.WRITE_SCOPE);

        String response = mockMvc.perform(post(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(SCIM)
                        .content(userPayload("person@example.test", "Pessoa SCIM", "directory-42", true)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"1\""))
                .andExpect(jsonPath("$.schemas[0]").value(ScimResourceContract.USER_SCHEMA))
                .andExpect(jsonPath("$.userName").value("person@example.test"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();

        mockMvc.perform(get(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("filter", "externalId eq \"directory-42\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalResults").value(1))
                .andExpect(jsonPath("$.Resources[0].id").value(id));

        mockMvc.perform(put(usersUrl(tenant) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, "W/\"1\"")
                        .contentType(SCIM)
                        .content(userPayload("person@example.test", "Pessoa Atualizada", "directory-42", true)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "W/\"2\""))
                .andExpect(jsonPath("$.displayName").value("Pessoa Atualizada"));

        mockMvc.perform(patch(usersUrl(tenant) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, "W/\"2\"")
                        .contentType(SCIM)
                        .content("""
                                {"schemas":["%s"],"Operations":[
                                  {"op":"replace","path":"active","value":false}
                                ]}
                                """.formatted(ScimResourceContract.PATCH_SCHEMA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        assertThat(resources.findByIdAndTenantIdAndDeletedAtIsNull(id, tenant.getId())
                .orElseThrow().getMembership().getStatus())
                .isEqualTo(MembershipStatus.SUSPENDED);

        mockMvc.perform(delete(usersUrl(tenant) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, "W/\"3\""))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(usersUrl(tenant) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.schemas[0]").value(ScimResourceContract.ERROR_SCHEMA));

        assertThat(audit.findAllByTenantId(tenant.getId(), PageRequest.of(0, 20))
                .stream().map(event -> event.getEventType()))
                .contains(
                        SecurityAuditEventType.SCIM_USER_CREATED,
                        SecurityAuditEventType.SCIM_USER_UPDATED,
                        SecurityAuditEventType.SCIM_USER_DELETED);
    }

    @Test
    void isolatesTheClientToItsOwningTenantAndAuditsTheDenial() throws Exception {
        Tenant owner = tenant("owner");
        Tenant foreign = tenant("foreign");
        String clientId = client(owner, ScimResourceContract.WRITE_SCOPE);

        mockMvc.perform(post(usersUrl(foreign))
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(clientId, ScimResourceContract.AUDIENCE, ScimResourceContract.WRITE_SCOPE))
                        .contentType(SCIM)
                        .content(userPayload("foreign@example.test", "Foreign", null, true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value("403"));

        assertThat(resources.findByTenantIdAndUserNameIgnoreCase(
                foreign.getId(), "foreign@example.test")).isEmpty();
        assertThat(audit.findAllByTenantId(foreign.getId(), PageRequest.of(0, 20)).stream()
                .filter(event -> event.getEventType() == SecurityAuditEventType.SCIM_USER_CREATED)
                .anyMatch(event -> "CLIENT_TENANT_MISMATCH".equals(event.getReasonCode())))
                .isTrue();
    }

    @Test
    void separatesReadAndWriteScopesAndRequiresTheScimAudience() throws Exception {
        Tenant tenant = tenant("scopes");
        String clientId = client(tenant, ScimResourceContract.READ_SCOPE, ScimResourceContract.WRITE_SCOPE);

        mockMvc.perform(get(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(clientId, ScimResourceContract.AUDIENCE, ScimResourceContract.READ_SCOPE)))
                .andExpect(status().isOk());
        mockMvc.perform(post(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(clientId, ScimResourceContract.AUDIENCE, ScimResourceContract.READ_SCOPE))
                        .contentType(SCIM)
                        .content(userPayload("readonly@example.test", "Read only", null, true)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION,
                                bearer(clientId, "another-audience", ScimResourceContract.READ_SCOPE)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publishesDiscoveryDocumentsAndRejectsUnsupportedFiltersAndStaleVersions() throws Exception {
        Tenant tenant = tenant("contracts");
        String clientId = client(tenant, ScimResourceContract.READ_SCOPE, ScimResourceContract.WRITE_SCOPE);
        String token = bearer(clientId, ScimResourceContract.AUDIENCE,
                ScimResourceContract.READ_SCOPE + " " + ScimResourceContract.WRITE_SCOPE);

        mockMvc.perform(get(baseUrl(tenant) + "/ServiceProviderConfig")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patch.supported").value(true))
                .andExpect(jsonPath("$.bulk.supported").value(false));
        mockMvc.perform(get(baseUrl(tenant) + "/ResourceTypes")
                        .header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.Resources[0].endpoint").value("/Users"));
        mockMvc.perform(get(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .param("filter", "displayName co \"x\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.scimType").value("invalidFilter"));

        String body = mockMvc.perform(post(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(SCIM)
                        .content(userPayload("etag@example.test", "ETag", null, true)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asText();
        mockMvc.perform(delete(usersUrl(tenant) + "/" + id)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .header(HttpHeaders.IF_MATCH, "W/\"99\""))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    void preventsDuplicateExternalIdsAndDoesNotGrantTenantRoles() throws Exception {
        Tenant tenant = tenant("uniqueness");
        String clientId = client(tenant, ScimResourceContract.WRITE_SCOPE);
        String token = bearer(clientId, ScimResourceContract.AUDIENCE, ScimResourceContract.WRITE_SCOPE);

        mockMvc.perform(post(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(SCIM)
                        .content(userPayload("first@example.test", "First", "same-id", true)))
                .andExpect(status().isCreated());
        mockMvc.perform(post(usersUrl(tenant))
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(SCIM)
                        .content(userPayload("second@example.test", "Second", "same-id", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.scimType").value("uniqueness"));

        assertThat(resources.findByTenantIdAndUserNameIgnoreCaseAndDeletedAtIsNull(
                        tenant.getId(), "first@example.test").orElseThrow()
                .getMembership().getRole()).isNull();
    }

    @Test
    void issuesAClientCredentialsTokenWithTheExclusiveScimAudience() throws Exception {
        Tenant tenant = tenant("token");
        String clientId = "scim-token-" + UUID.randomUUID();
        OAuthClientAdministrationService.OAuthClientView created = clientAdministration.create(
                tenant.getId(),
                new OAuthClientAdministrationService.OAuthClientCommand(
                        clientId,
                        "Provisionador SCIM",
                        Set.of(),
                        Set.of(),
                        Set.of(ScimResourceContract.READ_SCOPE, ScimResourceContract.WRITE_SCOPE),
                        "CONFIDENTIAL"));
        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + created.clientSecret()).getBytes(StandardCharsets.UTF_8));

        String response = mockMvc.perform(post("/oauth2/token")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", ScimResourceContract.READ_SCOPE + " " + ScimResourceContract.WRITE_SCOPE))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("access_token").asText();
        org.springframework.security.oauth2.jwt.Jwt jwt = scimDecoder.decode(token);

        assertThat(jwt.getAudience()).containsExactly(ScimResourceContract.AUDIENCE);
        assertThat(jwt.getSubject()).isEqualTo("client:" + clientId);
        assertThat(jwt.getClaimAsString("client_id")).isEqualTo(clientId);

        assertThatThrownBy(() -> clientAdministration.create(
                tenant.getId(),
                new OAuthClientAdministrationService.OAuthClientCommand(
                        "public-scim-" + UUID.randomUUID(),
                        "Cliente público indevido",
                        Set.of("https://client.example.test/callback"),
                        Set.of(),
                        Set.of(ScimResourceContract.READ_SCOPE),
                        "PUBLIC")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cliente confidencial");
    }

    private Tenant tenant(String label) {
        String suffix = UUID.randomUUID().toString();
        return tenants.save(new Tenant(label + "-" + suffix, "Tenant " + label));
    }

    private String client(Tenant tenant, String... scopes) {
        String clientId = "scim-" + UUID.randomUUID();
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret("{noop}" + UUID.randomUUID())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scopes(values -> values.addAll(List.of(scopes)))
                .build();
        registeredClients.save(client);
        ownerships.save(new TenantOAuthClient(tenant, client.getId(), clientId));
        return clientId;
    }

    private String bearer(String clientId, String audience, String scope) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("http://localhost")
                .subject("client:" + clientId)
                .audience(List.of(audience))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("client_id", clientId)
                .claim("scope", scope)
                .build();
        String token = new NimbusJwtEncoder(jwkSource)
                .encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return "Bearer " + token;
    }

    private static String baseUrl(Tenant tenant) {
        return "/scim/v2/" + tenant.getId();
    }

    private static String usersUrl(Tenant tenant) {
        return baseUrl(tenant) + "/Users";
    }

    private static String userPayload(
            String userName, String displayName, String externalId, boolean active) {
        String external = externalId == null ? "" : ",\"externalId\":\"" + externalId + "\"";
        return """
                {"schemas":["%s"],"userName":"%s","displayName":"%s","active":%s%s}
                """.formatted(ScimResourceContract.USER_SCHEMA, userName, displayName, active, external);
    }
}
