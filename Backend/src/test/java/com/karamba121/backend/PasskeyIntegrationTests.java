package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PasskeyIntegrationTests {

    private static final String USERNAME = "admin@identityhub.local";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PublicKeyCredentialUserEntityRepository userEntities;

    @Autowired
    private UserCredentialRepository credentials;

    @Test
    void createsRegistrationAndAuthenticationOptionsWithRequiredUserVerification() throws Exception {
        mockMvc.perform(post("/webauthn/register/options").with(csrf()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/webauthn/register/options")
                        .with(user(USERNAME))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rp.id").value("localhost"))
                .andExpect(jsonPath("$.user.name").value(USERNAME))
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andExpect(jsonPath("$.authenticatorSelection.residentKey").value("required"))
                .andExpect(jsonPath("$.authenticatorSelection.userVerification").value("required"));

        mockMvc.perform(post("/webauthn/authenticate/options").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rpId").value("localhost"))
                .andExpect(jsonPath("$.challenge").isNotEmpty())
                .andExpect(jsonPath("$.allowCredentials").isEmpty())
                .andExpect(jsonPath("$.userVerification").value("required"));
    }

    @Test
    void listsAndRemovesOnlyTheAuthenticatedUsersCredential() throws Exception {
        PublicKeyCredentialUserEntity owner = userEntities.findByUsername(USERNAME);
        if (owner == null) {
            owner = userEntity(USERNAME);
            userEntities.save(owner);
        }
        CredentialRecord credential = credential(owner.getId(), "Notebook de teste");
        credentials.save(credential);

        mockMvc.perform(get("/api/v1/passkeys").with(user(USERNAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(credential.getCredentialId().toBase64UrlString()))
                .andExpect(jsonPath("$[0].label").value("Notebook de teste"));

        mockMvc.perform(delete("/api/v1/passkeys/{id}", credential.getCredentialId().toBase64UrlString())
                        .with(user("other-passkey@example.test"))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(credentials.findByCredentialId(credential.getCredentialId())).isNotNull();

        mockMvc.perform(delete("/api/v1/passkeys/{id}", credential.getCredentialId().toBase64UrlString())
                        .with(user(USERNAME))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(credentials.findByCredentialId(credential.getCredentialId())).isNull();
    }

    @Test
    void completesOpaqueLoginInteractionOnlyWithFreshWebAuthnAuthority() throws Exception {
        MvcResult withoutFactor = startAuthorization();
        MockHttpSession firstSession = (MockHttpSession) withoutFactor.getRequest().getSession(false);
        String firstInteraction = interactionId(withoutFactor);

        mockMvc.perform(post("/api/v1/interactions/{id}/passkey", firstInteraction)
                        .session(firstSession)
                        .with(user(USERNAME))
                        .with(csrf()))
                .andExpect(status().isUnauthorized());

        MvcResult withFactor = startAuthorization();
        MockHttpSession secondSession = (MockHttpSession) withFactor.getRequest().getSession(false);
        String secondInteraction = interactionId(withFactor);
        mockMvc.perform(post("/api/v1/interactions/{id}/passkey", secondInteraction)
                        .session(secondSession)
                        .with(user(USERNAME).authorities(
                                new SimpleGrantedAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY)))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.continueUrl").value(
                        org.hamcrest.Matchers.containsString("/oauth2/authorize")))
                .andExpect(jsonPath("$.mfaRequired").value(false));
    }

    @Test
    void rejectsMissingOrReplayedAuthenticationCeremony() throws Exception {
        mockMvc.perform(post("/login/webauthn")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
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

    private static String interactionId(MvcResult result) {
        return UriComponentsBuilder.fromUriString(result.getResponse().getRedirectedUrl())
                .build().getQueryParams().getFirst("interaction_id");
    }

    private static PublicKeyCredentialUserEntity userEntity(String username) {
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.random())
                .name(username)
                .displayName(username)
                .build();
    }

    private static CredentialRecord credential(Bytes userId, String label) {
        Instant now = Instant.now();
        return ImmutableCredentialRecord.builder()
                .credentialId(Bytes.random())
                .userEntityUserId(userId)
                .publicKey(new ImmutablePublicKeyCose(new byte[] { 1, 2, 3 }))
                .signatureCount(0)
                .uvInitialized(true)
                .backupEligible(true)
                .transports(Set.of(AuthenticatorTransport.INTERNAL))
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .backupState(false)
                .created(now)
                .lastUsed(now)
                .label(label)
                .build();
    }
}
