CREATE TABLE user_entities (
    id VARCHAR(1000) PRIMARY KEY,
    name VARCHAR(254) NOT NULL UNIQUE,
    display_name VARCHAR(200),
    CONSTRAINT fk_user_entities_identity
        FOREIGN KEY (name) REFERENCES identity_user(email) ON DELETE CASCADE
);

CREATE TABLE user_credentials (
    credential_id VARCHAR(1000) PRIMARY KEY,
    user_entity_user_id VARCHAR(1000) NOT NULL,
    public_key BYTEA NOT NULL,
    signature_count BIGINT,
    uv_initialized BOOLEAN,
    backup_eligible BOOLEAN NOT NULL,
    authenticator_transports VARCHAR(1000),
    public_key_credential_type VARCHAR(100),
    backup_state BOOLEAN NOT NULL,
    attestation_object BYTEA,
    attestation_client_data_json BYTEA,
    created TIMESTAMP NOT NULL,
    last_used TIMESTAMP NOT NULL,
    label VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_user_credentials_entity
        FOREIGN KEY (user_entity_user_id) REFERENCES user_entities(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_credentials_entity
    ON user_credentials (user_entity_user_id, created DESC);
