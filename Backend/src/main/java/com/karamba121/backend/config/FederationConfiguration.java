package com.karamba121.backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

@Configuration
@EnableConfigurationProperties(FederationProperties.class)
public class FederationConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "identity-hub.federation", name = "enabled", havingValue = "true")
    ClientRegistrationRepository federationClientRegistrationRepository(FederationProperties properties) {
        ClientRegistration.Builder registration = ClientRegistration
                .withRegistrationId(properties.registrationId())
                .clientName(properties.displayName())
                .clientId(properties.clientId())
                .clientSecret(properties.clientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(properties.scopes())
                .authorizationUri(properties.authorizationUri())
                .tokenUri(properties.tokenUri())
                .jwkSetUri(properties.jwkSetUri())
                .issuerUri(properties.issuerUri())
                .userNameAttributeName("sub");
        if (properties.userInfoUri() != null) {
            registration.userInfoUri(properties.userInfoUri());
        }
        return new InMemoryClientRegistrationRepository(registration.build());
    }
}
