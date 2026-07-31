package com.karamba121.backend.features.session;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

public class PublicRefreshClientAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository clients;

    public PublicRefreshClientAuthenticationProvider(RegisteredClientRepository clients) {
        this.clients = clients;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken clientAuthentication =
                (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(clientAuthentication.getClientAuthenticationMethod())
                || !Boolean.TRUE.equals(clientAuthentication.getAdditionalParameters()
                        .get(PublicRefreshClientAuthenticationConverter.ELIGIBLE_REQUEST))) {
            return null;
        }

        RegisteredClient client = clients.findByClientId(clientAuthentication.getPrincipal().toString());
        if (client == null
                || !client.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)
                || !client.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw new OAuth2AuthenticationException("invalid_client");
        }
        return new OAuth2ClientAuthenticationToken(
                client,
                ClientAuthenticationMethod.NONE,
                null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
