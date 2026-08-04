package com.karamba121.backend;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(properties = {
        "identity-hub.federation.enabled=true",
        "identity-hub.federation.registration-id=corporate",
        "identity-hub.federation.display-name=Login corporativo",
        "identity-hub.federation.client-id=test-client",
        "identity-hub.federation.client-secret=test-secret",
        "identity-hub.federation.issuer-uri=https://issuer.example.test",
        "identity-hub.federation.authorization-uri=https://issuer.example.test/oauth2/authorize",
        "identity-hub.federation.token-uri=https://issuer.example.test/oauth2/token",
        "identity-hub.federation.jwk-set-uri=https://issuer.example.test/oauth2/jwks"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FederationAuthorizationRedirectTests {

    @Autowired MockMvc mockMvc;

    @Test
    void exposesProviderAndStartsOidcAuthorizationFromTheOpaqueInteraction() throws Exception {
        MvcResult authorization = startAuthorization();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        String interactionId = UriComponentsBuilder
                .fromUriString(authorization.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("interaction_id");

        mockMvc.perform(get("/api/v1/federation/providers").session(session))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[0].displayName").value("Login corporativo"));

        mockMvc.perform(get("/api/v1/interactions/{id}/federation/corporate", interactionId)
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/corporate"));

        mockMvc.perform(get("/oauth2/authorization/corporate").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", containsString("https://issuer.example.test/oauth2/authorize")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", containsString("openid")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", containsString("state=")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", containsString("nonce=")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Location", containsString("code_challenge=")));
    }

    @Test
    void rejectsUnknownProviderAndInteractionFromAnotherSession() throws Exception {
        MvcResult authorization = startAuthorization();
        String interactionId = UriComponentsBuilder
                .fromUriString(authorization.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("interaction_id");

        mockMvc.perform(get("/api/v1/interactions/{id}/federation/unknown", interactionId)
                        .session((MockHttpSession) authorization.getRequest().getSession(false)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/interactions/{id}/federation/corporate", interactionId)
                        .session(new MockHttpSession()))
                .andExpect(status().isNotFound());
    }

    private MvcResult startAuthorization() throws Exception {
        return mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identity-hub-demo")
                        .queryParam("redirect_uri", "http://localhost:4200/demo/callback")
                        .queryParam("scope", "openid profile")
                        .queryParam("state", Bytes.random().toBase64UrlString())
                        .queryParam("nonce", Bytes.random().toBase64UrlString())
                        .queryParam("code_challenge", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }
}
