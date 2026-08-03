package com.karamba121.backend.features.access;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdministrativeActionAuditor {

    private final TenantPermissionAuthorizer authorizer;
    private final SecurityAuditEventRepository events;
    private final SecurityAuditFailureRecorder failureRecorder;

    public AdministrativeActionAuditor(
            TenantPermissionAuthorizer authorizer,
            SecurityAuditEventRepository events,
            SecurityAuditFailureRecorder failureRecorder) {
        this.authorizer = authorizer;
        this.events = events;
        this.failureRecorder = failureRecorder;
    }

    @Transactional
    public <T> T execute(
            String actorId,
            String tenantId,
            PermissionCode permission,
            SecurityAuditEventType eventType,
            String targetType,
            String targetId,
            Supplier<T> operation) {
        String correlationId = UUID.randomUUID().toString();
        try {
            authorizer.require(actorId, tenantId, permission);
            T result = operation.get();
            events.save(SecurityAuditEvent.succeeded(
                    eventType, actorId, tenantId, targetType, targetId, correlationId));
            return result;
        } catch (RuntimeException exception) {
            Failure failure = failure(exception);
            failureRecorder.record(SecurityAuditEvent.failed(
                    eventType,
                    failure.result(),
                    failure.reasonCode(),
                    actorId,
                    tenantId,
                    targetType,
                    targetId,
                    correlationId));
            throw exception;
        }
    }

    private static Failure failure(RuntimeException exception) {
        if (exception instanceof AccessDeniedException) {
            return new Failure(SecurityAuditResult.DENIED, "MISSING_PERMISSION");
        }
        if (exception instanceof LastTenantAdministratorException) {
            return new Failure(SecurityAuditResult.FAILED, "LAST_ADMINISTRATOR");
        }
        if (exception instanceof TenantAdministrationResourceNotFoundException) {
            return new Failure(SecurityAuditResult.FAILED, "RESOURCE_NOT_FOUND");
        }
        if (exception instanceof OAuthClientAdministrationException oauthException) {
            return new Failure(
                    SecurityAuditResult.FAILED,
                    oauthException.isConflict() ? "CONFLICT" : "RESOURCE_NOT_FOUND");
        }
        if (exception instanceof IllegalArgumentException) {
            return new Failure(SecurityAuditResult.FAILED, "VALIDATION_ERROR");
        }
        if (exception instanceof DataIntegrityViolationException) {
            return new Failure(SecurityAuditResult.FAILED, "CONFLICT");
        }
        return new Failure(SecurityAuditResult.FAILED, "UNEXPECTED_ERROR");
    }

    private record Failure(SecurityAuditResult result, String reasonCode) {
    }
}
