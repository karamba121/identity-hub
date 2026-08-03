package com.karamba121.backend.features.identity;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;

public class LoginProtectionAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider delegate;
    private final LoginAttemptService attempts;

    public LoginProtectionAuthenticationProvider(AuthenticationProvider delegate, LoginAttemptService attempts) {
        this.delegate = delegate;
        this.attempts = attempts;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        try {
            Authentication result = delegate.authenticate(authentication);
            attempts.succeeded(authentication.getName());
            return result;
        } catch (AuthenticationException exception) {
            attempts.failed(authentication.getName());
            throw exception;
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return delegate.supports(authentication);
    }
}
