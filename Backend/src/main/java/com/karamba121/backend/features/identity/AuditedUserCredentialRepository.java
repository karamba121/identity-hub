package com.karamba121.backend.features.identity;

import java.util.List;

import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.access.SecurityAuditEventType;

public class AuditedUserCredentialRepository implements UserCredentialRepository {

    private final UserCredentialRepository delegate;
    private final PublicKeyCredentialUserEntityRepository users;
    private final IdentitySecurityAuditor auditor;

    public AuditedUserCredentialRepository(
            UserCredentialRepository delegate,
            PublicKeyCredentialUserEntityRepository users,
            IdentitySecurityAuditor auditor) {
        this.delegate = delegate;
        this.users = users;
        this.auditor = auditor;
    }

    @Override
    public void delete(Bytes credentialId) {
        delegate.delete(credentialId);
    }

    @Override
    @Transactional
    public void save(CredentialRecord credential) {
        if (delegate.findByCredentialId(credential.getCredentialId()) != null) {
            delegate.save(credential);
            return;
        }
        PublicKeyCredentialUserEntity user = users.findById(credential.getUserEntityUserId());
        if (user == null) {
            throw new IllegalStateException("Identidade da passkey não encontrada");
        }
        auditor.execute(user.getName(), SecurityAuditEventType.PASSKEY_REGISTERED, () -> {
            delegate.save(credential);
            return credential;
        });
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credentialId) {
        return delegate.findByCredentialId(credentialId);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return delegate.findByUserId(userId);
    }
}
