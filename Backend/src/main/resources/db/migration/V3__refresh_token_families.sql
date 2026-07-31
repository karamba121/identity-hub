CREATE TABLE oauth_refresh_token_family (
    id varchar(36) NOT NULL PRIMARY KEY,
    authorization_id varchar(100) NOT NULL,
    current_token_hash varchar(64) NOT NULL,
    status varchar(20) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamp with time zone NOT NULL,
    last_rotated_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone DEFAULT NULL,
    CONSTRAINT uk_refresh_token_family_authorization UNIQUE (authorization_id)
);

CREATE TABLE oauth_refresh_token_history (
    token_hash varchar(64) NOT NULL PRIMARY KEY,
    family_id varchar(36) NOT NULL,
    status varchar(20) NOT NULL,
    issued_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone DEFAULT NULL,
    CONSTRAINT fk_refresh_token_history_family FOREIGN KEY (family_id)
        REFERENCES oauth_refresh_token_family (id)
);

CREATE INDEX idx_refresh_token_history_family ON oauth_refresh_token_history (family_id);
