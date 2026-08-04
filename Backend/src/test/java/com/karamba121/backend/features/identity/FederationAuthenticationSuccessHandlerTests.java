package com.karamba121.backend.features.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import com.karamba121.backend.config.IdentityHubProperties;
import com.karamba121.backend.features.interaction.AuthorizationInteraction;
import com.karamba121.backend.features.interaction.AuthorizationInteractionService;
import com.karamba121.backend.features.interaction.InteractionType;
import com.karamba121.backend.features.interaction.InteractionController;

class FederationAuthenticationSuccessHandlerTests {

    @Test
    void replacesExternalPrincipalWithLocalIdentityAndCompletesOpaqueInteraction() throws Exception {
        FederatedIdentityService identities = mock(FederatedIdentityService.class);
        IdentityUserDetailsService userDetails = mock(IdentityUserDetailsService.class);
        MfaService mfa = mock(MfaService.class);
        AuthorizationInteractionService interactions = mock(AuthorizationInteractionService.class);
        IdentityHubProperties properties = mock(IdentityHubProperties.class);
        HttpSessionSecurityContextRepository contexts = new HttpSessionSecurityContextRepository();
        FederationAuthenticationSuccessHandler handler = new FederationAuthenticationSuccessHandler(
                identities, userDetails, mfa, interactions, contexts, properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession().setAttribute(FederationController.LOGIN_INTERACTION, "opaque-interaction");
        OidcUser oidcUser = mock(OidcUser.class);
        OAuth2AuthenticationToken external = new OAuth2AuthenticationToken(
                oidcUser, List.of(new SimpleGrantedAuthority("OIDC_USER")), "corporate");
        IdentityUser local = new IdentityUser(
                "person@example.test", "Pessoa", "{bcrypt}unused");
        AuthorizationInteraction interaction = mock(AuthorizationInteraction.class);
        when(identities.authenticate("corporate", oidcUser, null)).thenReturn(local);
        when(userDetails.loadUserByUsername(local.getEmail())).thenReturn(User.withUsername(local.getEmail())
                .password("unused").authorities("ROLE_USER").build());
        when(mfa.isEnabled(local.getEmail())).thenReturn(false);
        when(interactions.resolvePending("opaque-interaction", request, InteractionType.LOGIN))
                .thenReturn(interaction);
        when(interaction.getResumeUri()).thenReturn("/oauth2/authorize?continue");

        handler.onAuthenticationSuccess(request, response, external);

        assertThat(response.getRedirectedUrl()).isEqualTo("/oauth2/authorize?continue");
        assertThat(org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication().getAuthorities())
                .extracting(authority -> authority.getAuthority())
                .contains("ROLE_USER", FederationAuthenticationSuccessHandler.FEDERATED_FACTOR_AUTHORITY)
                .doesNotContain("OIDC_USER");
        verify(interactions).completeLogin(eq(interaction), eq(request.getSession().getId()));
        verify(identities).recordSuccessfulAuthentication(local.getEmail());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void preservesLocalTotpChallengeAfterFederatedAuthentication() throws Exception {
        FederatedIdentityService identities = mock(FederatedIdentityService.class);
        IdentityUserDetailsService userDetails = mock(IdentityUserDetailsService.class);
        MfaService mfa = mock(MfaService.class);
        AuthorizationInteractionService interactions = mock(AuthorizationInteractionService.class);
        IdentityHubProperties properties = mock(IdentityHubProperties.class);
        when(properties.uiBaseUrl()).thenReturn("http://localhost:4200");
        FederationAuthenticationSuccessHandler handler = new FederationAuthenticationSuccessHandler(
                identities, userDetails, mfa, interactions,
                new HttpSessionSecurityContextRepository(), properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession().setAttribute(FederationController.LOGIN_INTERACTION, "opaque-mfa");
        OidcUser oidcUser = mock(OidcUser.class);
        OAuth2AuthenticationToken external = new OAuth2AuthenticationToken(
                oidcUser, List.of(new SimpleGrantedAuthority("OIDC_USER")), "corporate");
        IdentityUser local = new IdentityUser("mfa@example.test", "Pessoa MFA", "{bcrypt}unused");
        when(identities.authenticate("corporate", oidcUser, null)).thenReturn(local);
        when(userDetails.loadUserByUsername(local.getEmail())).thenReturn(User.withUsername(local.getEmail())
                .password("unused").authorities("ROLE_USER").build());
        when(mfa.isEnabled(local.getEmail())).thenReturn(true);

        handler.onAuthenticationSuccess(request, response, external);

        assertThat(response.getRedirectedUrl())
                .isEqualTo("http://localhost:4200/signin?federated_mfa=1&interaction_id=opaque-mfa");
        assertThat(request.getSession().getAttribute(InteractionController.PENDING_MFA_AUTHENTICATION))
                .isInstanceOf(org.springframework.security.core.Authentication.class);
        assertThat(request.getSession().getAttribute(InteractionController.PENDING_MFA_INTERACTION))
                .isEqualTo("opaque-mfa");
        verify(identities).recordSuccessfulAuthentication(local.getEmail());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsLockedLocalAccountWithoutRecordingSuccessfulFederatedLogin() throws Exception {
        FederatedIdentityService identities = mock(FederatedIdentityService.class);
        IdentityUserDetailsService userDetails = mock(IdentityUserDetailsService.class);
        MfaService mfa = mock(MfaService.class);
        AuthorizationInteractionService interactions = mock(AuthorizationInteractionService.class);
        IdentityHubProperties properties = mock(IdentityHubProperties.class);
        when(properties.uiBaseUrl()).thenReturn("http://localhost:4200");
        FederationAuthenticationSuccessHandler handler = new FederationAuthenticationSuccessHandler(
                identities, userDetails, mfa, interactions,
                new HttpSessionSecurityContextRepository(), properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.getSession().setAttribute(FederationController.LOGIN_INTERACTION, "locked-flow");
        OidcUser oidcUser = mock(OidcUser.class);
        OAuth2AuthenticationToken external = new OAuth2AuthenticationToken(
                oidcUser, List.of(new SimpleGrantedAuthority("OIDC_USER")), "corporate");
        IdentityUser local = new IdentityUser("locked@example.test", "Bloqueada", "{bcrypt}unused");
        when(identities.authenticate("corporate", oidcUser, null)).thenReturn(local);
        when(userDetails.loadUserByUsername(local.getEmail())).thenReturn(User.withUsername(local.getEmail())
                .password("unused").authorities("ROLE_USER").accountLocked(true).build());

        handler.onAuthenticationSuccess(request, response, external);

        assertThat(response.getRedirectedUrl()).isEqualTo(
                "http://localhost:4200/signin?federation_error=1&interaction_id=locked-flow");
        verify(identities, never()).recordSuccessfulAuthentication(local.getEmail());
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
