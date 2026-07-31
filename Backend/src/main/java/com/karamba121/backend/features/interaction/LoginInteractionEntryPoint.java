package com.karamba121.backend.features.interaction;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.karamba121.backend.config.IdentityHubProperties;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class LoginInteractionEntryPoint implements AuthenticationEntryPoint {

    private final AuthorizationInteractionService interactions;
    private final IdentityHubProperties properties;

    public LoginInteractionEntryPoint(
            AuthorizationInteractionService interactions,
            IdentityHubProperties properties) {
        this.interactions = interactions;
        this.properties = properties;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {
        try {
            String interactionId = interactions.createLogin(request);
            response.sendRedirect(uiUrl("/signin?interaction_id="
                    + URLEncoder.encode(interactionId, StandardCharsets.UTF_8)));
        } catch (InteractionException exception) {
            response.sendError(exception.getStatusCode().value(), exception.getReason());
        }
    }

    private String uiUrl(String path) {
        String baseUrl = properties.uiBaseUrl();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) + path : baseUrl + path;
    }
}
