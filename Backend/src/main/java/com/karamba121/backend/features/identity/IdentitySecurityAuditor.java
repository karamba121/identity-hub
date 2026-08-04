package com.karamba121.backend.features.identity;

import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.karamba121.backend.features.access.SecurityAuditEvent;
import com.karamba121.backend.features.access.SecurityAuditEventRepository;
import com.karamba121.backend.features.access.SecurityAuditEventType;
import com.karamba121.backend.features.access.SecurityAuditFailureRecorder;
import com.karamba121.backend.features.access.SecurityAuditResult;
import com.karamba121.backend.features.abuse.RateLimitExceededException;

@Service
public class IdentitySecurityAuditor {

    static final String TARGET_TYPE = "IDENTITY_USER";
    private static final EnumSet<SecurityAuditEventType> MFA_EVENTS = EnumSet.of(
            SecurityAuditEventType.MFA_ENROLLMENT_STARTED,
            SecurityAuditEventType.MFA_ENABLED,
            SecurityAuditEventType.MFA_RECOVERY_CODES_REGENERATED,
            SecurityAuditEventType.MFA_DISABLED,
            SecurityAuditEventType.MFA_CHALLENGE_SUCCEEDED,
            SecurityAuditEventType.MFA_CHALLENGE_FAILED,
            SecurityAuditEventType.PASSKEY_REGISTERED,
            SecurityAuditEventType.PASSKEY_REMOVED,
            SecurityAuditEventType.PASSKEY_AUTHENTICATION_SUCCEEDED);

    private final IdentityUserRepository users;
    private final SecurityAuditEventRepository events;
    private final SecurityAuditFailureRecorder failureRecorder;

    public IdentitySecurityAuditor(
            IdentityUserRepository users,
            SecurityAuditEventRepository events,
            SecurityAuditFailureRecorder failureRecorder) {
        this.users = users;
        this.events = events;
        this.failureRecorder = failureRecorder;
    }

    @Transactional
    public <T> T execute(String email, SecurityAuditEventType eventType, Supplier<T> operation) {
        String userId = requireUserId(email);
        String correlationId = UUID.randomUUID().toString();
        try {
            T result = operation.get();
            events.save(SecurityAuditEvent.succeeded(
                    eventType, userId, null, TARGET_TYPE, userId, correlationId));
            return result;
        } catch (RuntimeException exception) {
            failureRecorder.record(SecurityAuditEvent.failed(
                    eventType,
                    SecurityAuditResult.FAILED,
                    reason(exception),
                    userId,
                    null,
                    TARGET_TYPE,
                    userId,
                    correlationId));
            throw exception;
        }
    }

    @Transactional
    public boolean verifyChallenge(String email, Supplier<Boolean> verification) {
        String userId = requireUserId(email);
        String correlationId = UUID.randomUUID().toString();
        boolean accepted = verification.get();
        events.save(accepted
                ? SecurityAuditEvent.succeeded(
                        SecurityAuditEventType.MFA_CHALLENGE_SUCCEEDED,
                        userId, null, TARGET_TYPE, userId, correlationId)
                : SecurityAuditEvent.failed(
                        SecurityAuditEventType.MFA_CHALLENGE_FAILED,
                        SecurityAuditResult.FAILED,
                        "INVALID_FACTOR",
                        userId, null, TARGET_TYPE, userId, correlationId));
        return accepted;
    }

    public void recordChallengeFailure(String email, String reasonCode) {
        String userId = requireUserId(email);
        failureRecorder.record(SecurityAuditEvent.failed(
                SecurityAuditEventType.MFA_CHALLENGE_FAILED,
                SecurityAuditResult.FAILED,
                reasonCode,
                userId,
                null,
                TARGET_TYPE,
                userId,
                UUID.randomUUID().toString()));
    }

    @Transactional(readOnly = true)
    public Page<SecurityAuditEvent> history(String email, int page, int size) {
        String userId = requireUserId(email);
        return events.findAllByTargetTypeAndTargetIdAndEventTypeIn(
                TARGET_TYPE,
                userId,
                MFA_EVENTS,
                PageRequest.of(
                        Math.max(page, 0),
                        Math.min(Math.max(size, 1), 50),
                        Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
    }

    private String requireUserId(String email) {
        return users.findByEmailIgnoreCase(email)
                .map(IdentityUser::getId)
                .orElseThrow(() -> new IllegalArgumentException("Identidade não encontrada"));
    }

    private static String reason(RuntimeException exception) {
        if (exception instanceof RateLimitExceededException) return "RATE_LIMITED";
        if (exception instanceof IllegalArgumentException) return "INVALID_FACTOR";
        if (exception instanceof IllegalStateException) return "STATE_CONFLICT";
        return "UNEXPECTED_ERROR";
    }
}
