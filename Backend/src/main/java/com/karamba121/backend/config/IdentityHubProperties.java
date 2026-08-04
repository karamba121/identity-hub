package com.karamba121.backend.config;

import java.time.Duration;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "identity-hub")
public record IdentityHubProperties(
        String issuer,
        String uiBaseUrl,
        Duration interactionTtl,
        Registration registration,
        PasswordRecovery passwordRecovery,
        LoginProtection loginProtection,
        AbuseProtection abuseProtection,
        AuditRetention auditRetention,
        Observability observability,
        WebAuthn webauthn,
        Bootstrap bootstrap) {

    public record Registration(
            Duration verificationTtl,
            String mailFrom) {
    }

    public record PasswordRecovery(Duration tokenTtl) {
    }

    public record LoginProtection(
            int failureThreshold,
            Duration initialLockDuration,
            Duration maximumLockDuration) {
    }

    public record AbuseProtection(
            Duration window,
            int subjectLimit,
            int originLimit,
            int combinationLimit,
            int maximumBuckets) {
    }

    public record AuditRetention(
            boolean enabled,
            Duration retention,
            int batchSize,
            Duration initialDelay,
            Duration interval) {
    }

    public record Observability(Metrics metrics) {
    }

    public record Metrics(
            String token,
            String tokenLocation) {
    }

    public record WebAuthn(
            String rpId,
            String rpName,
            Set<String> allowedOrigins) {
    }

    public record Bootstrap(
            boolean enabled,
            String userEmail,
            String userPassword,
            String userName,
            String clientId,
            String clientName,
            String redirectUri,
            String postLogoutRedirectUri,
            String tenantSlug,
            String tenantName) {
    }
}
