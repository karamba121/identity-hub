ALTER TABLE identity_user
    ADD COLUMN credential_version bigint NOT NULL DEFAULT 0;

CREATE INDEX idx_oauth2_authorization_principal_name
    ON oauth2_authorization (principal_name);
