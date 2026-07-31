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
import com.karamba121.backend.features.access.PermissionDefinitionRepository;
import com.karamba121.backend.features.access.TenantRole;
import com.karamba121.backend.features.access.TenantRoleRepository;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantMembership;
import com.karamba121.backend.features.tenancy.TenantMembershipRepository;
import com.karamba121.backend.features.tenancy.TenantRepository;

@Component
public class DevelopmentBootstrap implements ApplicationRunner {

    private final IdentityHubProperties properties;
    private final IdentityUserRepository users;
    private final RegisteredClientRepository clients;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenants;
    private final TenantMembershipRepository memberships;
    private final TenantRoleRepository roles;
    private final PermissionDefinitionRepository permissions;

    public DevelopmentBootstrap(
            IdentityHubProperties properties,
            IdentityUserRepository users,
            RegisteredClientRepository clients,
            PasswordEncoder passwordEncoder,
            TenantRepository tenants,
            TenantMembershipRepository memberships,
            TenantRoleRepository roles,
            PermissionDefinitionRepository permissions) {
        this.properties = properties;
        this.users = users;
        this.clients = clients;
        this.passwordEncoder = passwordEncoder;
        this.tenants = tenants;
        this.memberships = memberships;
        this.roles = roles;
        this.permissions = permissions;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        IdentityHubProperties.Bootstrap bootstrap = properties.bootstrap();
        RegisteredClient existingClient = clients.findByClientId(bootstrap.clientId());
        if (!bootstrap.enabled()) {
            if (existingClient != null) {
                clients.save(configureClientSecurity(existingClient));
            }
            return;
        }

        IdentityUser user = users.findByEmailIgnoreCase(bootstrap.userEmail()).orElseGet(() -> users.save(
                new IdentityUser(
                        bootstrap.userEmail(),
                        bootstrap.userName(),
                        passwordEncoder.encode(bootstrap.userPassword()))));
        Tenant tenant = tenants.findBySlugIgnoreCase(bootstrap.tenantSlug())
                .orElseGet(() -> tenants.save(new Tenant(
                        bootstrap.tenantSlug(), bootstrap.tenantName())));
        TenantMembership membership = memberships.findByTenantIdAndUserId(tenant.getId(), user.getId())
                .orElseGet(() -> memberships.save(new TenantMembership(tenant, user)));
        TenantRole administrator = roles.findByTenantIdAndCode(tenant.getId(), "administrator")
                .orElseGet(() -> new TenantRole(tenant, "administrator", "Administrador", true));
        permissions.findAllByOrderBySortOrderAsc().forEach(administrator::grant);
        administrator = roles.save(administrator);
        membership.assignRole(administrator);
        memberships.save(membership);

        if (existingClient == null) {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId(bootstrap.clientId())
                    .clientName(bootstrap.clientName())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .redirectUri(bootstrap.redirectUri())
                    .postLogoutRedirectUri(bootstrap.postLogoutRedirectUri())
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope(OidcScopes.EMAIL)
                    .scope("demo.read")
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
            clients.save(client);
        } else {
            clients.save(configureClientSecurity(existingClient));
        }
    }

    private RegisteredClient configureClientSecurity(RegisteredClient existingClient) {
        return RegisteredClient.from(existingClient)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .postLogoutRedirectUri(properties.bootstrap().postLogoutRedirectUri())
                .scope("demo.read")
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofMinutes(2))
                        .accessTokenTimeToLive(Duration.ofMinutes(5))
                        .refreshTokenTimeToLive(Duration.ofHours(8))
                        .reuseRefreshTokens(false)
                        .build())
                .build();
    }
}
