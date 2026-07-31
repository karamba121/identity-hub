CREATE TABLE tenant (
    id varchar(36) PRIMARY KEY,
    slug varchar(100) NOT NULL,
    display_name varchar(200) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT uk_tenant_slug UNIQUE (slug),
    CONSTRAINT ck_tenant_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE TABLE tenant_membership (
    id varchar(36) PRIMARY KEY,
    tenant_id varchar(36) NOT NULL,
    user_id varchar(36) NOT NULL,
    status varchar(20) NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT fk_tenant_membership_tenant FOREIGN KEY (tenant_id)
        REFERENCES tenant (id),
    CONSTRAINT fk_tenant_membership_user FOREIGN KEY (user_id)
        REFERENCES identity_user (id),
    CONSTRAINT uk_tenant_membership_tenant_user UNIQUE (tenant_id, user_id),
    CONSTRAINT ck_tenant_membership_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX idx_tenant_membership_user_status
    ON tenant_membership (user_id, status);

CREATE INDEX idx_tenant_membership_tenant_status
    ON tenant_membership (tenant_id, status);
