package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeviceAuthorizationGrantIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RegisteredClientRepository clients;

    @Test
    void issuesDeviceCodeRequiresBrowserConsentAndExchangesItOnce() throws Exception {
        String clientId = deviceClient();
        MvcResult start = start(clientId);
        String deviceCode = JsonPath.read(start.getResponse().getContentAsString(), "$.device_code");
        String userCode = JsonPath.read(start.getResponse().getContentAsString(), "$.user_code");

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", AuthorizationGrantType.DEVICE_CODE.getValue())
                        .param("client_id", clientId)
                        .param("device_code", deviceCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("authorization_pending"));

        MvcResult verification = mockMvc.perform(get("/oauth2/device_verification")
                        .queryParam("user_code", userCode)
                        .with(user("admin@identityhub.local")))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String consentUrl = verification.getResponse().getRedirectedUrl();
        assertThat(consentUrl).startsWith("http://localhost:4200/device?");
        String state = query(consentUrl, "state");

        mockMvc.perform(get("/api/v1/device-authorization/consent")
                        .queryParam("client_id", clientId)
                        .queryParam("user_code", userCode)
                        .with(user("admin@identityhub.local")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientName").value("Dispositivo de teste"))
                .andExpect(jsonPath("$.scopes", org.hamcrest.Matchers.hasItems("profile", "demo.read")));

        mockMvc.perform(post("/oauth2/device_verification")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId)
                        .param("state", state)
                        .param("user_code", userCode)
                        .param("scope", "profile", "demo.read")
                        .with(user("admin@identityhub.local")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:4200/device?status=approved"));

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", AuthorizationGrantType.DEVICE_CODE.getValue())
                        .param("client_id", clientId)
                        .param("device_code", deviceCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.refresh_token").doesNotExist())
                .andReturn();
        assertThat(JsonPath.<String>read(token.getResponse().getContentAsString(), "$.scope"))
                .contains("demo.read");

        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", AuthorizationGrantType.DEVICE_CODE.getValue())
                        .param("client_id", clientId)
                        .param("device_code", deviceCode))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("access_denied"));
    }

    @Test
    void sendsUnauthenticatedVerificationThroughOpaqueLoginInteraction() throws Exception {
        String clientId = deviceClient();
        String userCode = JsonPath.read(start(clientId).getResponse().getContentAsString(), "$.user_code");

        MvcResult redirect = mockMvc.perform(get("/oauth2/device_verification")
                        .queryParam("user_code", userCode))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) redirect.getRequest().getSession(false);
        String interactionId = query(redirect.getResponse().getRedirectedUrl(), "interaction_id");

        mockMvc.perform(get("/api/v1/interactions/{id}", interactionId).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("login"))
                .andExpect(jsonPath("$.clientName").value("Dispositivo de teste"))
                .andExpect(jsonPath("$.redirectUri").doesNotExist());

        mockMvc.perform(get("/oauth2/device_verification")
                        .queryParam("user_code", "BCDF-GHJK")
                        .with(user("admin@identityhub.local")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:4200/device?status=error"));
    }

    private MvcResult start(String clientId) throws Exception {
        return mockMvc.perform(post("/oauth2/device_authorization")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", clientId)
                        .param("scope", "profile demo.read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device_code").isNotEmpty())
                .andExpect(jsonPath("$.user_code").isNotEmpty())
                .andExpect(jsonPath("$.verification_uri").value("http://localhost:4200/device"))
                .andExpect(jsonPath("$.verification_uri_complete").isNotEmpty())
                .andExpect(jsonPath("$.expires_in").value(600))
                .andReturn();
    }

    private String deviceClient() {
        String clientId = "device-" + UUID.randomUUID();
        clients.save(RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientName("Dispositivo de teste")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                .scope("profile")
                .scope("demo.read")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(true).build())
                .tokenSettings(TokenSettings.builder()
                        .deviceCodeTimeToLive(Duration.ofMinutes(10))
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .build())
                .build());
        return clientId;
    }

    private static String query(String url, String name) {
        String value = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst(name);
        return value == null ? null : URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
