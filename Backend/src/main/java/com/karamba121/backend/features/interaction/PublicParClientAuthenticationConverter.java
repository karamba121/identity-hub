package com.karamba121.backend.features.interaction;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

public final class PublicParClientAuthenticationConverter implements AuthenticationConverter {

    static final String ELIGIBLE_REQUEST = PublicParClientAuthenticationConverter.class.getName();

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod()) || !"/oauth2/par".equals(request.getRequestURI())) {
            return null;
        }
        String clientId = request.getParameter("client_id");
        String[] clientIds = request.getParameterValues("client_id");
        if (!StringUtils.hasText(clientId) || clientIds == null || clientIds.length != 1) {
            return null;
        }
        return new OAuth2ClientAuthenticationToken(
                clientId,
                ClientAuthenticationMethod.NONE,
                null,
                Map.of(ELIGIBLE_REQUEST, true));
    }
}
