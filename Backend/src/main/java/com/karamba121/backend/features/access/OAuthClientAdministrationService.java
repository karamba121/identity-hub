package com.karamba121.backend.features.access;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.resource.DemoResourceContract;
import com.karamba121.backend.features.scim.ScimResourceContract;
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantRepository;

@Service
public class OAuthClientAdministrationService {

    private static final Set<String> SUPPORTED_SCOPES = Set.of(
            OidcScopes.OPENID,
            OidcScopes.PROFILE,
            OidcScopes.EMAIL,
            DemoResourceContract.SCOPE,
            AdminResourceContract.SCOPE,
            ScimResourceContract.READ_SCOPE,
            ScimResourceContract.WRITE_SCOPE);
    private static final Set<String> CONFIDENTIAL_SCOPES = Set.of(
            DemoResourceContract.SCOPE,
            ScimResourceContract.READ_SCOPE,
            ScimResourceContract.WRITE_SCOPE);
    private static final Set<String> DEVICE_SCOPES = Set.of(
            OidcScopes.OPENID,
            OidcScopes.PROFILE,
            OidcScopes.EMAIL,
            DemoResourceContract.SCOPE);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RegisteredClientRepository clients;
    private final TenantOAuthClientRepository ownerships;
    private final TenantRepository tenants;
    private final JdbcOperations jdbc;
    private final RotatingClientSecretPasswordEncoder passwordEncoder;

