package com.karamba121.backend.features.access;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface SecurityAuditEventRepository extends Repository<SecurityAuditEvent, String> {

    SecurityAuditEvent save(SecurityAuditEvent event);

    Page<SecurityAuditEvent> findAllByTenantId(String tenantId, Pageable pageable);

    Page<SecurityAuditEvent> findAllByTargetTypeAndTargetIdAndEventTypeIn(
            String targetType,
            String targetId,
            Collection<SecurityAuditEventType> eventTypes,
            Pageable pageable);
}
