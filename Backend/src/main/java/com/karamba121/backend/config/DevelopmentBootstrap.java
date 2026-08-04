package com.karamba121.backend.config;

import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.karamba121.backend.features.access.AdministrativeBootstrapLockRepository;
import com.karamba121.backend.features.access.FirstAdministratorBootstrapService;
import com.karamba121.backend.features.access.AdminResourceContract;
import com.karamba121.backend.features.access.TenantOAuthClient;
import com.karamba121.backend.features.access.TenantOAuthClientRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;

@Component
public class DevelopmentBootstrap implements ApplicationRunner {

    private static final String BOOTSTRAP_LOCK = "first-administrator";

    private final IdentityHubProperties properties;
    private final RegisteredClientRepository clients;
    private final FirstAdministratorBootstrapService firstAdministratorBootstrap;
    private final TenantOAuthClientRepository clientOwnerships;
    private final TenantRepository tenants;
    private final AdministrativeBootstrapLockRepository bootstrapLocks;

    public DevelopmentBootstrap(
            IdentityHubProperties properties,
            RegisteredClientRepository clients,
            FirstAdministratorBootstrapService firstAdministratorBootstrap,
            TenantOAuthClientRepository clientOwnerships,
            TenantRepository tenants,
            AdministrativeBootstrapLockRepository bootstrapLocks) {
        this.properties = properties;
        this.clients = clients;
        this.firstAdministratorBootstrap = firstAdministratorBootstrap;
        this.clientOwnerships = clientOwnerships;
        this.tenants = tenants;
        this.bootstrapLocks = bootstrapLocks;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        IdentityHubProperties.Bootstrap bootstrap = properties.bootstrap();
        bootstrapLocks.findByLockNameForUpdate(BOOTSTRAP_LOCK)
                .orElseThrow(() -> new IllegalStateException("Lock do bootstrap administrativo não encontrado"));
        RegisteredClient existingClient = clients.findByClientId(bootstrap.clientId());
        if (!bootstrap.enabled()) {
            if (existingClient != null) {
                clients.save(configureClientSecurity(existingClient));
            }
            return;
        }

        firstAdministratorBootstrap.provision(bootstrap);

        RegisteredClient managedClient;
        if (existingClient == null) {
            managedClient = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(bootstrap.clientId())
                    .clientName(bootstrap.clientName())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri(bootstrap.redirectUri())
                    .redirectUri(adminRedirectUri())
                    .postLogoutRedirectUri(bootstrap.postLogoutRedirectUri())
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("demo.read")
                    .scope(AdminResourceContract.SCOPE)
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(true)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .authorizationCodeTimeToLive(Duration.ofMinutes(2))
                            .accessTokenTimeToLive(Duration.ofMinutes(5))
                            .refreshTokenTimeToLive(Duration.ofHours(8))
                            .reuseRefreshTokens(false)
                            .build())
                    .build();
        } else {
            managedClient = configureClientSecurity(existingClient);
        }
        clients.save(managedClient);
        linkClientToBootstrapTenant(managedClient, bootstrap);
    }

    private RegisteredClient configureClientSecurity(RegisteredClient existingClient) {
        return RegisteredClient.from(existingClient)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(adminRedirectUri())
                .postLogoutRedirectUri(properties.bootstrap().postLogoutRedirectUri())
                .scope("demo.read")
                .scope(AdminResourceContract.SCOPE)
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofMinutes(2))
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .refreshTokenTimeToLive(Duration.ofHours(8))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }

    private String adminRedirectUri() {
        String baseUrl = properties.uiBaseUrl();
        return (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                + "/admin/oauth-clients/callback";
    }

    private void linkClientToBootstrapTenant(
            RegisteredClient client,
            IdentityHubProperties.Bootstrap bootstrap) {
        if (clientOwnerships.existsById(client.getId())) {
            return;
        }
        tenants.findBySlugIgnoreCase(bootstrap.tenantSlug()).ifPresent(tenant -> clientOwnerships.save(
                new TenantOAuthClient(tenant, client.getId(), client.getClientId())));
    }
}
