package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PushedAuthorizationRequestIntegrationTests {

    private static final String CLIENT_ID = "identity-hub-demo";
    private static final String REDIRECT_URI = "http://localhost:4200/demo/callback";
    private static final String CODE_CHALLENGE = challenge(
            "a-secure-verifier-with-more-than-forty-three-characters-123456789");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredClientRepository clients;

    @Autowired
    private OAuth2AuthorizationService authorizations;

    @Test
    void advertisesAndCreatesShortLivedPushedAuthorizationRequestsForPublicClients() throws Exception {
        mockMvc.perform(get("/.well-known/oauth-authorization-server"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pushed_authorization_request_endpoint")
                        .value("http://localhost/oauth2/par"));

        MvcResult result = pushAuthorizationRequest()
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request_uri").value(
                        org.hamcrest.Matchers.startsWith("urn:ietf:params:oauth:request_uri:")))
                .andExpect(jsonPath("$.expires_in").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.greaterThan(0),
                                org.hamcrest.Matchers.lessThanOrEqualTo(300))))
                .andReturn();

        String requestUri = JsonPath.read(result.getResponse().getContentAsString(), "$.request_uri");
        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("request_uri", requestUri))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void rejectsPublicParWithoutPkce() throws Exception {
        mockMvc.perform(post("/oauth2/par")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("response_type", "code")
                        .param("client_id", CLIENT_ID)
                        .param("redirect_uri", REDIRECT_URI)
                        .param("scope", "openid profile")
                        .param("state", "missing-pkce"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"));
    }

    @Test
    void bindsRequestUriToClientAndRejectsTampering() throws Exception {
        String requestUri = pushedRequestUri();
        RegisteredClient demo = clients.findByClientId(CLIENT_ID);
        String otherClientId = "par-other-" + UUID.randomUUID();
        clients.save(RegisteredClient.from(demo)
                .id(UUID.randomUUID().toString())
                .clientId(otherClientId)
                .build());

        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", otherClientId)
                        .queryParam("request_uri", requestUri))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("request_uri", requestUri + "tampered"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consumesRequestUriOnlyOnce() throws Exception {
        String requestUri = pushedRequestUri();

        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("request_uri", requestUri)
                        .with(user("admin@identityhub.local")))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("request_uri", requestUri)
                        .with(user("admin@identityhub.local")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAndRemovesExpiredRequestUri() throws Exception {
        RegisteredClient client = clients.findByClientId(CLIENT_ID);
        String state = "expired-par-state___" + Instant.now().minusSeconds(1).toEpochMilli();
        OAuth2AuthorizationRequest request = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri("http://localhost/oauth2/authorize")
                .clientId(CLIENT_ID)
                .redirectUri(REDIRECT_URI)
                .scopes(Set.of("openid", "profile"))
                .state("expired-browser-state")
                .additionalParameters(java.util.Map.of(
                        "code_challenge", CODE_CHALLENGE,
                        "code_challenge_method", "S256"))
                .build();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .principalName(CLIENT_ID)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .attribute(OAuth2AuthorizationRequest.class.getName(), request)
                .attribute(OAuth2ParameterNames.STATE, state)
                .build();
        authorizations.save(authorization);

        mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("client_id", CLIENT_ID)
                        .queryParam("request_uri", "urn:ietf:params:oauth:request_uri:" + state))
                .andExpect(status().isBadRequest());

        assertThat(authorizations.findByToken(state, new OAuth2TokenType("state"))).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions pushAuthorizationRequest() throws Exception {
        return mockMvc.perform(post("/oauth2/par")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("response_type", "code")
                .param("client_id", CLIENT_ID)
                .param("redirect_uri", REDIRECT_URI)
                .param("scope", "openid profile email demo.read")
                .param("state", "browser-state")
                .param("nonce", "browser-nonce")
                .param("code_challenge", CODE_CHALLENGE)
                .param("code_challenge_method", "S256"));
    }

    private String pushedRequestUri() throws Exception {
        MvcResult result = pushAuthorizationRequest()
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.request_uri");
    }

    private static String challenge(String verifier) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
