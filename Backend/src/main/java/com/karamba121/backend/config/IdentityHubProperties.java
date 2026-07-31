package com.karamba121.backend.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity-hub")
public record IdentityHubProperties(
        String issuer,
        String uiBaseUrl,
        Duration interactionTtl,
        Bootstrap bootstrap) {

    public record Bootstrap(
            boolean enabled,
            String userEmail,
            String userPassword,
            String userName,
            String clientId,
            String clientName,
            String redirectUri) {
    }
}
