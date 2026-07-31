CREATE TABLE identity_user (
    id varchar(36) PRIMARY KEY,
    email varchar(254) NOT NULL,
    display_name varchar(200) NOT NULL,
    password_hash varchar(200) NOT NULL,
    enabled boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_identity_user_email UNIQUE (email)
);

CREATE TABLE oauth2_registered_client (
    id varchar(100) NOT NULL PRIMARY KEY,
    client_id varchar(100) NOT NULL,
    client_id_issued_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret varchar(200) DEFAULT NULL,
    client_secret_expires_at timestamp with time zone DEFAULT NULL,
    client_name varchar(200) NOT NULL,
    client_authentication_methods varchar(1000) NOT NULL,
    authorization_grant_types varchar(1000) NOT NULL,
    redirect_uris varchar(1000) DEFAULT NULL,
    post_logout_redirect_uris varchar(1000) DEFAULT NULL,
    scopes varchar(1000) NOT NULL,
    client_settings varchar(2000) NOT NULL,
    token_settings varchar(2000) NOT NULL,
    CONSTRAINT uk_oauth2_registered_client_client_id UNIQUE (client_id)
);

CREATE TABLE oauth2_authorization (
    id varchar(100) NOT NULL PRIMARY KEY,
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorization_grant_type varchar(100) NOT NULL,
    authorized_scopes varchar(1000) DEFAULT NULL,
    attributes text DEFAULT NULL,
    state varchar(500) DEFAULT NULL,
    authorization_code_value text DEFAULT NULL,
    authorization_code_issued_at timestamp with time zone DEFAULT NULL,
    authorization_code_expires_at timestamp with time zone DEFAULT NULL,
    authorization_code_metadata text DEFAULT NULL,
    access_token_value text DEFAULT NULL,
    access_token_issued_at timestamp with time zone DEFAULT NULL,
    access_token_expires_at timestamp with time zone DEFAULT NULL,
    access_token_metadata text DEFAULT NULL,
    access_token_type varchar(100) DEFAULT NULL,
    access_token_scopes varchar(1000) DEFAULT NULL,
    oidc_id_token_value text DEFAULT NULL,
    oidc_id_token_issued_at timestamp with time zone DEFAULT NULL,
    oidc_id_token_expires_at timestamp with time zone DEFAULT NULL,
    oidc_id_token_metadata text DEFAULT NULL,
    refresh_token_value text DEFAULT NULL,
    refresh_token_issued_at timestamp with time zone DEFAULT NULL,
    refresh_token_expires_at timestamp with time zone DEFAULT NULL,
    refresh_token_metadata text DEFAULT NULL,
    user_code_value text DEFAULT NULL,
    user_code_issued_at timestamp with time zone DEFAULT NULL,
    user_code_expires_at timestamp with time zone DEFAULT NULL,
    user_code_metadata text DEFAULT NULL,
    device_code_value text DEFAULT NULL,
    device_code_issued_at timestamp with time zone DEFAULT NULL,
    device_code_expires_at timestamp with time zone DEFAULT NULL,
    device_code_metadata text DEFAULT NULL,
    CONSTRAINT fk_oauth2_authorization_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client (id)
);

CREATE INDEX idx_oauth2_authorization_state ON oauth2_authorization (state);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id varchar(100) NOT NULL,
    principal_name varchar(200) NOT NULL,
    authorities varchar(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name),
    CONSTRAINT fk_oauth2_consent_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client (id)
);

CREATE TABLE authorization_interaction (
    id_hash varchar(64) NOT NULL PRIMARY KEY,
    session_id_hash varchar(64) NOT NULL,
    interaction_type varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    principal_name varchar(200) DEFAULT NULL,
    client_id varchar(100) NOT NULL,
    requested_scopes varchar(1000) NOT NULL,
    oauth_state varchar(500) DEFAULT NULL,
    resume_uri text DEFAULT NULL,
    redirect_uri text DEFAULT NULL,
    expires_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone DEFAULT NULL
);

CREATE INDEX idx_authorization_interaction_expires_at
    ON authorization_interaction (expires_at);
