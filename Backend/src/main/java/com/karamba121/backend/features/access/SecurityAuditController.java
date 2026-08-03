package com.karamba121.backend.features.access;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/tenants/{tenantId}/audit-events")
public class SecurityAuditController {

    private final TenantPermissionAuthorizer authorizer;
    private final SecurityAuditEventRepository events;

    public SecurityAuditController(
            TenantPermissionAuthorizer authorizer,
            SecurityAuditEventRepository events) {
        this.authorizer = authorizer;
        this.events = events;
    }

    @GetMapping
    AuditPage list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String tenantId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        authorizer.require(jwt.getSubject(), tenantId, PermissionCode.SECURITY_AUDIT_READ);
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<SecurityAuditEvent> result = events.findAllByTenantId(
                tenantId,
                PageRequest.of(
                        safePage,
                        safeSize,
                        Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id"))));
        return new AuditPage(
                result.getContent().stream().map(AuditEventView::from).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    record AuditPage(
            List<AuditEventView> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    record AuditEventView(
            String id,
            Instant occurredAt,
            AdministrativeAuditEventType eventType,
            SecurityAuditResult result,
            String reasonCode,
            String actorId,
            String targetType,
            String targetId,
            String correlationId) {

        static AuditEventView from(SecurityAuditEvent event) {
            return new AuditEventView(
                    event.getId(),
                    event.getOccurredAt(),
                    event.getEventType(),
                    event.getResult(),
                    event.getReasonCode(),
                    event.getActorId(),
                    event.getTargetType(),
                    event.getTargetId(),
                    event.getCorrelationId());
        }
    }
}
