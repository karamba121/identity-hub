CREATE TABLE permission_definition (
    code varchar(100) PRIMARY KEY,
    display_name varchar(160) NOT NULL,
    description varchar(500) NOT NULL,
    category varchar(40) NOT NULL,
    sort_order integer NOT NULL,
    CONSTRAINT uk_permission_definition_sort_order UNIQUE (sort_order),
    CONSTRAINT ck_permission_definition_category CHECK (
        category IN ('TENANT_ACCESS', 'OAUTH_CLIENTS', 'SECURITY_AUDIT')
    )
);

INSERT INTO permission_definition (code, display_name, description, category, sort_order) VALUES
    ('tenant.access.read', 'Consultar acessos do tenant',
     'Permite consultar memberships e papéis pertencentes ao tenant autorizado.', 'TENANT_ACCESS', 10),
    ('tenant.access.manage', 'Gerenciar acessos do tenant',
     'Permite criar, alterar e suspender memberships e seus papéis no tenant autorizado.', 'TENANT_ACCESS', 20),
    ('oauth.clients.read', 'Consultar clientes OAuth',
     'Permite consultar clientes OAuth pertencentes ao tenant autorizado.', 'OAUTH_CLIENTS', 30),
    ('oauth.clients.manage', 'Gerenciar clientes OAuth',
     'Permite criar e alterar clientes OAuth pertencentes ao tenant autorizado.', 'OAUTH_CLIENTS', 40),
    ('security.audit.read', 'Consultar auditoria de segurança',
     'Permite consultar eventos de segurança pertencentes ao tenant autorizado.', 'SECURITY_AUDIT', 50);
