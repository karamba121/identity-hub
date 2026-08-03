package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;
import com.karamba121.backend.features.access.AdminResourceContract;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthorizationFlowIntegrationTests {

    private static final Pattern HIDDEN_INPUT = Pattern.compile(
            "name=\\\"([^\\\"]+)\\\" value=\\\"([^\\\"]*)\\\"");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenants;

    @Autowired
    private TenantMembershipRepository memberships;

    @Autowired
    private IdentityUserRepository users;

    @Test
    void completesAuthorizationCodeWithPkceThroughOpaqueInteractions() throws Exception {
        String verifier = "a-secure-verifier-with-more-than-forty-three-characters-123456789";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        MvcResult authorizationStart = mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identity-hub-demo")
                        .queryParam("redirect_uri", "http://localhost:4200/demo/callback")
                        .queryParam("scope", "openid profile email demo.read " + AdminResourceContract.SCOPE)
                        .queryParam("state", "browser-state")
                        .queryParam("nonce", "browser-nonce")
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) authorizationStart.getRequest().getSession(false);
        String loginInteractionId = queryParameter(
                authorizationStart.getResponse().getRedirectedUrl(), "interaction_id");
        assertThat(loginInteractionId).hasSizeGreaterThan(40);

        mockMvc.perform(get("/api/v1/interactions/{id}", loginInteractionId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("login"))
                .andExpect(jsonPath("$.clientName").value("Cliente demonstrativo Identity Hub"))
                .andExpect(jsonPath("$.redirectUri").doesNotExist())
                .andExpect(jsonPath("$.state").doesNotExist());

        mockMvc.perform(get("/api/v1/interactions/{id}", loginInteractionId)
                        .session(new MockHttpSession()))
                .andExpect(status().isNotFound());

        MvcResult login = mockMvc.perform(post("/api/v1/interactions/{id}/login", loginInteractionId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@identityhub.local","password":"TestSecureAccessPhrase123!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String resumeUrl = JsonPath.read(login.getResponse().getContentAsString(), "$.continueUrl");

        MvcResult consentRedirect = mockMvc.perform(get(URI.create(resumeUrl)).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        URI consentUri = URI.create(consentRedirect.getResponse().getRedirectedUrl());

        MvcResult consentPage = mockMvc.perform(get(consentUri).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String consentInteractionId = queryParameter(
                consentPage.getResponse().getRedirectedUrl(), "interaction_id");

        mockMvc.perform(get("/api/v1/interactions/{id}", consentInteractionId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("consent"))
                .andExpect(jsonPath("$.scopes.length()").value(5));

        MvcResult consent = mockMvc.perform(post("/api/v1/interactions/{id}/consent", consentInteractionId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        String continueUrl = JsonPath.read(consent.getResponse().getContentAsString(), "$.continueUrl");

        MvcResult bridge = mockMvc.perform(get(continueUrl).session(session))
                .andExpect(status().isOk())
                .andReturn();
        MultiValueMap<String, String> consentParameters = hiddenInputs(
                bridge.getResponse().getContentAsString());

        MvcResult authorizationResponse = mockMvc.perform(post("/oauth2/authorize")
                        .session(session)
                        .params(consentParameters))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String callback = authorizationResponse.getResponse().getRedirectedUrl();
        assertThat(queryParameter(callback, "state")).isEqualTo("browser-state");
        String code = queryParameter(callback, "code");

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "identity-hub-demo")
                        .param("redirect_uri", "http://localhost:4200/demo/callback")
                        .param("code", code)
                        .param("code_verifier", verifier))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").isNotEmpty())
                .andExpect(jsonPath("$.id_token").isNotEmpty())
                .andReturn();
        String accessToken = JsonPath.read(token.getResponse().getContentAsString(), "$.access_token");

        mockMvc.perform(get("/userinfo").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Administrador Identity Hub"))
                .andExpect(jsonPath("$.email").value("admin@identityhub.local"));

        mockMvc.perform(get("/api/v1/demo/resource")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Acesso autorizado à API protegida"))
                .andExpect(jsonPath("$.audience[0]").value("identity-hub-api"))
                .andExpect(jsonPath("$.scopes").value(org.hamcrest.Matchers.hasItem("demo.read")));

        Tenant tenant = tenants.findBySlugIgnoreCase("identity-hub-demo").orElseThrow();
        String administratorId = users.findByEmailIgnoreCase("admin@identityhub.local")
                .orElseThrow()
                .getId();
        TenantMembership administrator = memberships.findByTenantIdAndUserId(
                tenant.getId(), administratorId).orElseThrow();
        mockMvc.perform(delete("/api/v1/admin/tenants/{tenantId}/memberships/{membershipId}",
                        tenant.getId(), administrator.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "identity-hub-demo")
                        .param("redirect_uri", "http://localhost:4200/demo/callback")
                        .param("code", code)
                        .param("code_verifier", verifier))
                .andExpect(status().isBadRequest());
    }

    private static String queryParameter(String url, String name) {
        return UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
    }

    private static MultiValueMap<String, String> hiddenInputs(String html) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        Matcher matcher = HIDDEN_INPUT.matcher(html);
        while (matcher.find()) {
            parameters.add(matcher.group(1), matcher.group(2));
        }
        return parameters;
    }
}
