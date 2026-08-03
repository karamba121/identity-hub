ALTER TABLE security_audit_event
    ALTER COLUMN tenant_id DROP NOT NULL;

CREATE INDEX idx_security_audit_target_occurred
    ON security_audit_event (target_type, target_id, occurred_at DESC, id DESC);
