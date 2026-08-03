CREATE TABLE security_audit_event (
    id varchar(36) NOT NULL PRIMARY KEY,
    occurred_at timestamp with time zone NOT NULL,
    event_type varchar(80) NOT NULL,
    result varchar(20) NOT NULL,
    reason_code varchar(50),
    actor_id varchar(100) NOT NULL,
    tenant_id varchar(100) NOT NULL,
    target_type varchar(50) NOT NULL,
    target_id varchar(200) NOT NULL,
    correlation_id varchar(36) NOT NULL,
    CONSTRAINT ck_security_audit_result CHECK (result IN ('SUCCEEDED', 'DENIED', 'FAILED'))
);

CREATE INDEX idx_security_audit_tenant_occurred
    ON security_audit_event (tenant_id, occurred_at DESC, id DESC);

CREATE INDEX idx_security_audit_actor_occurred
    ON security_audit_event (actor_id, occurred_at DESC);
