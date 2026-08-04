package com.karamba121.backend.features.interaction;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

public final class PublicDeviceClientAuthenticationConverter implements AuthenticationConverter {

    static final String ELIGIBLE_REQUEST = PublicDeviceClientAuthenticationConverter.class.getName();

    @Override
    public Authentication convert(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod()) || !isDeviceRequest(request)) {
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

    private static boolean isDeviceRequest(HttpServletRequest request) {
        return "/oauth2/device_authorization".equals(request.getRequestURI())
                || ("/oauth2/token".equals(request.getRequestURI())
                    && AuthorizationGrantType.DEVICE_CODE.getValue().equals(request.getParameter("grant_type")));
    }
}
