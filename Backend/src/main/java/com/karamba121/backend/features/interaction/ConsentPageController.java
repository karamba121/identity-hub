package com.karamba121.backend.features.interaction;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.karamba121.backend.config.IdentityHubProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
public class ConsentPageController {

    private final AuthorizationInteractionService interactions;
    private final IdentityHubProperties properties;

    public ConsentPageController(
            AuthorizationInteractionService interactions,
            IdentityHubProperties properties) {
        this.interactions = interactions;
        this.properties = properties;
    }

    @GetMapping("/oauth2/consent")
    public void consent(
            @RequestParam("client_id") String clientId,
            @RequestParam("scope") String scopes,
            @RequestParam("state") String state,
            Authentication authentication,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String interactionId = interactions.createConsent(
                request, authentication.getName(), clientId, scopes, state);
        response.sendRedirect(uiUrl("/consent?interaction_id="
                + URLEncoder.encode(interactionId, StandardCharsets.UTF_8)));
    }

    private String uiUrl(String path) {
        String baseUrl = properties.uiBaseUrl();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;
    }
}
