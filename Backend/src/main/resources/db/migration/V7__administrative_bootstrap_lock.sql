CREATE TABLE administrative_bootstrap_lock (
    lock_name varchar(80) NOT NULL PRIMARY KEY,
    created_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    tenant_id varchar(36),
    user_id varchar(36),
    CONSTRAINT fk_administrative_bootstrap_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant (id),
    CONSTRAINT fk_administrative_bootstrap_user FOREIGN KEY (user_id)
        REFERENCES identity_user (id),
    CONSTRAINT ck_administrative_bootstrap_completion CHECK (
        (completed_at IS NULL AND tenant_id IS NULL AND user_id IS NULL)
        OR
        (completed_at IS NOT NULL AND tenant_id IS NOT NULL AND user_id IS NOT NULL)
    )
);

INSERT INTO administrative_bootstrap_lock (lock_name, created_at)
VALUES ('first-administrator', CURRENT_TIMESTAMP);
