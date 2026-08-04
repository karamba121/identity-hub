package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.karamba121.backend.features.access.SecurityAuditEventType;
import com.karamba121.backend.features.identity.FederatedIdentityRepository;
import com.karamba121.backend.features.identity.FederatedIdentityService;
import com.karamba121.backend.features.identity.IdentitySecurityAuditor;
import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FederationIntegrationTests {

    @Autowired FederatedIdentityService federation;
    @Autowired FederatedIdentityRepository links;
    @Autowired IdentityUserRepository users;
    @Autowired IdentitySecurityAuditor auditor;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired MockMvc mockMvc;

    @Test
    void provisionsOnlyVerifiedNewIdentityAndReusesStableProviderSubject() {
        String email = unique("federated");
        String subject = "subject-" + UUID.randomUUID();
        IdentityUser first = federation.authenticate("corporate", oidc(subject, email, true), null);
        IdentityUser second = federation.authenticate("corporate", oidc(subject, email, true), null);

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(first.hasLocalCredentials()).isFalse();
        assertThat(first.isEmailVerified()).isTrue();
        assertThat(links.countByUserId(first.getId())).isEqualTo(1);
        assertThat(auditor.history(email, 0, 20).getContent())
                .extracting(event -> event.getEventType())
                .contains(SecurityAuditEventType.FEDERATED_IDENTITY_LINKED);
    }

    @Test
    void refusesUnverifiedEmailAndSilentLinkToExistingLocalAccount() {
        String existingEmail = unique("existing");
        users.save(new IdentityUser(existingEmail, "Conta local", passwordEncoder.encode("Local password 2026")));

        assertThatThrownBy(() -> federation.authenticate(
                "corporate", oidc("unverified-" + UUID.randomUUID(), unique("unverified"), false), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("e-mail verificado");
        assertThatThrownBy(() -> federation.authenticate(
                "corporate", oidc("collision-" + UUID.randomUUID(), existingEmail, true), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Vínculo explícito");
        assertThat(links.findAllByUserEmailIgnoreCaseOrderByCreatedAtDesc(existingEmail)).isEmpty();
    }

    @Test
    void explicitlyLinksAndLetsOnlyTheOwnerRemoveTheProvider() throws Exception {
        String ownerEmail = unique("owner");
        IdentityUser owner = users.save(new IdentityUser(
                ownerEmail, "Conta vinculada", passwordEncoder.encode("Local password 2026")));
        federation.authenticate(
                "corporate", oidc("linked-" + UUID.randomUUID(), unique("provider-mail"), true), ownerEmail);
        String linkId = links.findAllByUserEmailIgnoreCaseOrderByCreatedAtDesc(ownerEmail).get(0).getId();

        mockMvc.perform(get("/api/v1/federation/links").with(user(ownerEmail)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(linkId))
                .andExpect(jsonPath("$[0].provider").value("corporate"));

        mockMvc.perform(delete("/api/v1/federation/links/{id}", linkId)
                        .with(user(unique("attacker"))).with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(links.findById(linkId)).isPresent();

        mockMvc.perform(delete("/api/v1/federation/links/{id}", linkId)
                        .with(user(ownerEmail)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(links.findById(linkId)).isEmpty();
        assertThat(owner.hasLocalCredentials()).isTrue();
    }

    @Test
    void protectsTheLastSignInMethodOfFederatedOnlyAccount() {
        String email = unique("only-external");
        federation.authenticate("corporate", oidc("only-" + UUID.randomUUID(), email, true), null);
        String linkId = links.findAllByUserEmailIgnoreCaseOrderByCreatedAtDesc(email).get(0).getId();

        assertThatThrownBy(() -> federation.unlink(email, linkId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cadastre uma senha");
        assertThat(links.findById(linkId)).isPresent();
    }

    @Test
    void hidesProvidersWhenFederationIsDisabled() throws Exception {
        mockMvc.perform(get("/api/v1/federation/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    private static OidcUser oidc(String subject, String email, boolean verified) {
        OidcUser user = mock(OidcUser.class);
        when(user.getSubject()).thenReturn(subject);
        when(user.getEmail()).thenReturn(email);
        when(user.getFullName()).thenReturn("Pessoa federada");
        when(user.getClaim("email_verified")).thenReturn(verified);
        return user;
    }

    private static String unique(String prefix) {
        return prefix + "+" + UUID.randomUUID() + "@example.test";
    }
}
