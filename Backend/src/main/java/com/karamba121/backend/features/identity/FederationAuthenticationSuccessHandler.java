package com.karamba121.backend.features.identity;

import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;

import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import com.karamba121.backend.config.IdentityHubProperties;
import com.karamba121.backend.features.interaction.AuthorizationInteraction;
import com.karamba121.backend.features.interaction.AuthorizationInteractionService;
import com.karamba121.backend.features.interaction.InteractionType;
import com.karamba121.backend.features.interaction.InteractionController;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class FederationAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    public static final String FEDERATED_FACTOR_AUTHORITY = "FACTOR_FEDERATED";

    private final AccountStatusUserDetailsChecker accountStatus = new AccountStatusUserDetailsChecker();
    private final FederatedIdentityService identities;
    private final IdentityUserDetailsService userDetailsService;
    private final MfaService mfa;
    private final AuthorizationInteractionService interactions;
    private final SecurityContextRepository securityContexts;
    private final IdentityHubProperties properties;

    public FederationAuthenticationSuccessHandler(
            FederatedIdentityService identities,
            IdentityUserDetailsService userDetailsService,
            MfaService mfa,
            AuthorizationInteractionService interactions,
            SecurityContextRepository securityContexts,
            IdentityHubProperties properties) {
        this.identities = identities;
        this.userDetailsService = userDetailsService;
        this.mfa = mfa;
        this.interactions = interactions;
        this.securityContexts = securityContexts;
        this.properties = properties;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        String linkingEmail = attribute(session, FederationController.LINKING_EMAIL);
        String interactionId = attribute(session, FederationController.LOGIN_INTERACTION);
        try {
            if (!(authentication instanceof OAuth2AuthenticationToken oauth)
                    || !(oauth.getPrincipal() instanceof OidcUser oidcUser)
                    || (linkingEmail == null && interactionId == null)) {
                throw new IllegalArgumentException("Contexto de federação inválido");
            }
            UserDetails localUser = null;
            if (linkingEmail != null) {
                localUser = userDetailsService.loadUserByUsername(linkingEmail);
                accountStatus.check(localUser);
            }
            IdentityUser user = identities.authenticate(
                    oauth.getAuthorizedClientRegistrationId(), oidcUser, linkingEmail);
            if (localUser == null) {
                localUser = userDetailsService.loadUserByUsername(user.getEmail());
                accountStatus.check(localUser);
            }
            identities.recordSuccessfulAuthentication(user.getEmail());
            HashSet<GrantedAuthority> authorities = new HashSet<>(localUser.getAuthorities());
            authorities.add(new SimpleGrantedAuthority(FEDERATED_FACTOR_AUTHORITY));
            Authentication localAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                    localUser, null, authorities);
            if (linkingEmail == null && mfa.isEnabled(user.getEmail())) {
                SecurityContext empty = SecurityContextHolder.createEmptyContext();
                SecurityContextHolder.setContext(empty);
                securityContexts.saveContext(empty, request, response);
                session.setAttribute(InteractionController.PENDING_MFA_AUTHENTICATION, localAuthentication);
                session.setAttribute(InteractionController.PENDING_MFA_INTERACTION, interactionId);
                session.setAttribute(InteractionController.PENDING_MFA_CREATED_AT, Instant.now());
                clear(session);
                response.sendRedirect(properties.uiBaseUrl()
                        + "/signin?federated_mfa=1&interaction_id=" + url(interactionId));
                return;
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(localAuthentication);
            SecurityContextHolder.setContext(context);
            securityContexts.saveContext(context, request, response);
            clear(session);

            if (linkingEmail != null) {
                response.sendRedirect(properties.uiBaseUrl() + "/profile?federation=linked");
                return;
            }
            AuthorizationInteraction interaction = interactions.resolvePending(
                    interactionId, request, InteractionType.LOGIN);
            interactions.completeLogin(interaction, request.getSession().getId());
            response.sendRedirect(interaction.getResumeUri());
        } catch (RuntimeException exception) {
            SecurityContext fallback = SecurityContextHolder.createEmptyContext();
            if (linkingEmail != null) {
                try {
                    UserDetails localUser = userDetailsService.loadUserByUsername(linkingEmail);
                    accountStatus.check(localUser);
                    fallback.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            localUser, null, localUser.getAuthorities()));
                } catch (RuntimeException ignored) {
                    // Falha fechada: não restaura uma conta que deixou de estar ativa.
                }
            }
            SecurityContextHolder.setContext(fallback);
            securityContexts.saveContext(fallback, request, response);
            clear(session);
            String destination = linkingEmail != null
                    ? properties.uiBaseUrl() + "/profile?federation=error"
                    : properties.uiBaseUrl() + "/signin?federation_error=1"
                            + (interactionId == null ? "" : "&interaction_id=" + url(interactionId));
            response.sendRedirect(destination);
        }
    }

    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        HttpSession session = request.getSession(false);
        boolean linking = attribute(session, FederationController.LINKING_EMAIL) != null;
        String interactionId = attribute(session, FederationController.LOGIN_INTERACTION);
        clear(session);
        String destination = linking
                ? properties.uiBaseUrl() + "/profile?federation=error"
                : properties.uiBaseUrl() + "/signin?federation_error=1"
                        + (interactionId == null ? "" : "&interaction_id=" + url(interactionId));
        response.sendRedirect(destination);
    }

    private static String attribute(HttpSession session, String name) {
        Object value = session == null ? null : session.getAttribute(name);
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static void clear(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(FederationController.LINKING_EMAIL);
        session.removeAttribute(FederationController.LOGIN_INTERACTION);
    }

    private static String url(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
