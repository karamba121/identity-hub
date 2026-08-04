package com.karamba121.backend.features.identity;

import java.security.Principal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.karamba121.backend.config.FederationProperties;
import com.karamba121.backend.features.interaction.AuthorizationInteractionService;
import com.karamba121.backend.features.interaction.InteractionType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1")
public class FederationController {

    static final String LOGIN_INTERACTION = "identity-hub.federation.login-interaction";
    static final String LINKING_EMAIL = "identity-hub.federation.linking-email";

    private final FederationProperties properties;
    private final AuthorizationInteractionService interactions;
    private final FederatedIdentityService identities;

    public FederationController(
            FederationProperties properties,
            AuthorizationInteractionService interactions,
            FederatedIdentityService identities) {
        this.properties = properties;
        this.interactions = interactions;
        this.identities = identities;
    }

    @GetMapping("/federation/providers")
    List<FederationProviderView> providers() {
        if (!properties.enabled()) return List.of();
        return List.of(new FederationProviderView(properties.registrationId(), properties.displayName()));
    }

    @GetMapping("/interactions/{interactionId}/federation/{registrationId}")
    void startLogin(
            @PathVariable String interactionId,
            @PathVariable String registrationId,
            HttpServletRequest request,
            HttpServletResponse response) throws java.io.IOException {
        requireProvider(registrationId);
        interactions.resolvePending(interactionId, request, InteractionType.LOGIN);
        request.getSession(true).setAttribute(LOGIN_INTERACTION, interactionId);
        request.getSession().removeAttribute(LINKING_EMAIL);
        response.sendRedirect("/oauth2/authorization/" + properties.registrationId());
    }

    @GetMapping("/federation/{registrationId}/link")
    void startLink(
            @PathVariable String registrationId,
            Principal principal,
            HttpServletRequest request,
            HttpServletResponse response) throws java.io.IOException {
        requireProvider(registrationId);
        request.getSession(true).setAttribute(LINKING_EMAIL, principal.getName());
        request.getSession().removeAttribute(LOGIN_INTERACTION);
        response.sendRedirect("/oauth2/authorization/" + properties.registrationId());
    }

    @GetMapping("/federation/links")
    List<FederatedIdentityService.FederatedIdentityView> links(Principal principal) {
        return identities.list(principal.getName());
    }

    @GetMapping("/federation/csrf")
    ResponseEntity<Void> csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/federation/links/{id}")
    ResponseEntity<Void> unlink(@PathVariable String id, Principal principal) {
        identities.unlink(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    private void requireProvider(String registrationId) {
        if (!properties.enabled() || !properties.registrationId().equals(registrationId)) {
            throw new IllegalArgumentException("Provedor federado indisponível");
        }
    }

    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    ResponseEntity<ProblemDetail> invalid(RuntimeException exception) {
        return ResponseEntity.badRequest().body(
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage()));
    }

    public record FederationProviderView(String id, String displayName) { }
}
