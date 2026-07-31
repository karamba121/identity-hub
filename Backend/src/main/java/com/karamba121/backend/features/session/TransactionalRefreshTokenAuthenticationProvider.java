package com.karamba121.backend.features.session;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class TransactionalRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final TransactionTemplate transaction;
    private final SessionMetrics metrics;

    public TransactionalRefreshTokenAuthenticationProvider(
            AuthenticationProvider delegate,
            PlatformTransactionManager transactionManager,
            SessionMetrics metrics) {
        this.delegate = delegate;
        this.transaction = new TransactionTemplate(transactionManager);
        this.metrics = metrics;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        long startedAt = System.nanoTime();
        String outcome = SessionMetrics.SUCCESS;
        try {
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
        } catch (OAuth2AuthenticationException exception) {
            outcome = SessionMetrics.REJECTED;
            throw exception;
        } catch (RuntimeException exception) {
            outcome = containsDataAccessFailure(exception)
                    ? SessionMetrics.UNAVAILABLE
                    : SessionMetrics.ERROR;
            throw exception;
        } finally {
            metrics.recordRefreshAttempt(outcome, System.nanoTime() - startedAt);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static boolean containsDataAccessFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof DataAccessException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
