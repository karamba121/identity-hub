package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.jayway.jsonpath.JsonPath;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.identity.TotpAlgorithm;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MfaIntegrationTests {

    @Autowired MockMvc mockMvc;
    @Autowired IdentityUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcOperations jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void enrollsTotpAndReplacesSingleUseRecoveryCodes() throws Exception {
        String email = "mfa-" + UUID.randomUUID() + "@example.test";
        users.save(new IdentityUser(email, "Pessoa MFA", passwordEncoder.encode("A secure MFA phrase for 2026")));

        MvcResult enrollment = mockMvc.perform(post("/api/v1/mfa/enrollment")
                        .with(user(email)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.otpauthUri").value(org.hamcrest.Matchers.startsWith("otpauth://totp/")))
                .andReturn();
        String secret = JsonPath.read(enrollment.getResponse().getContentAsString(), "$.secret");
        entityManager.flush();
        String storedSecret = jdbc.queryForObject(
                "select encrypted_secret from user_mfa where user_id = (select id from identity_user where email = ?)",
                String.class, email);
        assertThat(storedSecret).isNotEqualTo(secret).doesNotContain(secret);
        String totp = TotpAlgorithm.generate(secret, Instant.now());

        MvcResult confirmation = mockMvc.perform(post("/api/v1/mfa/enrollment/confirm")
                        .with(user(email)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(totp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(8))
                .andReturn();
        List<String> firstCodes = JsonPath.read(
                confirmation.getResponse().getContentAsString(), "$.recoveryCodes");
        entityManager.flush();
        List<String> storedHashes = jdbc.queryForList(
                "select code_hash from mfa_recovery_code where user_id = (select id from identity_user where email = ?)",
                String.class, email);
        assertThat(storedHashes).hasSize(8).allMatch(hash -> hash.length() == 64);
        assertThat(storedHashes).doesNotContainAnyElementsOf(firstCodes);

        mockMvc.perform(get("/api/v1/mfa").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(8));

        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .with(user(email)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(totp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Código MFA inválido"));

        MvcResult replacement = mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .with(user(email)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(firstCodes.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveryCodes.length()").value(8))
                .andReturn();
        List<String> replacementCodes = JsonPath.read(
                replacement.getResponse().getContentAsString(), "$.recoveryCodes");
        assertThat(replacementCodes).doesNotContainAnyElementsOf(firstCodes);

        mockMvc.perform(post("/api/v1/mfa/recovery-codes")
                        .with(user(email)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(firstCodes.get(0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Código MFA inválido"));

        mockMvc.perform(delete("/api/v1/mfa")
                        .with(user(email)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(replacementCodes.get(0))))
                .andExpect(status().isNoContent());
        entityManager.flush();
        mockMvc.perform(get("/api/v1/mfa").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.recoveryCodesRemaining").value(0));
        assertThat(jdbc.queryForObject(
                "select credential_version from identity_user where email = ?", Long.class, email))
                .isEqualTo(2L);

        String auditResponse = mockMvc.perform(get("/api/v1/mfa/audit-events")
                        .with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.items[*].eventType", org.hamcrest.Matchers.hasItems(
                        "MFA_ENROLLMENT_STARTED",
                        "MFA_ENABLED",
                        "MFA_RECOVERY_CODES_REGENERATED",
                        "MFA_DISABLED")))
                .andExpect(jsonPath("$.items[?(@.eventType == 'MFA_RECOVERY_CODES_REGENERATED' && @.result == 'FAILED')].reasonCode")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("INVALID_FACTOR"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(auditResponse)
                .doesNotContain(email, secret, totp)
                .doesNotContain(firstCodes.get(0), replacementCodes.get(0));

        String otherEmail = "mfa-other-" + UUID.randomUUID() + "@example.test";
        users.save(new IdentityUser(
                otherEmail,
                "Outra Identidade",
                passwordEncoder.encode("Another secure identity phrase 2026")));
        mockMvc.perform(get("/api/v1/mfa/audit-events").with(user(otherEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void requiresSecondFactorBeforeCreatingTheSsoSession() throws Exception {
        String email = "mfa-login-" + UUID.randomUUID() + "@example.test";
        String password = "Another secure MFA phrase 2026";
        users.save(new IdentityUser(email, "Login MFA", passwordEncoder.encode(password)));

        MvcResult enrollment = mockMvc.perform(post("/api/v1/mfa/enrollment")
                        .with(user(email)).with(csrf()))
                .andExpect(status().isOk()).andReturn();
        String secret = JsonPath.read(enrollment.getResponse().getContentAsString(), "$.secret");
        MvcResult confirmation = mockMvc.perform(post("/api/v1/mfa/enrollment/confirm")
                        .with(user(email)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(TotpAlgorithm.generate(secret, Instant.now()))))
                .andExpect(status().isOk()).andReturn();
        List<String> recoveryCodes = JsonPath.read(
                confirmation.getResponse().getContentAsString(), "$.recoveryCodes");

        String verifier = "mfa-verifier-with-more-than-forty-three-characters-123456789";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        MvcResult authorization = mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identity-hub-demo")
                        .queryParam("redirect_uri", "http://localhost:4200/demo/callback")
                        .queryParam("scope", "openid profile")
                        .queryParam("state", "mfa-state")
                        .queryParam("nonce", "mfa-nonce")
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        String interactionId = queryParameter(
                authorization.getResponse().getRedirectedUrl(), "interaction_id");

        mockMvc.perform(post("/api/v1/interactions/{id}/login", interactionId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.continueUrl").isEmpty());
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();

        mockMvc.perform(post("/api/v1/interactions/{id}/mfa", interactionId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"000000\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/interactions/{id}/mfa", interactionId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"%s\"}".formatted(recoveryCodes.get(0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(false))
                .andExpect(jsonPath("$.continueUrl").isNotEmpty());
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNotNull();

        mockMvc.perform(get("/api/v1/mfa/audit-events").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].eventType", org.hamcrest.Matchers.hasItems(
                        "MFA_CHALLENGE_FAILED", "MFA_CHALLENGE_SUCCEEDED")))
                .andExpect(jsonPath("$.items[?(@.eventType == 'MFA_CHALLENGE_FAILED')].reasonCode")
                        .value(org.hamcrest.Matchers.hasItem("INVALID_FACTOR")));
    }

    @Test
    void adaptivePolicyDeniesPasswordOnlyLoginAfterRecentFailures() throws Exception {
        String email = "adaptive-login-" + UUID.randomUUID() + "@example.test";
        String password = "Adaptive authentication phrase 2026";
        IdentityUser user = users.save(new IdentityUser(
                email, "Adaptive Login", passwordEncoder.encode(password)));
        entityManager.flush();
        jdbc.update(
                "update identity_user set failed_login_attempts = 3, last_failed_login_at = ? where id = ?",
                Instant.now(),
                user.getId());
        entityManager.clear();

        String verifier = "adaptive-verifier-with-more-than-forty-three-characters-12345";
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        MvcResult authorization = mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identity-hub-demo")
                        .queryParam("redirect_uri", "http://localhost:4200/demo/callback")
                        .queryParam("scope", "openid profile")
                        .queryParam("state", "adaptive-state")
                        .queryParam("nonce", "adaptive-nonce")
                        .queryParam("code_challenge", challenge)
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        String interactionId = queryParameter(
                authorization.getResponse().getRedirectedUrl(), "interaction_id");

        mockMvc.perform(post("/api/v1/interactions/{id}/login", interactionId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isForbidden());
        assertThat(session.getAttribute("SPRING_SECURITY_CONTEXT")).isNull();
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                "select adaptive_step_up_until from identity_user where id = ?",
                Instant.class,
                user.getId())).isAfter(Instant.now());
        mockMvc.perform(get("/api/v1/mfa/audit-events").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].eventType")
                        .value("ADAPTIVE_PASSWORD_AUTHENTICATION_DENIED"))
                .andExpect(jsonPath("$.items[0].result").value("DENIED"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("RECENT_FAILURES"));
    }

    @Test
    void rateLimitsMfaManagementAndAuditsTheRejection() throws Exception {
        String email = "mfa-rate-" + UUID.randomUUID() + "@example.test";
        users.save(new IdentityUser(
                email,
                "Rate Limited MFA",
                passwordEncoder.encode("A secure rate limited phrase 2026")));

        for (int attempt = 0; attempt < 8; attempt++) {
            mockMvc.perform(post("/api/v1/mfa/enrollment")
                            .with(user(email)).with(csrf()).with(remoteAddress("192.0.2.55")))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/mfa/enrollment")
                        .with(user(email)).with(csrf()).with(remoteAddress("192.0.2.55")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detail").value("Muitas tentativas. Aguarde antes de tentar novamente."));

        mockMvc.perform(get("/api/v1/mfa/audit-events").with(user(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(9))
                .andExpect(jsonPath("$.items[0].eventType").value("MFA_ENROLLMENT_STARTED"))
                .andExpect(jsonPath("$.items[0].result").value("FAILED"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("RATE_LIMITED"));
    }

    private static String queryParameter(String uri, String name) {
        String query = URI.create(uri).getRawQuery();
        for (String item : query.split("&")) {
            String[] pair = item.split("=", 2);
            if (name.equals(URLDecoder.decode(pair[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        throw new IllegalArgumentException("Parâmetro ausente: " + name);
    }

    private static RequestPostProcessor remoteAddress(String value) {
        return request -> {
            request.setRemoteAddr(value);
            return request;
        };
    }
}
