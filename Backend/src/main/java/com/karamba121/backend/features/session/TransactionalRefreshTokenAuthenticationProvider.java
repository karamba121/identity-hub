package com.karamba121.backend.features.session;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class TransactionalRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final TransactionTemplate transaction;

    public TransactionalRefreshTokenAuthenticationProvider(
            AuthenticationProvider delegate,
            PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2AuthenticationException[] failure = new OAuth2AuthenticationException[1];
        Authentication result = transaction.execute(status -> {
            try {
                return delegate.authenticate(authentication);
            } catch (OAuth2AuthenticationException exception) {
                failure[0] = exception;
                return null;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        return result;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
