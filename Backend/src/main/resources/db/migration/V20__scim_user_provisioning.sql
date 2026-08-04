CREATE TABLE scim_user_resource (
    id varchar(36) NOT NULL PRIMARY KEY,
    tenant_id varchar(36) NOT NULL,
    membership_id varchar(36) NOT NULL,
    user_name varchar(254) NOT NULL,
    display_name varchar(200) NOT NULL,
    external_id varchar(200),
    version bigint NOT NULL,
    created_at timestamp with time zone NOT NULL,
    last_modified_at timestamp with time zone NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT fk_scim_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_scim_user_membership FOREIGN KEY (membership_id, tenant_id)
        REFERENCES tenant_membership (id, tenant_id),
    CONSTRAINT uk_scim_user_tenant_membership UNIQUE (tenant_id, membership_id),
    CONSTRAINT uk_scim_user_tenant_username UNIQUE (tenant_id, user_name),
    CONSTRAINT uk_scim_user_tenant_external_id UNIQUE (tenant_id, external_id),
    CONSTRAINT ck_scim_user_version CHECK (version > 0)
);

CREATE INDEX idx_scim_user_tenant_active
    ON scim_user_resource (tenant_id, deleted_at, user_name);
