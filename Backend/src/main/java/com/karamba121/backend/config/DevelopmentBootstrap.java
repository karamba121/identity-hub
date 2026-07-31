package com.karamba121.backend.config;

import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;

@Component
public class DevelopmentBootstrap implements ApplicationRunner {

    private final IdentityHubProperties properties;
    private final IdentityUserRepository users;
    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;

    public DevelopmentBootstrap(
            IdentityHubProperties properties,
            IdentityUserRepository users,
            RegisteredClientRepository clients,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        IdentityHubProperties.Bootstrap bootstrap = properties.bootstrap();
        if (!bootstrap.enabled()) {
            return;
        }

        users.findByEmailIgnoreCase(bootstrap.userEmail()).orElseGet(() -> users.save(
                new IdentityUser(
                        bootstrap.userEmail(),
                        bootstrap.userName(),
                        passwordEncoder.encode(bootstrap.userPassword()))));

        if (clients.findByClientId(bootstrap.clientId()) == null) {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(bootstrap.clientId())
                    .clientName(bootstrap.clientName())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri(bootstrap.redirectUri())
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(true)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .authorizationCodeTimeToLive(Duration.ofMinutes(2))
                            .accessTokenTimeToLive(Duration.ofMinutes(5))
                            .build())
                    .build();
            clients.save(client);
        }
    }
}
