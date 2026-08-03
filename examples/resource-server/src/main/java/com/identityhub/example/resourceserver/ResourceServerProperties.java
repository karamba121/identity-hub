package com.identityhub.example.resourceserver;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("identity-hub.resource-server")
public record ResourceServerProperties(
        String issuer,
        String jwkSetUri,
        String audience,
        String requiredScope) {

    public ResourceServerProperties {
        issuer = required(issuer, "issuer");
        jwkSetUri = required(jwkSetUri, "jwk-set-uri");
        audience = required(audience, "audience");
        requiredScope = required(requiredScope, "required-scope");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("identity-hub.resource-server." + field + " é obrigatório");
        }
        return value.trim();
    }
}
