CREATE TABLE tenant_role (
    id varchar(36) NOT NULL PRIMARY KEY,
    tenant_id varchar(36) NOT NULL,
    code varchar(80) NOT NULL,
    display_name varchar(160) NOT NULL,
    system_role boolean NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_tenant_role_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant (id),
    CONSTRAINT uk_tenant_role_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT uk_tenant_role_id_tenant UNIQUE (id, tenant_id)
);

CREATE TABLE tenant_role_permission (
    role_id varchar(36) NOT NULL,
    permission_code varchar(100) NOT NULL,
    PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_tenant_role_permission_role FOREIGN KEY (role_id)
        REFERENCES tenant_role (id),
    CONSTRAINT fk_tenant_role_permission_definition FOREIGN KEY (permission_code)
        REFERENCES permission_definition (code)
);

ALTER TABLE tenant_membership
    ADD COLUMN role_id varchar(36);

ALTER TABLE tenant_membership
    ADD CONSTRAINT uk_tenant_membership_id_tenant UNIQUE (id, tenant_id);

ALTER TABLE tenant_membership
    ADD CONSTRAINT fk_tenant_membership_role_same_tenant
    FOREIGN KEY (role_id, tenant_id) REFERENCES tenant_role (id, tenant_id);

CREATE INDEX idx_tenant_role_tenant
    ON tenant_role (tenant_id, code);

CREATE INDEX idx_tenant_membership_role
    ON tenant_membership (tenant_id, role_id, status);
