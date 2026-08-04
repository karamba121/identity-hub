package com.karamba121.backend.features.interaction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.karamba121.backend.config.IdentityHubProperties;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class AuthorizationInteractionService {

    private static final OAuth2TokenType STATE_TOKEN_TYPE = new OAuth2TokenType("state");
    private static final OAuth2TokenType USER_CODE_TOKEN_TYPE =
            new OAuth2TokenType(OAuth2ParameterNames.USER_CODE);
    private static final String PAR_REQUEST_URI_PREFIX = "urn:ietf:params:oauth:request_uri:";
    private static final String PAR_REQUEST_URI_DELIMITER = "___";

    private final SecureRandom secureRandom = new SecureRandom();
    private final AuthorizationInteractionRepository interactions;
    private final RegisteredClientRepository clients;
    private final OAuth2AuthorizationService authorizations;
    private final IdentityHubProperties properties;
    private final TransactionTemplate cleanupTransaction;

    public AuthorizationInteractionService(
            AuthorizationInteractionRepository interactions,
            RegisteredClientRepository clients,
            OAuth2AuthorizationService authorizations,
            IdentityHubProperties properties,
            PlatformTransactionManager transactionManager) {
        this.interactions = interactions;
        this.clients = clients;
        this.authorizations = authorizations;
        this.properties = properties;
        this.cleanupTransaction = new TransactionTemplate(transactionManager);
        this.cleanupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public String createLogin(HttpServletRequest request) {
        if (request.getRequestURI().endsWith("/oauth2/device_verification")) {
            return createDeviceLogin(request);
        }
        String clientId = required(request.getParameter("client_id"), "client_id ausente");
        RegisteredClient client = requireClient(clientId);
        OAuth2AuthorizationRequest pushedRequest = resolvePushedRequest(request, client);
        String redirectUri = pushedRequest == null
                ? required(request.getParameter("redirect_uri"), "redirect_uri ausente")
                : pushedRequest.getRedirectUri();
        if (!client.getRedirectUris().contains(redirectUri)) {
            throw new InteractionException(HttpStatus.BAD_REQUEST, "redirect_uri inválida");
        }

        String resumeUri = request.getRequestURI();
        if (StringUtils.hasText(request.getQueryString())) {
            resumeUri += "?" + request.getQueryString();
        }

        return create(
                request,
                InteractionType.LOGIN,
                null,
                clientId,
                pushedRequest == null ? request.getParameter("scope") : String.join(" ", pushedRequest.getScopes()),
                pushedRequest == null ? request.getParameter("state") : pushedRequest.getState(),
                resumeUri,
                redirectUri);
    }

    private String createDeviceLogin(HttpServletRequest request) {
        String userCode = required(request.getParameter(OAuth2ParameterNames.USER_CODE), "user_code ausente");
        OAuth2Authorization authorization = authorizations.findByToken(userCode, USER_CODE_TOKEN_TYPE);
        if (authorization == null || authorization.getToken(OAuth2UserCode.class) == null
                || !authorization.getToken(OAuth2UserCode.class).isActive()) {
            throw new InteractionException(HttpStatus.BAD_REQUEST, "Código de dispositivo inválido ou expirado");
        }
        RegisteredClient client = clients.findById(authorization.getRegisteredClientId());
        if (client == null || !client.getAuthorizationGrantTypes()
                .contains(org.springframework.security.oauth2.core.AuthorizationGrantType.DEVICE_CODE)) {
            throw new InteractionException(HttpStatus.BAD_REQUEST, "Cliente de dispositivo inválido");
        }
        @SuppressWarnings("unchecked")
        Set<String> requestedScopes = authorization.getAttribute(OAuth2ParameterNames.SCOPE);
        String resumeUri = request.getRequestURI();
        if (StringUtils.hasText(request.getQueryString())) {
            resumeUri += "?" + request.getQueryString();
        }
        return create(
                request,
                InteractionType.LOGIN,
                null,
                client.getClientId(),
                requestedScopes == null ? "" : String.join(" ", requestedScopes),
                null,
                resumeUri,
                null);
    }

    private OAuth2AuthorizationRequest resolvePushedRequest(
            HttpServletRequest request,
            RegisteredClient client) {
        String requestUri = request.getParameter("request_uri");
        if (!StringUtils.hasText(requestUri)) {
            return null;
        }
        if (!requestUri.startsWith(PAR_REQUEST_URI_PREFIX)) {
            throw invalidPushedRequest();
        }
        String state = requestUri.substring(PAR_REQUEST_URI_PREFIX.length());
        int delimiter = state.lastIndexOf(PAR_REQUEST_URI_DELIMITER);
        if (delimiter <= 0) {
            throw invalidPushedRequest();
        }
        Instant expiresAt;
        try {
            expiresAt = Instant.ofEpochMilli(Long.parseLong(
                    state.substring(delimiter + PAR_REQUEST_URI_DELIMITER.length())));
        } catch (RuntimeException exception) {
            throw invalidPushedRequest();
        }

        OAuth2Authorization authorization = authorizations.findByToken(state, STATE_TOKEN_TYPE);
        if (authorization == null) {
            throw invalidPushedRequest();
        }
        if (!expiresAt.isAfter(Instant.now())) {
            cleanupTransaction.executeWithoutResult(status -> authorizations.remove(authorization));
            throw invalidPushedRequest();
        }
        if (!authorization.getRegisteredClientId().equals(client.getId())) {
            throw invalidPushedRequest();
        }
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest(authorization);
        if (!client.getClientId().equals(authorizationRequest.getClientId())) {
            throw invalidPushedRequest();
        }
        return authorizationRequest;
    }

    private InteractionException invalidPushedRequest() {
        return new InteractionException(HttpStatus.BAD_REQUEST, "request_uri PAR inválida");
    }

    @Transactional
    public String createConsent(
            HttpServletRequest request,
            String principalName,
            String clientId,
            String scopes,
            String consentState) {
        OAuth2Authorization authorization = requireAuthorization(consentState);
        if (!authorization.getPrincipalName().equals(principalName)) {
            throw new InteractionException(HttpStatus.FORBIDDEN, "Interação não pertence à sessão autenticada");
        }
        RegisteredClient client = requireClient(clientId);
        if (!authorization.getRegisteredClientId().equals(client.getId())) {
            throw new InteractionException(HttpStatus.BAD_REQUEST, "Cliente inconsistente");
        }
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest(authorization);

        return create(
                request,
                InteractionType.CONSENT,
                principalName,
                clientId,
                scopes,
                consentState,
                null,
                authorizationRequest.getRedirectUri());
    }

    @Transactional(readOnly = true)
    public AuthorizationInteraction resolvePending(String rawId, HttpServletRequest request) {
        AuthorizationInteraction interaction = resolve(rawId, request);
        if (interaction.getStatus() != InteractionStatus.PENDING) {
            throw new InteractionException(HttpStatus.GONE, "Interação já utilizada");
        }
        return interaction;
    }

    @Transactional(readOnly = true)
    public AuthorizationInteraction resolvePending(String rawId, HttpServletRequest request, InteractionType type) {
        AuthorizationInteraction interaction = resolvePending(rawId, request);
        if (interaction.getType() != type) {
            throw new InteractionException(HttpStatus.GONE, "Interação já utilizada");
        }
        return interaction;
    }

    public void requirePrincipal(AuthorizationInteraction interaction, String principalName) {
        if (!StringUtils.hasText(interaction.getPrincipalName())
                || !interaction.getPrincipalName().equals(principalName)) {
            throw new InteractionException(HttpStatus.FORBIDDEN, "Interação não pertence ao usuário autenticado");
        }
    }

    @Transactional(readOnly = true)
    public AuthorizationInteraction resolveApproved(String rawId, HttpServletRequest request) {
        AuthorizationInteraction interaction = resolve(rawId, request);
        if (interaction.getType() != InteractionType.CONSENT
                || interaction.getStatus() != InteractionStatus.APPROVED) {
            throw new InteractionException(HttpStatus.GONE, "Interação não está aprovada");
        }
        return interaction;
    }

    @Transactional
    public void completeLogin(AuthorizationInteraction interaction, String newSessionId) {
        interaction.rebindSession(hash(newSessionId));
        interaction.complete();
        interactions.save(interaction);
    }

    @Transactional
    public void approve(AuthorizationInteraction interaction) {
        interaction.approve();
        interactions.save(interaction);
    }

    @Transactional
    public void complete(AuthorizationInteraction interaction) {
        interaction.complete();
        interactions.save(interaction);
    }

    @Transactional
    public String deny(AuthorizationInteraction interaction) {
        OAuth2Authorization authorization = requireAuthorization(interaction.getOauthState());
        OAuth2AuthorizationRequest authorizationRequest = authorizationRequest(authorization);
        interaction.deny();
        interactions.save(interaction);
        authorizations.remove(authorization);

        String separator = authorizationRequest.getRedirectUri().contains("?") ? "&" : "?";
        String redirect = authorizationRequest.getRedirectUri() + separator + "error=access_denied";
        if (StringUtils.hasText(authorizationRequest.getState())) {
            redirect += "&state=" + java.net.URLEncoder.encode(
                    authorizationRequest.getState(), StandardCharsets.UTF_8);
        }
        return redirect;
    }

    public RegisteredClient requireClient(String clientId) {
        RegisteredClient client = clients.findByClientId(clientId);
        if (client == null) {
            throw new InteractionException(HttpStatus.BAD_REQUEST, "Cliente OAuth inválido");
        }
        return client;
    }

    public Set<String> scopes(AuthorizationInteraction interaction) {
        if (!StringUtils.hasText(interaction.getRequestedScopes())) {
            return Set.of();
        }
        return Set.of(StringUtils.delimitedListToStringArray(interaction.getRequestedScopes(), " "));
    }

    private String create(
            HttpServletRequest request,
            InteractionType type,
            String principalName,
            String clientId,
            String scopes,
            String oauthState,
            String resumeUri,
            String redirectUri) {
        String rawId = newRawId();
        AuthorizationInteraction interaction = new AuthorizationInteraction(
                hash(rawId),
                hash(request.getSession(true).getId()),
                type,
                principalName,
                clientId,
                scopes == null ? "" : scopes,
                oauthState,
                resumeUri,
                redirectUri,
                Instant.now().plus(properties.interactionTtl()));
        interactions.save(interaction);
        return rawId;
    }

    private AuthorizationInteraction resolve(String rawId, HttpServletRequest request) {
        if (!StringUtils.hasText(rawId) || request.getSession(false) == null) {
            throw new InteractionException(HttpStatus.NOT_FOUND, "Interação não encontrada");
        }
        AuthorizationInteraction interaction = interactions
                .findByIdHashAndSessionIdHash(hash(rawId), hash(request.getSession(false).getId()))
                .orElseThrow(() -> new InteractionException(HttpStatus.NOT_FOUND, "Interação não encontrada"));
        if (!interaction.getExpiresAt().isAfter(Instant.now())) {
            throw new InteractionException(HttpStatus.GONE, "Interação expirada");
        }
        return interaction;
    }

    private OAuth2Authorization requireAuthorization(String state) {
        OAuth2Authorization authorization = authorizations.findByToken(state, STATE_TOKEN_TYPE);
        if (authorization == null) {
            throw new InteractionException(HttpStatus.GONE, "Pedido de autorização expirado");
        }
        return authorization;
    }

    private OAuth2AuthorizationRequest authorizationRequest(OAuth2Authorization authorization) {
        OAuth2AuthorizationRequest request = authorization
                .getAttribute(OAuth2AuthorizationRequest.class.getName());
        if (request == null) {
            throw new InteractionException(HttpStatus.GONE, "Contexto de autorização indisponível");
        }
        return request;
    }

    private String newRawId() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }

    private String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new InteractionException(HttpStatus.BAD_REQUEST, message);
        }
        return value;
    }
}
