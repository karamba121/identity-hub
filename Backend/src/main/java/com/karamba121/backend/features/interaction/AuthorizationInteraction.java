package com.karamba121.backend.features.interaction;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "authorization_interaction")
public class AuthorizationInteraction {

    @Id
    @Column(name = "id_hash", length = 64, nullable = false)
    private String idHash;

    @Column(name = "session_id_hash", length = 64, nullable = false)
    private String sessionIdHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", length = 20, nullable = false)
    private InteractionType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private InteractionStatus status;

    @Column(name = "principal_name", length = 200)
    private String principalName;

    @Column(name = "client_id", length = 100, nullable = false)
    private String clientId;

    @Column(name = "requested_scopes", length = 1000, nullable = false)
    private String requestedScopes;

    @Column(name = "oauth_state", length = 500)
    private String oauthState;

    @Column(name = "resume_uri", columnDefinition = "text")
    private String resumeUri;

    @Column(name = "redirect_uri", columnDefinition = "text")
    private String redirectUri;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected AuthorizationInteraction() {
    }

    AuthorizationInteraction(
            String idHash,
            String sessionIdHash,
            InteractionType type,
            String principalName,
            String clientId,
            String requestedScopes,
            String oauthState,
            String resumeUri,
            String redirectUri,
            Instant expiresAt) {
        this.idHash = idHash;
        this.sessionIdHash = sessionIdHash;
        this.type = type;
        this.status = InteractionStatus.PENDING;
        this.principalName = principalName;
        this.clientId = clientId;
        this.requestedScopes = requestedScopes;
        this.oauthState = oauthState;
        this.resumeUri = resumeUri;
        this.redirectUri = redirectUri;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public String getIdHash() {
        return idHash;
    }

    public String getSessionIdHash() {
        return sessionIdHash;
    }

    public InteractionType getType() {
        return type;
    }

    public InteractionStatus getStatus() {
        return status;
    }

    public String getClientId() {
        return clientId;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public String getRequestedScopes() {
        return requestedScopes;
    }

    public String getOauthState() {
        return oauthState;
    }

    public String getResumeUri() {
        return resumeUri;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void rebindSession(String sessionIdHash) {
        this.sessionIdHash = sessionIdHash;
    }

    public void approve() {
        this.status = InteractionStatus.APPROVED;
    }

    public void complete() {
        this.status = InteractionStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void deny() {
        this.status = InteractionStatus.DENIED;
        this.completedAt = Instant.now();
    }
}
