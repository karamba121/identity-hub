package com.karamba121.backend.config;

import java.net.URI;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity-hub.federation")
public record FederationProperties(
        boolean enabled,
        String registrationId,
        String displayName,
        String clientId,
        String clientSecret,
        String issuerUri,
        String authorizationUri,
        String tokenUri,
        String jwkSetUri,
        String userInfoUri,
        Set<String> scopes) {

    public FederationProperties {
        registrationId = normalize(registrationId);
        displayName = normalize(displayName);
        clientId = normalize(clientId);
        clientSecret = normalize(clientSecret);
        issuerUri = normalize(issuerUri);
        authorizationUri = normalize(authorizationUri);
        tokenUri = normalize(tokenUri);
        jwkSetUri = normalize(jwkSetUri);
        userInfoUri = normalize(userInfoUri);
        scopes = scopes == null || scopes.isEmpty() ? Set.of("openid", "profile", "email") : Set.copyOf(scopes);
        if (enabled) {
            require(registrationId, "registration-id");
            require(displayName, "display-name");
            require(clientId, "client-id");
            require(clientSecret, "client-secret");
            requireHttps(issuerUri, "issuer-uri");
            requireHttps(authorizationUri, "authorization-uri");
            requireHttps(tokenUri, "token-uri");
            requireHttps(jwkSetUri, "jwk-set-uri");
            if (userInfoUri != null) requireHttps(userInfoUri, "user-info-uri");
            if (!registrationId.matches("[a-z0-9][a-z0-9-]{0,62}")) {
                throw new IllegalArgumentException("registration-id de federação inválido");
            }
            if (!scopes.contains("openid") || !scopes.contains("email")) {
                throw new IllegalArgumentException("Federação exige os escopos openid e email");
            }
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void require(String value, String field) {
        if (value == null) throw new IllegalArgumentException("identity-hub.federation." + field + " é obrigatório");
    }

    private static void requireHttps(String value, String field) {
        require(value, field);
        URI uri;
        try {
            uri = URI.create(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("identity-hub.federation." + field + " é inválido", exception);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getFragment() != null) {
            throw new IllegalArgumentException("identity-hub.federation." + field + " deve usar HTTPS");
        }
    }
}
