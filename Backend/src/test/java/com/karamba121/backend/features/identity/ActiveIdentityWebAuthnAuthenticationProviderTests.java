package com.karamba121.backend.features.identity;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationRequestToken;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

class ActiveIdentityWebAuthnAuthenticationProviderTests {

    @Test
    void rejectsLockedIdentityBeforeCryptographicValidationOrCounterUpdate() {
        Bytes credentialId = Bytes.random();
        Bytes userId = Bytes.random();
        WebAuthnRelyingPartyOperations relyingParty = mock(WebAuthnRelyingPartyOperations.class);
        UserCredentialRepository credentials = mock(UserCredentialRepository.class);
        PublicKeyCredentialUserEntityRepository users =
                mock(PublicKeyCredentialUserEntityRepository.class);
        UserDetailsService userDetails = mock(UserDetailsService.class);
        CredentialRecord credential = mock(CredentialRecord.class);
        PublicKeyCredentialUserEntity userEntity = mock(PublicKeyCredentialUserEntity.class);
        RelyingPartyAuthenticationRequest request =
                mock(RelyingPartyAuthenticationRequest.class, RETURNS_DEEP_STUBS);

        when(request.getPublicKey().getRawId()).thenReturn(credentialId);
        when(credentials.findByCredentialId(credentialId)).thenReturn(credential);
        when(credential.getUserEntityUserId()).thenReturn(userId);
        when(users.findById(userId)).thenReturn(userEntity);
        when(userEntity.getName()).thenReturn("locked@example.test");
        when(userDetails.loadUserByUsername("locked@example.test")).thenReturn(
                User.withUsername("locked@example.test")
                        .password("unused")
                        .authorities("ROLE_USER")
                        .accountLocked(true)
                        .build());

        ActiveIdentityWebAuthnAuthenticationProvider provider =
                new ActiveIdentityWebAuthnAuthenticationProvider(
                        relyingParty, credentials, users, userDetails);

        assertThatThrownBy(() -> provider.authenticate(new WebAuthnAuthenticationRequestToken(request)))
                .isInstanceOf(LockedException.class);
        verifyNoInteractions(relyingParty);
    }
}
