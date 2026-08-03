package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.identity.PasswordRecoverySender;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PasswordRecoveryIntegrationTests {

    private static final String ORIGINAL_PASSWORD = "Original password phrase 2026";
    private static final String NEW_PASSWORD = "A new private phrase for 2026";
    private static final String ACCEPTED_MESSAGE =
            "Se houver uma conta elegível, enviaremos um link de recuperação para o e-mail informado.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcOperations jdbc;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PasswordRecoverySender sender;

    @Test
    void returnsTheSameResponseForKnownAndUnknownAccounts() throws Exception {
        IdentityUser user = verifiedUser();

        String knownResponse = requestRecovery(user.getEmail())
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ACCEPTED_MESSAGE))
                .andReturn().getResponse().getContentAsString();
        String unknownResponse = requestRecovery("missing-" + UUID.randomUUID() + "@example.test")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(ACCEPTED_MESSAGE))
                .andReturn().getResponse().getContentAsString();

        assertThat(knownResponse).isEqualTo(unknownResponse);

        verify(sender, times(1)).send(eq(user.getEmail()), eq(user.getDisplayName()), anyString());
    }

    @Test
    void changesPasswordWithAHashedSingleUseToken() throws Exception {
        IdentityUser user = verifiedUser();
        requestRecovery(user.getEmail()).andExpect(status().isAccepted());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq(user.getEmail()), eq(user.getDisplayName()), link.capture());
        String rawToken = tokenFrom(link.getValue());
        assertThat(rawToken).hasSize(43);
        String storedHash = jdbc.queryForObject(
                "select token_hash from password_recovery_token where user_id = ?",
                String.class,
                user.getId());
        assertThat(storedHash).hasSize(64).isNotEqualTo(rawToken);
        assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, user.getPasswordHash())).isTrue();

        jdbc.update(
                "update identity_user set failed_login_attempts = 5, locked_until = ? where id = ?",
                Instant.now().plusSeconds(60),
                user.getId());
        entityManager.clear();

        completeRecovery(rawToken, NEW_PASSWORD).andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        IdentityUser updated = users.findById(user.getId()).orElseThrow();
        assertThat(updated.getPasswordHash()).startsWith("{argon2id}$argon2id$");
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updated.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, updated.getPasswordHash())).isFalse();
        assertThat(updated.getFailedLoginAttempts()).isZero();
        assertThat(updated.getLockedUntil()).isNull();
        assertThat(updated.getCredentialVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select consumed_at from password_recovery_token where user_id = ?",
                Instant.class,
                user.getId())).isNotNull();

        completeRecovery(rawToken, "Another long private phrase 2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Link de recuperação inválido ou expirado"));
    }

    @Test
    void expiresTokensAndRevokesThePreviousRequest() throws Exception {
        IdentityUser user = verifiedUser();
        requestRecovery(user.getEmail()).andExpect(status().isAccepted());
        requestRecovery(user.getEmail()).andExpect(status().isAccepted());

        ArgumentCaptor<String> links = ArgumentCaptor.forClass(String.class);
        verify(sender, times(2)).send(eq(user.getEmail()), eq(user.getDisplayName()), links.capture());
        String firstToken = tokenFrom(links.getAllValues().get(0));
        String currentToken = tokenFrom(links.getAllValues().get(1));

        completeRecovery(firstToken, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Link de recuperação inválido ou expirado"));

        jdbc.update(
                "update password_recovery_token set expires_at = ? where token_hash = (select token_hash from password_recovery_token where user_id = ? and revoked_at is null)",
                Instant.now().minusSeconds(1),
                user.getId());
        entityManager.clear();
        completeRecovery(currentToken, NEW_PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Link de recuperação inválido ou expirado"));
    }

    @Test
    void keepsTokenAvailableWhenTheNewPasswordViolatesPolicy() throws Exception {
        IdentityUser user = verifiedUser();
        requestRecovery(user.getEmail()).andExpect(status().isAccepted());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq(user.getEmail()), eq(user.getDisplayName()), link.capture());
        String rawToken = tokenFrom(link.getValue());

        completeRecovery(rawToken, "short")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Senha deve conter entre 15 e 128 caracteres"));
        completeRecovery(rawToken, NEW_PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void requiresCsrfAndReusesThePublicCsrfEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/registrations/csrf"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/password-recovery")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"missing@example.test\"}"))
                .andExpect(status().isAccepted());
        verify(sender, never()).send(eq("missing@example.test"), anyString(), anyString());

        mockMvc.perform(post("/api/v1/password-recovery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"someone@example.test\"}"))
                .andExpect(status().isForbidden());
    }

    private IdentityUser verifiedUser() {
        String email = "recovery-" + UUID.randomUUID() + "@example.test";
        return users.save(new IdentityUser(
                email,
                "Pessoa Recuperação",
                passwordEncoder.encode(ORIGINAL_PASSWORD)));
    }

    private org.springframework.test.web.servlet.ResultActions requestRecovery(String email) throws Exception {
        return mockMvc.perform(post("/api/v1/password-recovery")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    private org.springframework.test.web.servlet.ResultActions completeRecovery(String token, String password)
            throws Exception {
        return mockMvc.perform(post("/api/v1/password-recovery/complete")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"token":"%s","newPassword":"%s"}
                        """.formatted(token, password)));
    }

    private static String tokenFrom(String recoveryUrl) {
        String fragment = URI.create(recoveryUrl).getFragment();
        assertThat(fragment).startsWith("token=");
        return fragment.substring("token=".length());
    }
}
