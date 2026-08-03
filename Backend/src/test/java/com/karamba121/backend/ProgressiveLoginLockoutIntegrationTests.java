package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProgressiveLoginLockoutIntegrationTests {

    private static final String PASSWORD = "A valid private login phrase 2026";

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcOperations jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void locksAfterThresholdEscalatesAfterExpiryAndResetsOnSuccess() {
        IdentityUser user = verifiedUser();

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertBadCredentials(user.getEmail());
            IdentityUser current = users.findById(user.getId()).orElseThrow();
            assertThat(current.getFailedLoginAttempts()).isEqualTo(attempt);
            assertThat(current.getLockedUntil()).isNull();
        }

        Instant fifthAttempt = Instant.now();
        assertBadCredentials(user.getEmail());
        IdentityUser locked = users.findById(user.getId()).orElseThrow();
        assertThat(locked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(locked.getLockedUntil()).isBetween(
                fifthAttempt.plusSeconds(55), fifthAttempt.plusSeconds(70));
        Instant firstLock = locked.getLockedUntil();

        assertThatThrownBy(() -> authenticate(user.getEmail(), PASSWORD))
                .isInstanceOf(LockedException.class);
        IdentityUser stillLocked = users.findById(user.getId()).orElseThrow();
        assertThat(stillLocked.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(stillLocked.getLockedUntil()).isEqualTo(firstLock);

        expireLock(user.getId());
        Instant sixthAttempt = Instant.now();
        assertBadCredentials(user.getEmail());
        IdentityUser escalated = users.findById(user.getId()).orElseThrow();
        assertThat(escalated.getFailedLoginAttempts()).isEqualTo(6);
        assertThat(escalated.getLockedUntil()).isBetween(
                sixthAttempt.plusSeconds(115), sixthAttempt.plusSeconds(130));

        jdbc.update(
                "update identity_user set failed_login_attempts = 8, locked_until = ? where id = ?",
                Instant.now().minusSeconds(1),
                user.getId());
        Instant cappedAttempt = Instant.now();
        assertBadCredentials(user.getEmail());
        IdentityUser capped = users.findById(user.getId()).orElseThrow();
        assertThat(capped.getFailedLoginAttempts()).isEqualTo(9);
        assertThat(capped.getLockedUntil()).isBetween(
                cappedAttempt.plusSeconds(895), cappedAttempt.plusSeconds(910));

        expireLock(user.getId());
        assertThat(authenticate(user.getEmail(), PASSWORD).isAuthenticated()).isTrue();
        IdentityUser reset = users.findById(user.getId()).orElseThrow();
        assertThat(reset.getFailedLoginAttempts()).isZero();
        assertThat(reset.getLockedUntil()).isNull();
        assertThat(reset.getLastFailedLoginAt()).isNull();
    }

    @Test
    void doesNotPersistStateForUnknownOrIneligibleAccounts() {
        long countBefore = users.count();
        for (int attempt = 0; attempt < 5; attempt++) {
            assertBadCredentials("missing-" + UUID.randomUUID() + "@example.test");
        }
        assertThat(users.count()).isEqualTo(countBefore);

        IdentityUser pending = users.save(IdentityUser.pendingEmailVerification(
                "pending-" + UUID.randomUUID() + "@example.test",
                "Pessoa Pendente",
                passwordEncoder.encode(PASSWORD)));
        assertThatThrownBy(() -> authenticate(pending.getEmail(), PASSWORD))
                .isInstanceOf(org.springframework.security.authentication.DisabledException.class);
        IdentityUser unchanged = users.findById(pending.getId()).orElseThrow();
        assertThat(unchanged.getFailedLoginAttempts()).isZero();
        assertThat(unchanged.getLockedUntil()).isNull();
    }

    @Test
    void keepsThePublicLoginResponseGenericWhenTheAccountBecomesLocked() throws Exception {
        IdentityUser user = verifiedUser();
        MvcResult authorization = mockMvc.perform(get("/oauth2/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", "identity-hub-demo")
                        .queryParam("redirect_uri", "http://localhost:4200/demo/callback")
                        .queryParam("scope", "openid")
                        .queryParam("state", "lockout-state")
                        .queryParam("nonce", "lockout-nonce")
                        .queryParam("code_challenge", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                        .queryParam("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection())
                .andReturn();
        MockHttpSession session = (MockHttpSession) authorization.getRequest().getSession(false);
        String interactionId = UriComponentsBuilder
                .fromUriString(authorization.getResponse().getRedirectedUrl())
                .build()
                .getQueryParams()
                .getFirst("interaction_id");

        for (int attempt = 0; attempt < 5; attempt++) {
            login(interactionId, session, user.getEmail(), "Wrong private login phrase 2026")
                    .andExpect(status().isUnauthorized())
                    .andExpect(status().reason("Credenciais inválidas"));
        }
        login(interactionId, session, user.getEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(status().reason("Credenciais inválidas"));
    }

    private IdentityUser verifiedUser() {
        return users.save(new IdentityUser(
                "lockout-" + UUID.randomUUID() + "@example.test",
                "Pessoa Protegida",
                passwordEncoder.encode(PASSWORD)));
    }

    private void assertBadCredentials(String email) {
        assertThatThrownBy(() -> authenticate(email, "Wrong private login phrase 2026"))
                .isInstanceOf(BadCredentialsException.class);
    }

    private org.springframework.security.core.Authentication authenticate(String email, String password) {
        return authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
    }

    private void expireLock(String userId) {
        jdbc.update("update identity_user set locked_until = ? where id = ?", Instant.now().minusSeconds(1), userId);
    }

    private org.springframework.test.web.servlet.ResultActions login(
            String interactionId,
            MockHttpSession session,
            String email,
            String password) throws Exception {
        return mockMvc.perform(post("/api/v1/interactions/{id}/login", interactionId)
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }
}
