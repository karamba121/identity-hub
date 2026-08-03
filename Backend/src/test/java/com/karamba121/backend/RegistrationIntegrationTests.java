package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.identity.EmailVerificationSender;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserDetailsService;
import com.karamba121.backend.features.identity.IdentityUserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IdentityUserRepository users;

    @Autowired
    private IdentityUserDetailsService userDetailsService;

    @Autowired
    private JdbcOperations jdbc;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private EmailVerificationSender sender;

    @Test
    void registersPendingAccountAndActivatesItWithOneTimeHashedToken() throws Exception {
        String email = "new-user-" + UUID.randomUUID() + "@example.test";

        mockMvc.perform(post("/api/v1/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(email)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "Se o cadastro puder ser criado, enviaremos um link de verificação para o e-mail informado."));

        IdentityUser pending = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(pending.isEnabled()).isTrue();
        assertThat(pending.isEmailVerified()).isFalse();
        assertThat(pending.getPasswordHash())
                .startsWith("{argon2id}$argon2id$")
                .doesNotContain("Registration123!");
        UserDetails beforeVerification = userDetailsService.loadUserByUsername(email);
        assertThat(beforeVerification.isEnabled()).isFalse();

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(sender).send(org.mockito.ArgumentMatchers.eq(email),
                org.mockito.ArgumentMatchers.eq("Nova Pessoa"), link.capture());
        String rawToken = tokenFrom(link.getValue());
        assertThat(rawToken).hasSizeGreaterThanOrEqualTo(40);
        assertThat(jdbc.queryForList(
                "select token_hash from email_verification_token where user_id = ?",
                String.class,
                pending.getId()))
                .hasSize(1)
                .allSatisfy(hash -> assertThat(hash).hasSize(64).isNotEqualTo(rawToken));

        mockMvc.perform(post("/api/v1/registrations/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(rawToken)))
                .andExpect(status().isNoContent());

        IdentityUser verified = users.findByEmailIgnoreCase(email).orElseThrow();
        assertThat(verified.isEmailVerified()).isTrue();
        assertThat(userDetailsService.loadUserByUsername(email).isEnabled()).isTrue();
        assertThat(jdbc.queryForObject(
                "select consumed_at from email_verification_token where user_id = ?",
                Instant.class,
                pending.getId()))
                .isNotNull();

        mockMvc.perform(post("/api/v1/registrations/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(rawToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Link de verificação inválido ou expirado"));
    }

    @Test
    void returnsTheSamePublicResponseForAnExistingEmailWithoutDuplicatingIt() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        String payload = registrationPayload(email);

        String createdResponse = mockMvc.perform(post("/api/v1/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String existingResponse = mockMvc.perform(post("/api/v1/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "Se o cadastro puder ser criado, enviaremos um link de verificação para o e-mail informado."))
                .andReturn().getResponse().getContentAsString();

        assertThat(existingResponse).isEqualTo(createdResponse);

        assertThat(users.findAll().stream().filter(user -> email.equals(user.getEmail()))).hasSize(1);
        verify(sender, times(1)).send(
                org.mockito.ArgumentMatchers.eq(email),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsExpiredVerificationTokenAndKeepsAccountPending() throws Exception {
        String email = "expired-" + UUID.randomUUID() + "@example.test";
        mockMvc.perform(post("/api/v1/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload(email)))
                .andExpect(status().isAccepted());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(sender).send(org.mockito.ArgumentMatchers.eq(email),
                org.mockito.ArgumentMatchers.anyString(), link.capture());
        String rawToken = tokenFrom(link.getValue());
        IdentityUser pending = users.findByEmailIgnoreCase(email).orElseThrow();
        jdbc.update(
                "update email_verification_token set expires_at = ? where user_id = ?",
                Instant.now().minusSeconds(1),
                pending.getId());
        entityManager.clear();

        mockMvc.perform(post("/api/v1/registrations/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(rawToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Link de verificação inválido ou expirado"));

        assertThat(users.findByEmailIgnoreCase(email).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void requiresCsrfAndValidRegistrationFields() throws Exception {
        mockMvc.perform(post("/api/v1/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("csrf@example.test")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/registrations")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalid","displayName":"A","password":"short"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exposesTheCsrfCookieRequiredByTheAngularRegistrationFlow() throws Exception {
        MvcResult csrf = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/registrations/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn();
        jakarta.servlet.http.Cookie cookie = csrf.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        String token = com.jayway.jsonpath.JsonPath.read(
                csrf.getResponse().getContentAsString(), "$.token");

        mockMvc.perform(post("/api/v1/registrations")
                        .cookie(cookie)
                        .header("X-XSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationPayload("browser-" + UUID.randomUUID() + "@example.test")))
                .andExpect(status().isAccepted());
    }

    private static String registrationPayload(String email) {
        return """
                {
                  "email":"%s",
                  "displayName":"Nova Pessoa",
                  "password":"Registration123!"
                }
                """.formatted(email);
    }

    private static String tokenFrom(String verificationUrl) {
        String fragment = URI.create(verificationUrl).getFragment();
        assertThat(fragment).startsWith("token=");
        return fragment.substring("token=".length());
    }
}
