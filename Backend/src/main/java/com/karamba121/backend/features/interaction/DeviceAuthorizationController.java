package com.karamba121.backend.features.interaction;

import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2UserCode;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/device-authorization")
public class DeviceAuthorizationController {

    private static final OAuth2TokenType USER_CODE_TOKEN_TYPE =
            new OAuth2TokenType(OAuth2ParameterNames.USER_CODE);

    private final OAuth2AuthorizationService authorizations;
    private final RegisteredClientRepository clients;

    public DeviceAuthorizationController(
            OAuth2AuthorizationService authorizations,
            RegisteredClientRepository clients) {
        this.authorizations = authorizations;
        this.clients = clients;
    }

    @GetMapping("/consent")
    public DeviceConsentView consent(
            @RequestParam("client_id") String clientId,
            @RequestParam("user_code") String userCode) {
        OAuth2Authorization authorization = authorizations.findByToken(userCode, USER_CODE_TOKEN_TYPE);
        if (authorization == null || authorization.getToken(OAuth2UserCode.class) == null
                || !authorization.getToken(OAuth2UserCode.class).isActive()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Código de dispositivo inválido ou expirado");
        }
        RegisteredClient client = clients.findById(authorization.getRegisteredClientId());
        if (client == null || !client.getClientId().equals(clientId)
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.DEVICE_CODE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente de dispositivo inconsistente");
        }
        @SuppressWarnings("unchecked")
        Set<String> requestedScopes = authorization.getAttribute(OAuth2ParameterNames.SCOPE);
        return new DeviceConsentView(
                client.getClientId(),
                client.getClientName(),
                requestedScopes == null ? Set.of() : Set.copyOf(requestedScopes));
    }

    public record DeviceConsentView(String clientId, String clientName, Set<String> scopes) {
    }
}