    public OAuthClientAdministrationService(
            RegisteredClientRepository clients,
            TenantOAuthClientRepository ownerships,
            TenantRepository tenants,
            JdbcOperations jdbc,
            RotatingClientSecretPasswordEncoder passwordEncoder) {
        this.clients = clients;
        this.ownerships = ownerships;
        this.tenants = tenants;
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<OAuthClientView> list(String tenantId) {
        return ownerships.findAllByTenantIdOrderByClientIdAsc(tenantId).stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public OAuthClientView get(String tenantId, String clientId) {
        return view(ownership(tenantId, clientId));
    }

    @Transactional
    public OAuthClientView create(String tenantId, OAuthClientCommand command) {
        ValidatedClient validated = validate(command, true);
        if (clients.findByClientId(validated.clientId()) != null) {
            throw OAuthClientAdministrationException.conflict("Client ID já cadastrado");
        }
        Tenant tenant = tenants.findById(tenantId)
                .orElseThrow(OAuthClientAdministrationException::notFound);
        String clientSecret = validated.clientType() == ClientType.CONFIDENTIAL ? generateClientSecret() : null;
        RegisteredClient.Builder builder = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(validated.clientId())
                .clientName(validated.clientName())
                .redirectUris(values -> values.addAll(validated.redirectUris()))
                .postLogoutRedirectUris(values -> values.addAll(validated.postLogoutRedirectUris()))
                .scopes(values -> values.addAll(validated.scopes()));
        if (validated.clientType() == ClientType.CONFIDENTIAL) {
            builder.clientSecret(passwordEncoder.encode(clientSecret))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .clientSettings(confidentialClientSettings())
                    .tokenSettings(confidentialClientTokenSettings());
        } else if (validated.clientType() == ClientType.DEVICE) {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                    .clientSettings(deviceClientSettings())
                    .tokenSettings(deviceClientTokenSettings());
        } else {
            builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .clientSettings(publicClientSettings())
                    .tokenSettings(publicClientTokenSettings());
        }
        RegisteredClient client = builder.build();
        try {
            clients.save(client);
            TenantOAuthClient ownership = ownerships.save(new TenantOAuthClient(
                    tenant, client.getId(), client.getClientId()));
            return view(ownership, client, clientSecret);
        } catch (DataIntegrityViolationException exception) {
            throw OAuthClientAdministrationException.conflict("Client ID já cadastrado");
        }
    }

    @Transactional
    public OAuthClientView update(String tenantId, String clientId, OAuthClientCommand command) {
        TenantOAuthClient ownership = ownership(tenantId, clientId);
        RegisteredClient existing = registeredClient(ownership);
        ClientType clientType = clientType(existing);
        ValidatedClient validated = validate(command, false, clientType);
        RegisteredClient.Builder builder = RegisteredClient.from(existing)
                .clientName(validated.clientName())
                .redirectUris(values -> replace(values, validated.redirectUris()))
                .postLogoutRedirectUris(values -> replace(values, validated.postLogoutRedirectUris()))
                .scopes(values -> replace(values, validated.scopes()));
        if (clientType == ClientType.CONFIDENTIAL) {
            builder.clientSettings(confidentialClientSettings()).tokenSettings(confidentialClientTokenSettings());
        } else if (clientType == ClientType.DEVICE) {
            builder.clientSettings(deviceClientSettings()).tokenSettings(deviceClientTokenSettings());
        } else {
            builder.clientSettings(publicClientSettings()).tokenSettings(publicClientTokenSettings());
        }
        RegisteredClient updated = builder.build();
        clients.save(updated);
        return view(ownership, updated, null);
    }

    @Transactional
    public OAuthClientView rotateSecret(String tenantId, String clientId, Integer previousSecretValidForMinutes) {
        TenantOAuthClient ownership = ownerships.findForSecretRotation(tenantId, clientId)
                .orElseThrow(OAuthClientAdministrationException::notFound);
        RegisteredClient existing = registeredClient(ownership);
        if (clientType(existing) != ClientType.CONFIDENTIAL || existing.getClientSecret() == null) {
            throw new IllegalArgumentException("Somente clientes confidenciais possuem secret para rotação");
        }
        int validityMinutes = previousSecretValidForMinutes == null ? 15 : previousSecretValidForMinutes;
        if (validityMinutes < 0 || validityMinutes > 1_440) {
            throw new IllegalArgumentException("Janela do secret anterior deve estar entre 0 e 1440 minutos");
        }
        String newSecret = generateClientSecret();
        RotatingClientSecretPasswordEncoder.Rotation rotation = passwordEncoder.rotate(
                newSecret,
                existing.getClientSecret(),
                Duration.ofMinutes(validityMinutes));
        RegisteredClient updated = RegisteredClient.from(existing)
                .clientSecret(rotation.encodedSecret())
                .build();
        clients.save(updated);
        return view(ownership, updated, newSecret);
    }

    @Transactional
    public void delete(String tenantId, String clientId) {
        TenantOAuthClient ownership = ownership(tenantId, clientId);
        String registeredClientId = ownership.getRegisteredClientId();
        jdbc.update("""
                delete from oauth_refresh_token_history
                where family_id in (
                    select family.id from oauth_refresh_token_family family
                    where family.authorization_id in (
                                select authz.id from oauth2_authorization authz
                                where authz.registered_client_id = ?
                    )
                )
                """, registeredClientId);
        jdbc.update("""
                delete from oauth_refresh_token_family
                where authorization_id in (
                                select authz.id from oauth2_authorization authz
                                where authz.registered_client_id = ?
                )
                """, registeredClientId);
        jdbc.update("delete from oauth2_authorization_consent where registered_client_id = ?", registeredClientId);
        jdbc.update("delete from oauth2_authorization where registered_client_id = ?", registeredClientId);
        ownerships.delete(ownership);
        ownerships.flush();
        jdbc.update("delete from oauth2_registered_client where id = ?", registeredClientId);
    }

    private TenantOAuthClient ownership(String tenantId, String clientId) {
        return ownerships.findByTenantIdAndClientId(tenantId, clientId)
                .orElseThrow(OAuthClientAdministrationException::notFound);
    }

    private RegisteredClient registeredClient(TenantOAuthClient ownership) {
        RegisteredClient client = clients.findById(ownership.getRegisteredClientId());
        if (client == null) {
            throw new IllegalStateException("Ownership aponta para cliente OAuth inexistente");
        }
        return client;
    }

    private OAuthClientView view(TenantOAuthClient ownership) {
        return view(ownership, registeredClient(ownership));
    }

    private OAuthClientView view(TenantOAuthClient ownership, RegisteredClient client) {
        return view(ownership, client, null);
    }

    private OAuthClientView view(
            TenantOAuthClient ownership, RegisteredClient client, String clientSecret) {
        ClientType clientType = clientType(client);
        return new OAuthClientView(
                client.getClientId(),
                client.getClientName(),
                client.getRedirectUris().stream().sorted().toList(),
                client.getPostLogoutRedirectUris().stream().sorted().toList(),
                client.getScopes().stream().sorted().toList(),
                clientType.name(),
                clientType == ClientType.PUBLIC,
                ownership.getCreatedAt(),
                clientSecret,
                client.getClientSecret() == null
                        ? null
                        : passwordEncoder.previousSecretExpiresAt(client.getClientSecret()).orElse(null));
    }

    private static ValidatedClient validate(OAuthClientCommand command, boolean creating) {
        if (command == null) {
            throw new IllegalArgumentException("Dados do cliente são obrigatórios");
        }
        ClientType clientType = creating ? ClientType.parse(command.clientType()) : ClientType.PUBLIC;
        return validate(command, creating, clientType);
    }

    private static ValidatedClient validate(
            OAuthClientCommand command, boolean creating, ClientType clientType) {
        if (command == null) {
            throw new IllegalArgumentException("Dados do cliente são obrigatórios");
        }
        String clientId = creating ? required(command.clientId(), "Client ID", 100) : null;
        if (creating && !clientId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{2,99}")) {
            throw new IllegalArgumentException("Client ID inválido");
        }
        String clientName = required(command.clientName(), "Nome do cliente", 200);
        Set<String> redirectUris = uris(command.redirectUris(), "Redirect URI", clientType == ClientType.PUBLIC);
        Set<String> postLogoutRedirectUris = uris(
                command.postLogoutRedirectUris(), "Post logout redirect URI", false);
        Set<String> scopes = scopes(command.scopes());
        if (clientType == ClientType.CONFIDENTIAL) {
            if (!redirectUris.isEmpty() || !postLogoutRedirectUris.isEmpty()) {
                throw new IllegalArgumentException("Cliente confidencial de máquina não aceita redirect URIs");
            }
            if (!CONFIDENTIAL_SCOPES.containsAll(scopes)) {
                throw new IllegalArgumentException("Cliente confidencial aceita somente escopos de máquina");
            }
        } else if (clientType == ClientType.DEVICE) {
            if (!redirectUris.isEmpty() || !postLogoutRedirectUris.isEmpty()) {
                throw new IllegalArgumentException("Cliente de dispositivo não aceita redirect URIs");
            }
            if (!DEVICE_SCOPES.containsAll(scopes)) {
                throw new IllegalArgumentException("Cliente de dispositivo aceita somente escopos delegados de usuário");
            }
        } else if (scopes.contains(ScimResourceContract.READ_SCOPE)
                || scopes.contains(ScimResourceContract.WRITE_SCOPE)) {
            throw new IllegalArgumentException("Escopos SCIM exigem cliente confidencial de máquina");
        }
        return new ValidatedClient(clientId, clientName, redirectUris, postLogoutRedirectUris, scopes, clientType);
    }

    private static Set<String> uris(Set<String> values, String field, boolean required) {
        if ((values == null || values.isEmpty()) && required) {
            throw new IllegalArgumentException(field + " é obrigatória");
        }
        Set<String> normalized = new LinkedHashSet<>();
        if (values != null) {
            values.forEach(value -> normalized.add(validUri(value, field)));
        }
        if (normalized.size() > 10 || normalized.stream().mapToInt(String::length).sum() > 900) {
            throw new IllegalArgumentException(field + " excede o limite permitido");
        }
        return Set.copyOf(normalized);
    }

    private static String validUri(String value, String field) {
        String normalized = required(value, field, 500);
        if (normalized.contains("*")) {
            throw new IllegalArgumentException(field + " não aceita curingas");
        }
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " inválida");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean localHttp = "http".equals(scheme) && isLoopback(uri.getHost());
        if (!("https".equals(scheme) || localHttp)
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException(
                    field + " deve usar HTTPS, exceto HTTP para loopback local, sem fragmento ou credenciais");
        }
        return uri.toASCIIString();
    }

    private static boolean isLoopback(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equals(host)
                || "::1".equals(host);
    }

    private static Set<String> scopes(Set<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("Ao menos um escopo é obrigatório");
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String scope = required(value, "Escopo", 100);
            if (!SUPPORTED_SCOPES.contains(scope)) {
                throw new IllegalArgumentException("Escopo não suportado: " + scope);
            }
            normalized.add(scope);
        }
        return Set.copyOf(normalized);
    }

