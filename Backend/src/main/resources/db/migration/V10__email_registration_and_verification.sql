ALTER TABLE identity_user
    ADD COLUMN email_verified boolean NOT NULL DEFAULT true;

CREATE TABLE email_verification_token (
    id varchar(36) PRIMARY KEY,
    user_id varchar(36) NOT NULL,
    token_hash varchar(64) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone DEFAULT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_email_verification_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_token_user FOREIGN KEY (user_id)
        REFERENCES identity_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_token_user
    ON email_verification_token (user_id, expires_at);
