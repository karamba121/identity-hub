# ADR-017: Provisionamento SCIM isolado por tenant

- **Status:** aceito
- **Data:** 2026-08-04

## Contexto

Diretórios corporativos precisam criar, consultar, atualizar, suspender e
remover vínculos de usuários sem receber autoridade sobre papéis locais. O
protocolo deve manter interoperabilidade SCIM, mas também respeitar o modelo do
Identity Hub, no qual a identidade é global e a membership pertence ao tenant.

## Decisão

- expor um perfil SCIM 2.0 em `/scim/v2/{tenantId}`, inicialmente limitado ao
  recurso `User` do RFC 7643 e às operações do RFC 7644;
- autenticar o provisionador por Client Credentials, com scopes distintos
  `scim.read` e `scim.write` e audience exclusiva `identity-hub-scim-api`;
- exigir que o `client_id` do token pertença ao tenant presente na URL;
- criar uma identidade global sem credencial local e uma membership sem papel;
- reutilizar uma identidade com o mesmo e-mail somente para criar uma nova
  membership, sem importar papéis ou permissões;
- manter `externalId`, nome de exibição, versão e tombstone no recurso SCIM por
  tenant; `userName` permanece imutável depois da criação;
- mapear `active` para o estado da membership, sem desabilitar a identidade em
  outros tenants;
- usar ETags fracas e aceitar `If-Match` em mutações para detectar versões
  obsoletas;
- auditar criação, atualização, exclusão e tentativas horizontais sem registrar
  bearer tokens ou client secrets.

## Consequências

- um diretório não consegue conceder administração: memberships SCIM nascem
  sem papel e a atribuição continua na API administrativa local;
- `DELETE` mantém um tombstone e suspende a membership, permitindo
  reprovisionamento idempotente posterior;
- o subconjunto inicial não implementa `Groups`, Bulk, sort, senha nem filtros
  arbitrários; a descoberta anuncia esses limites;
- a conta provisionada pode criar uma credencial local posteriormente pelo
  fluxo de recuperação, sujeito à validação do e-mail.

## Alternativas consideradas

### Usar um token estático por tenant

Rejeitada porque duplicaria ciclo de vida, hashing, rotação e auditoria já
entregues pelos clientes OAuth confidenciais.

### Desabilitar a identidade global quando `active=false`

Rejeitada porque um diretório de um tenant não pode retirar acesso a outros
tenants aos quais a mesma identidade pertença.

### Importar grupos como papéis locais

Rejeitada nesta fatia porque converter grupos externos em privilégios exige
uma política explícita de mapeamento e proteção do último administrador.

## Evidências exigidas

- migração com unicidade por tenant, membership, `userName` e `externalId`;
- tokens de máquina com scopes e audience SCIM;
- CRUD, PATCH, filtros, paginação, erros SCIM e ETags;
- isolamento horizontal, auditoria e ausência de papel automático;
- testes backend e build Angular verdes.
