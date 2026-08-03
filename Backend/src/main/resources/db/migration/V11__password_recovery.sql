CREATE TABLE password_recovery_token (
    id varchar(36) PRIMARY KEY,
    user_id varchar(36) NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone DEFAULT NULL,
    revoked_at timestamp with time zone DEFAULT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_password_recovery_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_recovery_token_user FOREIGN KEY (user_id)
        REFERENCES identity_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_password_recovery_token_user
    ON password_recovery_token (user_id, created_at);
