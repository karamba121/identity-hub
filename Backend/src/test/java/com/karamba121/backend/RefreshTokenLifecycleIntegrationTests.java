package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import com.jayway.jsonpath.JsonPath;
import com.karamba121.backend.features.session.RefreshTokenFamilyRepository;
import com.karamba121.backend.features.session.SessionMetrics;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RefreshTokenLifecycleIntegrationTests {

    private static final Pattern HIDDEN_INPUT = Pattern.compile(
            "name=\\\"([^\\\"]+)\\\" value=\\\"([^\\\"]*)\\\"");
    private static final String VERIFIER =
            "a-secure-verifier-with-more-than-forty-three-characters-123456789";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PrometheusMeterRegistry meterRegistry;

    @MockitoSpyBean
    private RefreshTokenFamilyRepository families;

    @Test
    void rotatesRefreshTokenAndRevokesFamilyWhenAUsedTokenIsReplayed() throws Exception {
        String first = issueTokens().refreshToken();

        MvcResult rotation = refresh(first, 200);
        String successor = JsonPath.read(rotation.getResponse().getContentAsString(), "$.refresh_token");
        String accessToken = JsonPath.read(rotation.getResponse().getContentAsString(), "$.access_token");
        assertThat(successor).isNotEqualTo(first);
        mockMvc.perform(get("/api/v1/demo/resource")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        refresh(first, 400);
        refresh(successor, 400);
    }

    @Test
    void revokesRefreshTokenThroughTheStandardEndpoint() throws Exception {
        String refreshToken = issueTokens().refreshToken();

        mockMvc.perform(post("/oauth2/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "identity-hub-demo")
                        .param("token_type_hint", "refresh_token")
                        .param("token", refreshToken))
                .andExpect(status().isOk());

        refresh(refreshToken, 400);
    }

    @Test
    void revokesTokensAndEndsTheSsoSessionThroughOidcLogout() throws Exception {
        TokenPair tokens = issueTokens();

        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.end_session_endpoint").value("http://localhost/connect/logout"));

        mockMvc.perform(post("/oauth2/revoke")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("client_id", "identity-hub-demo")
                        .param("token_type_hint", "refresh_token")
                        .param("token", tokens.refreshToken()))
                .andExpect(status().isOk());

        MvcResult logout = mockMvc.perform(get("/connect/logout")
                        .session(tokens.session())
                        .queryParam("id_token_hint", tokens.idToken())
                        .queryParam("post_logout_redirect_uri", "http://localhost:4200/demo/logout")
                        .queryParam("state", "logout-state"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        assertThat(logout.getResponse().getRedirectedUrl())
                .isEqualTo("http://localhost:4200/demo/logout?state=logout-state");
        assertThat(tokens.session().isInvalid()).isTrue();
        refresh(tokens.refreshToken(), 400);

        MvcResult authorizationAfterLogout = mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identity-hub-demo")
                        .queryParam("redirect_uri", "http://localhost:4200/demo/callback")
                        .queryParam("scope", "openid")
                        .queryParam("state", "new-state")
                        .queryParam("nonce", "new-nonce")
                        .queryParam("code_challenge", "challenge-with-more-than-forty-three-characters-123")
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        assertThat(queryParameter(
                authorizationAfterLogout.getResponse().getRedirectedUrl(), "interaction_id"))
                .isNotBlank();
    }

    @Test
    void doesNotRedirectLogoutToAnUnregisteredUri() throws Exception {
        TokenPair tokens = issueTokens();

        MvcResult logout = mockMvc.perform(get("/connect/logout")
                        .session(tokens.session())
                        .queryParam("id_token_hint", tokens.idToken())
                        .queryParam("post_logout_redirect_uri", "https://attacker.example/callback")
                        .queryParam("state", "attacker-state"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(logout.getResponse().getRedirectedUrl()).isNull();
    }

    @Test
    void exposesOnlyBoundedSessionMetricsThroughTheProtectedPrometheusEndpoint() throws Exception {
        double familiesBefore = eventCount(SessionMetrics.FAMILY_CREATED);
        double rotationsBefore = eventCount(SessionMetrics.ROTATED);
        double replaysBefore = eventCount(SessionMetrics.REPLAY_DETECTED);
        long successesBefore = refreshOutcomeCount(SessionMetrics.SUCCESS);
        long rejectionsBefore = refreshOutcomeCount(SessionMetrics.REJECTED);

        String first = issueTokens().refreshToken();
        MvcResult rotation = refresh(first, 200);
        refresh(first, 400);

        assertThat(eventCount(SessionMetrics.FAMILY_CREATED)).isEqualTo(familiesBefore + 1);
        assertThat(eventCount(SessionMetrics.ROTATED)).isEqualTo(rotationsBefore + 1);
        assertThat(eventCount(SessionMetrics.REPLAY_DETECTED)).isEqualTo(replaysBefore + 1);
        assertThat(refreshOutcomeCount(SessionMetrics.SUCCESS)).isEqualTo(successesBefore + 1);
        assertThat(refreshOutcomeCount(SessionMetrics.REJECTED)).isEqualTo(rejectionsBefore + 1);
        assertThat(JsonPath.<String>read(
                rotation.getResponse().getContentAsString(), "$.refresh_token")).isNotBlank();

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").with(user("monitor")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString(
                                "identity_hub_session_refresh_events_total")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(first))));
    }

    @Test
    void failsClosedAndRecordsUnavailableWhenRefreshPersistenceIsDown() throws Exception {
        String refreshToken = issueTokens().refreshToken();
        long unavailableBefore = refreshOutcomeCount(SessionMetrics.UNAVAILABLE);
        doThrow(new DataAccessResourceFailureException("simulated persistence outage"))
                .when(families).findByIdForUpdate(anyString());

        assertThatThrownBy(() -> refreshWithoutExpectation(refreshToken))
                .isInstanceOf(DataAccessResourceFailureException.class);
        assertThat(refreshOutcomeCount(SessionMetrics.UNAVAILABLE))
                .isEqualTo(unavailableBefore + 1);
    }

    @Test
    void allowsOnlyOneSuccessorWhenRefreshTokenIsUsedConcurrently() throws Exception {
        String refreshToken = issueTokens().refreshToken();
        CyclicBarrier start = new CyclicBarrier(2);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> {
                start.await();
                return refreshWithoutExpectation(refreshToken);
            });
            Future<MvcResult> second = executor.submit(() -> {
                start.await();
                return refreshWithoutExpectation(refreshToken);
            });

            List<MvcResult> results = List.of(first.get(), second.get());
            assertThat(results).extracting(result -> result.getResponse().getStatus())
                    .containsExactlyInAnyOrder(200, 400);

            MvcResult success = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 200)
                    .findFirst()
                    .orElseThrow();
            String successor = JsonPath.read(success.getResponse().getContentAsString(), "$.refresh_token");
            refresh(successor, 400);
        } finally {
            executor.shutdownNow();
        }
    }

    private TokenPair issueTokens() throws Exception {
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(VERIFIER.getBytes(StandardCharsets.US_ASCII)));
        MvcResult authorizationStart = mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identity-hub-demo")
                        .queryParam("redirect_uri", "http://localhost:4200/demo/callback")
                        .queryParam("scope", "openid profile email demo.read")
                        .queryParam("state", "refresh-state")
                        .queryParam("nonce", "refresh-nonce")
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
                        .content("""
                                {"email":"admin@identityhub.local","password":"TestPassword123!"}
                                """))
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
        String code = queryParameter(callback, "code");

        MvcResult token = mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "authorization_code")
                        .param("client_id", "identity-hub-demo")
                        .param("redirect_uri", "http://localhost:4200/demo/callback")
                        .param("code", code)
                        .param("code_verifier", VERIFIER))
                .andExpect(status().isOk())
                .andReturn();
        String body = token.getResponse().getContentAsString();
        return new TokenPair(
                JsonPath.read(body, "$.access_token"),
                JsonPath.read(body, "$.refresh_token"),
                JsonPath.read(body, "$.id_token"),
                session);
    }

    private MvcResult refresh(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result = refreshWithoutExpectation(refreshToken);
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        return result;
    }

    private MvcResult refreshWithoutExpectation(String refreshToken) throws Exception {
        return mockMvc.perform(post("/oauth2/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "refresh_token")
                        .param("client_id", "identity-hub-demo")
                        .param("refresh_token", refreshToken))
                .andReturn();
    }

    private double eventCount(String event) {
        return meterRegistry.get("identity_hub.session.refresh.events")
                .tag("event", event)
                .counter()
                .count();
    }

    private long refreshOutcomeCount(String outcome) {
        return meterRegistry.get("identity_hub.session.refresh.duration")
                .tag("outcome", outcome)
                .timer()
                .count();
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

    private record TokenPair(
            String accessToken,
            String refreshToken,
            String idToken,
            MockHttpSession session) {
    }
}
