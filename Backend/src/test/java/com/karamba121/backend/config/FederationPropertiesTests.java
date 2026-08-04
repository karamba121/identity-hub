package com.karamba121.backend.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;

class FederationPropertiesTests {

    @Test
    void failsClosedForInsecureEndpointsOrMissingOidcScopes() {
        assertThatThrownBy(() -> properties("http://issuer.example.test", Set.of("openid", "email")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> properties("https://issuer.example.test", Set.of("profile", "email")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("openid e email");
    }

    private static FederationProperties properties(String issuer, Set<String> scopes) {
        return new FederationProperties(
                true,
                "corporate",
                "Provedor corporativo",
                "client-id",
                "client-secret",
                issuer,
                "https://issuer.example.test/authorize",
                "https://issuer.example.test/token",
                "https://issuer.example.test/jwks",
                null,
                scopes);
    }
}
