package com.karamba121.backend.features.identity;

import org.springframework.security.crypto.password.PasswordEncoder;

public class NormalizingPasswordEncoder implements PasswordEncoder {

    private final PasswordEncoder delegate;

    public NormalizingPasswordEncoder(PasswordEncoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return delegate.encode(normalize(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return delegate.matches(normalize(rawPassword), encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return delegate.upgradeEncoding(encodedPassword);
    }

    private static String normalize(CharSequence rawPassword) {
        return PasswordPolicy.normalize(rawPassword == null ? null : rawPassword.toString());
    }
}
