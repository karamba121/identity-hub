package com.karamba121.backend.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(SigningKeyProperties.class)
public class SigningKeyConfiguration {

    @Bean
    SigningKeyProvider signingKeyProvider(SigningKeyProperties properties, ResourceLoader resources) {
        return switch (properties.source()) {
            case GENERATED -> new GeneratedSigningKeyProvider();
            case PEM -> new PemSigningKeyProvider(properties, resources);
            case ROTATING_PEM -> new RotatingPemSigningKeyProvider(properties, resources);
        };
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeyProvider provider) {
        return jwkSource(provider, Clock.systemUTC());
    }

    JWKSource<SecurityContext> jwkSource(SigningKeyProvider provider, Clock clock) {
        SigningKeySet keys = provider.load();
        return (selector, context) -> selector.select(keys.jwkSetAt(clock.instant()));
    }

    @Bean
    JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        encoder.setJwkSelector(keys -> keys.stream()
                .filter(key -> key.isPrivate())
                .findFirst()
                .orElse(null));
        return encoder;
    }
}
