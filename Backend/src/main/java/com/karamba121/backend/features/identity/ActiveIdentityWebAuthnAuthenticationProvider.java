package com.karamba121.backend.features.identity;

import java.util.Collection;
import java.util.HashSet;

import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthentication;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

public final class ActiveIdentityWebAuthnAuthenticationProvider implements AuthenticationProvider {

    private final AccountStatusUserDetailsChecker accountStatus = new AccountStatusUserDetailsChecker();
    private final WebAuthnRelyingPartyOperations relyingParty;
    private final UserCredentialRepository credentials;
    private final PublicKeyCredentialUserEntityRepository users;
    private final UserDetailsService userDetailsService;

    public ActiveIdentityWebAuthnAuthenticationProvider(
            WebAuthnRelyingPartyOperations relyingParty,
            UserCredentialRepository credentials,
            PublicKeyCredentialUserEntityRepository users,
            UserDetailsService userDetailsService) {
        this.relyingParty = relyingParty;
        this.credentials = credentials;
        this.users = users;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        WebAuthnAuthenticationRequestToken request = (WebAuthnAuthenticationRequestToken) authentication;
        try {
            CredentialRecord credential = credentials.findByCredentialId(
                    request.getWebAuthnRequest().getPublicKey().getRawId());
            if (credential == null) {
                throw new BadCredentialsException("Passkey inválida");
            }
            PublicKeyCredentialUserEntity expectedUser = users.findById(credential.getUserEntityUserId());
            if (expectedUser == null) {
                throw new BadCredentialsException("Passkey inválida");
            }
            UserDetails userDetails = userDetailsService.loadUserByUsername(expectedUser.getName());
            accountStatus.check(userDetails);

            PublicKeyCredentialUserEntity verifiedUser = relyingParty.authenticate(request.getWebAuthnRequest());
            if (!expectedUser.getId().equals(verifiedUser.getId())
                    || !userDetails.getUsername().equalsIgnoreCase(verifiedUser.getName())) {
                throw new BadCredentialsException("Passkey inválida");
            }
            Collection<GrantedAuthority> authorities = new HashSet<>(userDetails.getAuthorities());
            authorities.add(FactorGrantedAuthority.fromAuthority(FactorGrantedAuthority.WEBAUTHN_AUTHORITY));
            return new WebAuthnAuthentication(verifiedUser, authorities);
        } catch (AuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BadCredentialsException("Passkey inválida", exception);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return WebAuthnAuthenticationRequestToken.class.isAssignableFrom(authentication);
    }
}
