package com.karamba121.backend.features.identity;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.access.SecurityAuditEventType;

@Service
public class PasskeyService {

    private final PublicKeyCredentialUserEntityRepository users;
    private final UserCredentialRepository credentials;
    private final IdentitySecurityAuditor auditor;

    public PasskeyService(
            PublicKeyCredentialUserEntityRepository users,
            UserCredentialRepository credentials,
            IdentitySecurityAuditor auditor) {
        this.users = users;
        this.credentials = credentials;
        this.auditor = auditor;
    }

    @Transactional(readOnly = true)
    public List<PasskeyView> list(String username) {
        PublicKeyCredentialUserEntity user = users.findByUsername(username);
        if (user == null) {
            return List.of();
        }
        return credentials.findByUserId(user.getId()).stream()
                .sorted(Comparator.comparing(CredentialRecord::getCreated).reversed())
                .map(PasskeyView::from)
                .toList();
    }

    @Transactional
    public void remove(String username, String encodedCredentialId) {
        Bytes credentialId;
        try {
            credentialId = Bytes.fromBase64(encodedCredentialId);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Passkey inválida");
        }
        PublicKeyCredentialUserEntity user = users.findByUsername(username);
        CredentialRecord credential = credentials.findByCredentialId(credentialId);
        if (user == null || credential == null || !user.getId().equals(credential.getUserEntityUserId())) {
            throw new IllegalArgumentException("Passkey inválida");
        }
        auditor.execute(username, SecurityAuditEventType.PASSKEY_REMOVED, () -> {
            credentials.delete(credentialId);
            return credentialId;
        });
    }

    public record PasskeyView(
            String id,
            String label,
            Instant createdAt,
            Instant lastUsedAt,
            boolean backupEligible,
            boolean backedUp) {

        private static PasskeyView from(CredentialRecord credential) {
            return new PasskeyView(
                    credential.getCredentialId().toBase64UrlString(),
                    credential.getLabel(),
                    credential.getCreated(),
                    credential.getLastUsed(),
                    credential.isBackupEligible(),
                    credential.isBackupState());
        }
    }
}
