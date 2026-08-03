package com.karamba121.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
@EnableConfigurationProperties(SigningKeyProperties.class)
public class SigningKeyConfiguration {

    @Bean
    SigningKeyProvider signingKeyProvider(SigningKeyProperties properties, ResourceLoader resources) {
        return switch (properties.source()) {
            case GENERATED -> new GeneratedSigningKeyProvider();
            case PEM -> new PemSigningKeyProvider(properties, resources);
        };
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(SigningKeyProvider provider) {
        RSAKey signingKey = provider.load();
        JWKSet keys = new JWKSet(signingKey);
        return (selector, context) -> selector.select(keys);
    }
}
