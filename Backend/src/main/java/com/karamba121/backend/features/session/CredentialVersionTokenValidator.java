package com.karamba121.backend.features.session;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import com.karamba121.backend.features.identity.IdentityUser;
import com.karamba121.backend.features.identity.IdentityUserRepository;

public class CredentialVersionTokenValidator implements OAuth2TokenValidator<Jwt> {

    public static final String CLAIM = "credential_version";

    private static final OAuth2Error INVALID_TOKEN = new OAuth2Error(
            "invalid_token",
            "A credencial associada ao access token não está mais vigente",
            null);

    private final IdentityUserRepository users;

    public CredentialVersionTokenValidator(IdentityUserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object claimedVersion = token.getClaim(CLAIM);
        IdentityUser user = token.getSubject() == null
                ? null
                : users.findById(token.getSubject()).orElse(null);
        if (user == null) {
            return OAuth2TokenValidatorResult.success();
        }
        if (claimedVersion == null
                || !Long.toString(user.getCredentialVersion()).equals(String.valueOf(claimedVersion))
                || !user.isEnabled()
                || !user.isEmailVerified()) {
            return OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
        }
        return OAuth2TokenValidatorResult.success();
    }
}
