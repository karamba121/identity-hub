package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.identity.PasswordRecoverySender;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CriticalSessionInvalidationIntegrationTests {

    private static final Pattern HIDDEN_INPUT = Pattern.compile(
            "name=\\\"([^\\\"]+)\\\" value=\\\"([^\\\"]*)\\\"");
    private static final String VERIFIER =
            "credential-event-verifier-with-more-than-forty-three-characters-123";
    private static final String ORIGINAL_PASSWORD = "Original session phrase 2026";
    private static final String NEW_PASSWORD = "New session phrase after recovery 2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcOperations jdbc;

    @Autowired
    private SessionRegistry sessions;

    @MockitoBean
    private PasswordRecoverySender sender;

    @Test
    void passwordRecoveryRevokesGrantsAccessTokensAndSsoSessions() throws Exception {
        IdentityUser user = users.save(new IdentityUser(
                "sessions-" + UUID.randomUUID() + "@example.test",
                "Pessoa com sessões",
                passwordEncoder.encode(ORIGINAL_PASSWORD)));
        TokenPair tokens = issueTokens(user.getEmail(), ORIGINAL_PASSWORD);
        Object otherPrincipal = User.withUsername("other-" + UUID.randomUUID() + "@example.test")
                .password("not-used")
                .roles("USER")
                .build();
        sessions.registerNewSession("other-session-" + UUID.randomUUID(), otherPrincipal);

        assertThat(jdbc.queryForObject(
                "select count(*) from oauth2_authorization where principal_name = ?",
                Integer.class,
                user.getEmail())).isPositive();
        mockMvc.perform(get("/api/v1/demo/resource")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/password-recovery")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(user.getEmail())))
                .andExpect(status().isAccepted());
        ArgumentCaptor<String> recoveryLink = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq(user.getEmail()), eq(user.getDisplayName()), recoveryLink.capture());
        String rawToken = URI.create(recoveryLink.getValue()).getFragment().substring("token=".length());

        mockMvc.perform(post("/api/v1/password-recovery/complete")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"%s"}
                                """.formatted(rawToken, NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        assertThat(users.findById(user.getId()).orElseThrow().getCredentialVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select count(*) from oauth2_authorization where principal_name = ?",
                Integer.class,
                user.getEmail())).isZero();
        Object userPrincipal = sessions.getAllPrincipals().stream()
                .filter(principal -> principal.toString().contains(user.getEmail()))
                .findFirst()
                .orElseThrow();
        assertThat(sessions.getAllSessions(userPrincipal, true))
                .isNotEmpty()
                .allSatisfy(session -> assertThat(session.isExpired()).isTrue());
        assertThat(sessions.getAllSessions(otherPrincipal, false))
                .isNotEmpty()
                .allSatisfy(session -> assertThat(session.isExpired()).isFalse());

        mockMvc.perform(get("/api/v1/demo/resource")
                        .header("Authorization", "Bearer " + tokens.accessToken()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", "identity-hub-demo")
                        .param("refresh_token", tokens.refreshToken()))
                .andExpect(status().isBadRequest());
    }

    private TokenPair issueTokens(String email, String password) throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        MvcResult authorizationStart = mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identity-hub-demo")
                        .queryParam("redirect_uri", "http://localhost:4200/demo/callback")
                        .queryParam("scope", "openid profile email demo.read")
                        .queryParam("state", "credential-state")
                        .queryParam("nonce", "credential-nonce")
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) authorizationStart.getRequest().getSession(false);
        String loginInteractionId = queryParameter(
                authorizationStart.getResponse().getRedirectedUrl(), "interaction_id");
        MvcResult login = mockMvc.perform(post("/api/v1/interactions/{id}/login", loginInteractionId)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();

        String resumeUrl = JsonPath.read(login.getResponse().getContentAsString(), "$.continueUrl");
        MvcResult consentRedirect = mockMvc.perform(get(URI.create(resumeUrl)).session(session))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        String callback = consentRedirect.getResponse().getRedirectedUrl();
        if (!callback.startsWith("http://localhost:4200/demo/callback")) {
            MvcResult consentPage = mockMvc.perform(get(URI.create(callback)).session(session))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            String consentInteractionId = queryParameter(
                    consentPage.getResponse().getRedirectedUrl(), "interaction_id");
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
            MvcResult authorizationResponse = mockMvc.perform(post("/oauth2/authorize")
                            .session(session)
                            .params(hiddenInputs(bridge.getResponse().getContentAsString())))
                    .andExpect(status().is3xxRedirection())
                    .andReturn();
            callback = authorizationResponse.getResponse().getRedirectedUrl();
        }

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "identity-hub-demo")
                        .param("redirect_uri", "http://localhost:4200/demo/callback")
                        .param("code", queryParameter(callback, "code"))
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn();
        String body = token.getResponse().getContentAsString();
        return new TokenPair(JsonPath.read(body, "$.access_token"), JsonPath.read(body, "$.refresh_token"));
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

    private record TokenPair(String accessToken, String refreshToken) {
    }
}
