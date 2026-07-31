package com.karamba121.backend.features.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.transaction.annotation.Transactional;

public class RefreshTokenTrackingAuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenFamilyRepository families;
    private final RefreshTokenHistoryRepository history;
    private final SessionMetrics metrics;

    public RefreshTokenTrackingAuthorizationService(
            OAuth2AuthorizationService delegate,
            RefreshTokenFamilyRepository families,
            RefreshTokenHistoryRepository history,
            SessionMetrics metrics) {
        this.delegate = delegate;
        this.families = families;
        this.history = history;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
        OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken = authorization.getRefreshToken();
        if (refreshToken == null) {
            return;
        }

        String tokenHash = hash(refreshToken.getToken().getTokenValue());
        RefreshTokenFamily family = families.findByAuthorizationId(authorization.getId()).orElse(null);
        if (family == null) {
            if (refreshToken.isActive()) {
                createFamily(authorization, refreshToken, tokenHash);
            }
            return;
        }

        family = families.findByIdForUpdate(family.getId()).orElseThrow();
        if (!refreshToken.isActive()) {
            revoke(family, Instant.now());
            return;
        }
        if (family.getStatus() != RefreshTokenFamilyStatus.ACTIVE) {
            delegate.remove(authorization);
            return;
        }
        if (family.getCurrentTokenHash().equals(tokenHash)) {
            return;
        }

        Instant issuedAt = requiredInstant(refreshToken.getToken().getIssuedAt(), Instant.now());
        Instant expiresAt = requiredInstant(refreshToken.getToken().getExpiresAt(), issuedAt);
        history.findById(family.getCurrentTokenHash()).ifPresent(current -> current.markUsed(issuedAt));
        family.rotateTo(tokenHash, issuedAt, expiresAt);
        history.save(new RefreshTokenHistory(tokenHash, family.getId(), issuedAt));
        metrics.recordEventAfterCommit(SessionMetrics.ROTATED);
    }

    @Override
    @Transactional
    public void remove(OAuth2Authorization authorization) {
        families.findByAuthorizationId(authorization.getId())
                .flatMap(family -> families.findByIdForUpdate(family.getId()))
                .ifPresent(family -> revoke(family, Instant.now()));
        delegate.remove(authorization);
    }

    @Override
    @Transactional(readOnly = true)
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    @Transactional
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        if (token == null || (tokenType != null && !OAuth2TokenType.REFRESH_TOKEN.equals(tokenType))) {
            return delegate.findByToken(token, tokenType);
        }

        String tokenHash = hash(token);
        RefreshTokenHistory tokenHistory = history.findById(tokenHash).orElse(null);
        if (tokenHistory == null) {
            OAuth2Authorization authorization = delegate.findByToken(token, tokenType);
            if (authorization != null && authorization.getRefreshToken() != null
                    && token.equals(authorization.getRefreshToken().getToken().getTokenValue())) {
                createFamily(authorization, authorization.getRefreshToken(), tokenHash);
            }
            return authorization;
        }

        RefreshTokenFamily family = families.findByIdForUpdate(tokenHistory.getFamilyId()).orElse(null);
        if (family == null) {
            return null;
        }
        if (tokenHistory.getStatus() == RefreshTokenStatus.CURRENT
                && family.getStatus() == RefreshTokenFamilyStatus.ACTIVE
                && tokenHash.equals(family.getCurrentTokenHash())) {
            return delegate.findByToken(token, tokenType);
        }

        compromise(family, Instant.now());
        return null;
    }

    private void createFamily(
            OAuth2Authorization authorization,
            OAuth2Authorization.Token<OAuth2RefreshToken> refreshToken,
            String tokenHash) {
        Instant issuedAt = requiredInstant(refreshToken.getToken().getIssuedAt(), Instant.now());
        Instant expiresAt = requiredInstant(refreshToken.getToken().getExpiresAt(), issuedAt);
        RefreshTokenFamily family = families.save(
                new RefreshTokenFamily(authorization.getId(), tokenHash, issuedAt, expiresAt));
        history.save(new RefreshTokenHistory(tokenHash, family.getId(), issuedAt));
        metrics.recordEventAfterCommit(SessionMetrics.FAMILY_CREATED);
    }

    private void compromise(RefreshTokenFamily family, Instant at) {
        family.compromise(at);
        history.findAllByFamilyId(family.getId()).forEach(token -> token.revoke(at));
        OAuth2Authorization currentAuthorization = delegate.findById(family.getAuthorizationId());
        if (currentAuthorization != null) {
            delegate.remove(currentAuthorization);
        }
        metrics.recordEventAfterCommit(SessionMetrics.REPLAY_DETECTED);
    }

    private void revoke(RefreshTokenFamily family, Instant at) {
        boolean newlyRevoked = family.getStatus() == RefreshTokenFamilyStatus.ACTIVE;
        family.revoke(at);
        history.findAllByFamilyId(family.getId()).forEach(token -> token.revoke(at));
        if (newlyRevoked) {
            metrics.recordEventAfterCommit(SessionMetrics.REVOKED);
        }
    }

    private static Instant requiredInstant(Instant value, Instant fallback) {
        return value == null ? fallback : value;
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 não está disponível", exception);
        }
    }
}
