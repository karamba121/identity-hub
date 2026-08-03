package com.karamba121.backend.features.access;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
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
import com.karamba121.backend.features.tenancy.Tenant;
import com.karamba121.backend.features.tenancy.TenantRepository;

@Service
public class OAuthClientAdministrationService {

    private static final Set<String> SUPPORTED_SCOPES = Set.of(
            OidcScopes.OPENID,
            OidcScopes.PROFILE,
            OidcScopes.EMAIL,
            DemoResourceContract.SCOPE,
            AdminResourceContract.SCOPE);

    private final RegisteredClientRepository clients;
    private final TenantOAuthClientRepository ownerships;
    private final TenantRepository tenants;
    private final JdbcOperations jdbc;

    public OAuthClientAdministrationService(
            RegisteredClientRepository clients,
            TenantOAuthClientRepository ownerships,
            TenantRepository tenants,
            JdbcOperations jdbc) {
        this.clients = clients;
        this.ownerships = ownerships;
        this.tenants = tenants;
        this.jdbc = jdbc;
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
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(validated.clientId())
                .clientName(validated.clientName())
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUris(values -> values.addAll(validated.redirectUris()))
                .postLogoutRedirectUris(values -> values.addAll(validated.postLogoutRedirectUris()))
                .scopes(values -> values.addAll(validated.scopes()))
                .clientSettings(publicClientSettings())
                .tokenSettings(publicClientTokenSettings())
                .build();
        try {
            clients.save(client);
            TenantOAuthClient ownership = ownerships.save(new TenantOAuthClient(
                    tenant, client.getId(), client.getClientId()));
            return view(ownership, client);
        } catch (DataIntegrityViolationException exception) {
            throw OAuthClientAdministrationException.conflict("Client ID já cadastrado");
        }
    }

    @Transactional
    public OAuthClientView update(String tenantId, String clientId, OAuthClientCommand command) {
        TenantOAuthClient ownership = ownership(tenantId, clientId);
        ValidatedClient validated = validate(command, false);
        RegisteredClient existing = registeredClient(ownership);
        RegisteredClient updated = RegisteredClient.from(existing)
                .clientName(validated.clientName())
                .redirectUris(values -> replace(values, validated.redirectUris()))
                .postLogoutRedirectUris(values -> replace(values, validated.postLogoutRedirectUris()))
                .scopes(values -> replace(values, validated.scopes()))
                .clientSettings(publicClientSettings())
                .tokenSettings(publicClientTokenSettings())
                .build();
        clients.save(updated);
        return view(ownership, updated);
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

    private static OAuthClientView view(TenantOAuthClient ownership, RegisteredClient client) {
        return new OAuthClientView(
                client.getClientId(),
                client.getClientName(),
                client.getRedirectUris().stream().sorted().toList(),
                client.getPostLogoutRedirectUris().stream().sorted().toList(),
                client.getScopes().stream().sorted().toList(),
                "PUBLIC",
                true,
                ownership.getCreatedAt());
    }

    private static ValidatedClient validate(OAuthClientCommand command, boolean creating) {
        if (command == null) {
            throw new IllegalArgumentException("Dados do cliente são obrigatórios");
        }
        String clientId = creating ? required(command.clientId(), "Client ID", 100) : null;
        if (creating && !clientId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{2,99}")) {
            throw new IllegalArgumentException("Client ID inválido");
        }
        String clientName = required(command.clientName(), "Nome do cliente", 200);
        Set<String> redirectUris = uris(command.redirectUris(), "Redirect URI", true);
        Set<String> postLogoutRedirectUris = uris(
                command.postLogoutRedirectUris(), "Post logout redirect URI", false);
        Set<String> scopes = scopes(command.scopes());
        return new ValidatedClient(clientId, clientName, redirectUris, postLogoutRedirectUris, scopes);
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

    private static TokenSettings publicClientTokenSettings() {
        return TokenSettings.builder()
                .authorizationCodeTimeToLive(Duration.ofMinutes(2))
                .accessTokenTimeToLive(Duration.ofMinutes(5))
                .refreshTokenTimeToLive(Duration.ofHours(8))
                .reuseRefreshTokens(false)
                .build();
    }

    public record OAuthClientCommand(
            String clientId,
            String clientName,
            Set<String> redirectUris,
            Set<String> postLogoutRedirectUris,
            Set<String> scopes) {
    }

    public record OAuthClientView(
            String clientId,
            String clientName,
            List<String> redirectUris,
            List<String> postLogoutRedirectUris,
            List<String> scopes,
            String clientType,
            boolean pkceRequired,
            Instant createdAt) {
    }

    private record ValidatedClient(
            String clientId,
            String clientName,
            Set<String> redirectUris,
            Set<String> postLogoutRedirectUris,
            Set<String> scopes) {
    }
}
