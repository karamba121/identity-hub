package com.karamba121.backend.features.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "federated_identity", uniqueConstraints = {
        @UniqueConstraint(name = "uk_federated_identity_provider_subject",
                columnNames = { "provider_registration_id", "provider_subject" }),
        @UniqueConstraint(name = "uk_federated_identity_user_provider",
                columnNames = { "user_id", "provider_registration_id" })
})
public class FederatedIdentity {

    @Id
    @Column(length = 36, nullable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private IdentityUser user;

    @Column(name = "provider_registration_id", length = 100, nullable = false)
    private String providerRegistrationId;

    @Column(name = "provider_subject", length = 255, nullable = false)
    private String providerSubject;

    @Column(name = "email_at_link", length = 254, nullable = false)
    private String emailAtLink;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected FederatedIdentity() {
    }

    public FederatedIdentity(
            IdentityUser user,
            String providerRegistrationId,
            String providerSubject,
            String emailAtLink) {
        this.id = UUID.randomUUID().toString();
        this.user = user;
        this.providerRegistrationId = providerRegistrationId;
        this.providerSubject = providerSubject;
        this.emailAtLink = emailAtLink;
        this.createdAt = Instant.now();
    }

    public void registerLogin(Instant now) {
        this.lastLoginAt = now;
    }

    public String getId() { return id; }
    public IdentityUser getUser() { return user; }
    public String getProviderRegistrationId() { return providerRegistrationId; }
    public String getProviderSubject() { return providerSubject; }
    public String getEmailAtLink() { return emailAtLink; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
}
