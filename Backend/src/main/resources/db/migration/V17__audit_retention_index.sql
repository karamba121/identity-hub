CREATE INDEX idx_security_audit_retention
    ON security_audit_event (occurred_at ASC, id ASC);
