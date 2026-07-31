package com.karamba121.backend.features.session;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

public class PublicRefreshClientAuthenticationConverter implements AuthenticationConverter {

    public static final String ELIGIBLE_REQUEST = PublicRefreshClientAuthenticationConverter.class.getName();

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod()) || !isRefreshOrRevocation(request)) {
            return null;
        }
        String clientId = request.getParameter("client_id");
        if (!StringUtils.hasText(clientId)
                || request.getParameterValues("client_id") == null
                || request.getParameterValues("client_id").length != 1) {
            return null;
        }
        return new OAuth2ClientAuthenticationToken(
                clientId,
                ClientAuthenticationMethod.NONE,
                null,
                Map.of(ELIGIBLE_REQUEST, true));
    }

    private static boolean isRefreshOrRevocation(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ("/oauth2/token".equals(path)
                && AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(request.getParameter("grant_type")))
                || "/oauth2/revoke".equals(path);
    }
}
