ALTER TABLE identity_user
    ADD COLUMN local_credentials_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE federated_identity (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    provider_registration_id VARCHAR(100) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email_at_link VARCHAR(254) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_login_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_federated_identity_user
        FOREIGN KEY (user_id) REFERENCES identity_user(id) ON DELETE CASCADE,
    CONSTRAINT uk_federated_identity_provider_subject
        UNIQUE (provider_registration_id, provider_subject),
    CONSTRAINT uk_federated_identity_user_provider
        UNIQUE (user_id, provider_registration_id)
);

CREATE INDEX idx_federated_identity_user
    ON federated_identity (user_id, created_at DESC);
