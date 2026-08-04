package com.karamba121.backend.features.identity;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.karamba121.backend.features.access.SecurityAuditEventType;

@Service
public class FederatedIdentityService {

    private final IdentityUserRepository users;
    private final FederatedIdentityRepository identities;
    private final PasswordEncoder passwordEncoder;
    private final IdentitySecurityAuditor auditor;

    public FederatedIdentityService(
            IdentityUserRepository users,
            FederatedIdentityRepository identities,
            PasswordEncoder passwordEncoder,
            IdentitySecurityAuditor auditor) {
        this.users = users;
        this.identities = identities;
        this.passwordEncoder = passwordEncoder;
        this.auditor = auditor;
    }

    @Transactional
    public IdentityUser authenticate(String registrationId, OidcUser oidcUser, String linkingEmail) {
        ExternalClaims claims = requireClaims(oidcUser);
        FederatedIdentity existing = identities
                .findByProviderRegistrationIdAndProviderSubject(registrationId, claims.subject())
                .orElse(null);
        if (existing != null) {
            if (linkingEmail != null && !existing.getUser().getEmail().equalsIgnoreCase(linkingEmail)) {
                throw new IllegalArgumentException("Identidade externa já vinculada");
            }
            return registerLogin(existing);
        }

        IdentityUser user;
        if (StringUtils.hasText(linkingEmail)) {
            user = users.findByEmailForUpdate(normalizeEmail(linkingEmail))
                    .orElseThrow(() -> new IllegalArgumentException("Identidade local não encontrada"));
            if (identities.existsByUserIdAndProviderRegistrationId(user.getId(), registrationId)) {
                throw new IllegalArgumentException("Provedor já vinculado à identidade");
            }
        } else {
            if (users.findByEmailIgnoreCase(claims.email()).isPresent()) {
                throw new IllegalArgumentException("Vínculo explícito necessário para esta conta");
            }
            String unusablePassword = passwordEncoder.encode(UUID.randomUUID() + "-federated-only");
            user = users.save(IdentityUser.federated(
                    claims.email(), claims.displayName(), unusablePassword));
        }

        IdentityUser linkedUser = user;
        FederatedIdentity identity = auditor.execute(
                user.getEmail(),
                SecurityAuditEventType.FEDERATED_IDENTITY_LINKED,
                () -> identities.save(new FederatedIdentity(
                        linkedUser, registrationId, claims.subject(), claims.email())));
        return registerLogin(identity);
    }

    @Transactional(readOnly = true)
    public List<FederatedIdentityView> list(String email) {
        return identities.findAllByUserEmailIgnoreCaseOrderByCreatedAtDesc(email).stream()
                .map(FederatedIdentityView::from)
                .toList();
    }

    @Transactional
    public void unlink(String email, String id) {
        FederatedIdentity identity = identities.findByIdAndUserEmailIgnoreCase(id, email)
                .orElseThrow(() -> new IllegalArgumentException("Identidade federada inválida"));
        IdentityUser user = identity.getUser();
        if (!user.hasLocalCredentials() && identities.countByUserId(user.getId()) <= 1) {
            throw new IllegalStateException("Cadastre uma senha ou outro provedor antes de remover este vínculo");
        }
        auditor.execute(email, SecurityAuditEventType.FEDERATED_IDENTITY_UNLINKED, () -> {
            identities.delete(identity);
            return identity.getId();
        });
    }

    private IdentityUser registerLogin(FederatedIdentity identity) {
        IdentityUser user = identity.getUser();
        identity.registerLogin(Instant.now());
        identities.save(identity);
        return user;
    }

    @Transactional
    public void recordSuccessfulAuthentication(String email) {
        auditor.execute(email, SecurityAuditEventType.FEDERATED_AUTHENTICATION_SUCCEEDED, () -> email);
    }

    private static ExternalClaims requireClaims(OidcUser user) {
        String subject = normalize(user.getSubject());
        String email = normalizeEmail(user.getEmail());
        Object verified = user.getClaim("email_verified");
        if (subject == null || email == null || !Boolean.TRUE.equals(verified)) {
            throw new IllegalArgumentException("O provedor deve confirmar sujeito e e-mail verificado");
        }
        String displayName = normalize(user.getFullName());
        if (displayName == null) displayName = email;
        if (displayName.length() > 200) displayName = displayName.substring(0, 200);
        return new ExternalClaims(subject, email, displayName);
    }

    private static String normalizeEmail(String value) {
        String normalized = normalize(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ExternalClaims(String subject, String email, String displayName) { }

    public record FederatedIdentityView(
            String id,
            String provider,
            String emailAtLink,
            Instant createdAt,
            Instant lastLoginAt) {

        private static FederatedIdentityView from(FederatedIdentity identity) {
            return new FederatedIdentityView(
                    identity.getId(),
                    identity.getProviderRegistrationId(),
                    identity.getEmailAtLink(),
                    identity.getCreatedAt(),
                    identity.getLastLoginAt());
        }
    }
}
