package com.identityhub.example.resourceserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTests {

    private final AudienceValidator validator = new AudienceValidator("identity-hub-api");

    @Test
    void acceptsExpectedAudience() {
        assertThat(validator.validate(jwt(List.of("identity-hub-api"))).hasErrors()).isFalse();
    }

    @Test
    void rejectsAnotherAudience() {
        var result = validator.validate(jwt(List.of("another-api")));

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).extracting(error -> error.getErrorCode())
                .containsExactly("invalid_token");
    }

    private static Jwt jwt(List<String> audience) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:4200")
                .subject("client:example")
                .audience(audience)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("scope", "demo.read")
                .build();
    }
}
