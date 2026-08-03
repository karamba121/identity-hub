package com.karamba121.backend.features.access;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityAuditFailureRecorder {

    private final SecurityAuditEventRepository events;

    public SecurityAuditFailureRecorder(SecurityAuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(SecurityAuditEvent event) {
        events.save(event);
    }
}
