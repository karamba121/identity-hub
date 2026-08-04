package com.karamba121.backend.features.scim;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.karamba121.backend.features.access.SecurityAuditEvent;
import com.karamba121.backend.features.access.SecurityAuditEventRepository;
import com.karamba121.backend.features.access.SecurityAuditEventType;
import com.karamba121.backend.features.access.SecurityAuditFailureRecorder;
import com.karamba121.backend.features.access.SecurityAuditResult;

@Service
public class ScimAuditService {

    private final ScimClientAuthorizer authorizer;
    private final SecurityAuditEventRepository events;
    private final SecurityAuditFailureRecorder failures;

    public ScimAuditService(
            ScimClientAuthorizer authorizer,
            SecurityAuditEventRepository events,
            SecurityAuditFailureRecorder failures) {
        this.authorizer = authorizer;
        this.events = events;
        this.failures = failures;
    }

    public <T> T execute(
            String clientId,
            String tenantId,
            SecurityAuditEventType eventType,
            String targetId,
            Supplier<T> operation) {
        String actor = "client:" + clientId;
        String correlationId = UUID.randomUUID().toString();
        try {
            authorizer.require(clientId, tenantId);
            T result = operation.get();
            events.save(SecurityAuditEvent.succeeded(
                    eventType, actor, tenantId, "SCIM_USER", targetId, correlationId));
            return result;
        } catch (RuntimeException exception) {
            SecurityAuditResult result = exception instanceof AccessDeniedException
                    ? SecurityAuditResult.DENIED
                    : SecurityAuditResult.FAILED;
            String reason = exception instanceof AccessDeniedException
                    ? "CLIENT_TENANT_MISMATCH"
                    : exception instanceof ScimException scim && scim.status().value() == 409
                            ? "CONFLICT"
                            : "VALIDATION_ERROR";
            failures.record(SecurityAuditEvent.failed(
                    eventType, result, reason, actor, tenantId, "SCIM_USER", targetId, correlationId));
            throw exception;
        }
    }
}
