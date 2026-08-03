CREATE TABLE tenant_oauth_client (
    registered_client_id varchar(100) NOT NULL PRIMARY KEY,
    tenant_id varchar(36) NOT NULL,
    client_id varchar(100) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_tenant_oauth_client_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant (id),
    CONSTRAINT fk_tenant_oauth_client_registered_client FOREIGN KEY (registered_client_id)
        REFERENCES oauth2_registered_client (id),
    CONSTRAINT uk_tenant_oauth_client_client_id UNIQUE (client_id)
);

CREATE INDEX idx_tenant_oauth_client_tenant
    ON tenant_oauth_client (tenant_id, client_id);
