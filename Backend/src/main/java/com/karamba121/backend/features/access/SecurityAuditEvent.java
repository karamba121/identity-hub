package com.karamba121.backend.features.access;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name = "security_audit_event")
public class SecurityAuditEvent {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 80, nullable = false, updatable = false)
    private SecurityAuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false, updatable = false)
    private SecurityAuditResult result;

    @Column(name = "reason_code", length = 50, updatable = false)
    private String reasonCode;

    @Column(name = "actor_id", length = 100, nullable = false, updatable = false)
    private String actorId;

    @Column(name = "tenant_id", length = 100, updatable = false)
    private String tenantId;

    @Column(name = "target_type", length = 50, nullable = false, updatable = false)
    private String targetType;

    @Column(name = "target_id", length = 200, nullable = false, updatable = false)
    private String targetId;

    @Column(name = "correlation_id", length = 36, nullable = false, updatable = false)
    private String correlationId;

    protected SecurityAuditEvent() {
    }

    private SecurityAuditEvent(
            SecurityAuditEventType eventType,
            SecurityAuditResult result,
            String reasonCode,
            String actorId,
            String tenantId,
            String targetType,
            String targetId,
            String correlationId) {
        this.id = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
        this.eventType = eventType;
        this.result = result;
        this.reasonCode = reasonCode;
        this.actorId = required(actorId, "<unknown-actor>");
        this.tenantId = optional(tenantId);
        this.targetType = required(targetType, "UNKNOWN");
        this.targetId = required(targetId, "<unknown-target>");
        this.correlationId = correlationId;
    }

    public static SecurityAuditEvent succeeded(
            SecurityAuditEventType eventType,
            String actorId,
            String tenantId,
            String targetType,
            String targetId,
            String correlationId) {
        return new SecurityAuditEvent(
                eventType, SecurityAuditResult.SUCCEEDED, null,
                actorId, tenantId, targetType, targetId, correlationId);
    }

    public static SecurityAuditEvent failed(
            SecurityAuditEventType eventType,
            SecurityAuditResult result,
            String reasonCode,
            String actorId,
            String tenantId,
            String targetType,
            String targetId,
            String correlationId) {
        return new SecurityAuditEvent(
                eventType, result, reasonCode,
                actorId, tenantId, targetType, targetId, correlationId);
    }

    private static String required(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public String getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public SecurityAuditEventType getEventType() {
        return eventType;
    }

    public SecurityAuditResult getResult() {
        return result;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public String getActorId() {
        return actorId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getCorrelationId() {
        return correlationId;
    }
}