    private static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " é obrigatório");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " excede " + maxLength + " caracteres");
        }
        return normalized;
    }

    private static void replace(Set<String> target, Set<String> values) {
        target.clear();
        target.addAll(values);
    }

    private static ClientSettings publicClientSettings() {
        return ClientSettings.builder()
                .requireProofKey(true)
                .requireAuthorizationConsent(true)
                .build();
    }

    private static ClientSettings confidentialClientSettings() {
        return ClientSettings.builder()
                .requireProofKey(false)
                .requireAuthorizationConsent(false)
                .build();
    }

    private static ClientSettings deviceClientSettings() {
        return ClientSettings.builder()
                .requireProofKey(false)
                .requireAuthorizationConsent(true)
                .build();
    }

    private static TokenSettings confidentialClientTokenSettings() {
        return TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build();
    }

    private static ClientType clientType(RegisteredClient client) {
        if (client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.CLIENT_CREDENTIALS)) {
            return ClientType.CONFIDENTIAL;
        }
        return client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.DEVICE_CODE)
                ? ClientType.DEVICE
                : ClientType.PUBLIC;
    }

    private static String generateClientSecret() {
        byte[] value = new byte[32];
        SECURE_RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static TokenSettings publicClientTokenSettings() {
        return TokenSettings.builder()
                .authorizationCodeTimeToLive(Duration.ofMinutes(2))
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .refreshTokenTimeToLive(Duration.ofHours(8))
                .reuseRefreshTokens(false)
                .build();
    }

    private static TokenSettings deviceClientTokenSettings() {
        return TokenSettings.builder()
                .deviceCodeTimeToLive(Duration.ofMinutes(10))
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .build();
    }

    public record OAuthClientCommand(
            String clientId,
            String clientName,
            Set<String> redirectUris,
            Set<String> postLogoutRedirectUris,
            Set<String> scopes,
            String clientType) {
    }

    public record OAuthClientView(
            String clientId,
            String clientName,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            List<String> scopes,
            String clientType,
            boolean pkceRequired,
            Instant createdAt,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            String clientSecret,
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
            Instant previousSecretExpiresAt) {
    }

    private record ValidatedClient(
            String clientId,
            String clientName,
            Set<String> redirectUris,
            Set<String> postLogoutRedirectUris,
            Set<String> scopes,
            ClientType clientType) {
    }

    private enum ClientType {
        PUBLIC,
        CONFIDENTIAL,
        DEVICE;

        private static ClientType parse(String value) {
            if (value == null || value.isBlank()) {
                return PUBLIC;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Tipo de cliente deve ser PUBLIC, CONFIDENTIAL ou DEVICE");
            }
        }
    }
}
