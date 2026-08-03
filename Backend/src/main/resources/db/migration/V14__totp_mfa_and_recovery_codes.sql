CREATE TABLE user_mfa (
    user_id varchar(36) PRIMARY KEY REFERENCES identity_user(id) ON DELETE CASCADE,
    encrypted_secret varchar(512) NOT NULL,
    enabled_at timestamp with time zone,
    last_used_step bigint,
    created_at timestamp with time zone NOT NULL
);

CREATE TABLE mfa_recovery_code (
    id varchar(36) PRIMARY KEY,
    user_id varchar(36) NOT NULL REFERENCES identity_user(id) ON DELETE CASCADE,
    code_hash varchar(64) NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_mfa_recovery_code_hash UNIQUE (code_hash)
);

CREATE INDEX idx_mfa_recovery_code_user_available
    ON mfa_recovery_code (user_id, used_at);
