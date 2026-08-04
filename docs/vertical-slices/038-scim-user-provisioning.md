# Fatia vertical 038 — Provisionamento SCIM de usuários

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADR exercitada:** 017
- **Referências normativas:** RFC 7643, RFC 7644 e RFC 6750

## Capacidade entregue

- clientes confidenciais aceitam `scim.read` e `scim.write` e os access tokens
  recebem a audience exclusiva `identity-hub-scim-api`;
- o endpoint `/scim/v2/{tenantId}` publica `ServiceProviderConfig`,
  `ResourceTypes`, `Schemas` e o recurso `Users`;
- `Users` suporta criação, consulta individual, listagem, substituição, PATCH e
  exclusão, usando `application/scim+json`;
- a listagem aceita `startIndex`, `count` limitado a 100 e filtros `eq` exatos
  por `userName` ou `externalId`;
- respostas incluem `meta`, location, versão e ETag; `If-Match` rejeita uma
  mutação baseada em versão obsoleta;
- cada cliente fica restrito ao tenant ao qual seu cadastro OAuth pertence;
- criar um usuário produz uma membership sem papel e nunca importa privilégios;
- `active=false` e `DELETE` suspendem somente a membership daquele tenant;
- mutações e tentativas de acesso horizontal entram na auditoria de segurança.

## Operação

1. Na área **Clientes OAuth**, crie um cliente confidencial no tenant alvo.
2. Selecione `scim.read`, `scim.write` ou ambos e copie o secret exibido uma
   única vez.
3. Obtenha um token em `/oauth2/token` com Client Credentials.
4. Configure no diretório a base URL
   `{IDENTITY_HUB_PUBLIC_URL}/scim/v2/{tenantId}` e envie o token como Bearer.

O secret continua armazenado somente como Argon2id e pode usar a rotação com
janela controlada já existente. Tokens SCIM duram cinco minutos.

## Evidências verificadas

- Flyway aplica as 20 migrações no H2 em modo PostgreSQL;
- testes usam JWTs assinados e cobrem ciclo de vida, filtros, descoberta,
  ETags, duplicidade, scopes, audience e isolamento horizontal;
- um teste obtém o token pelo endpoint Client Credentials e valida subject,
  `client_id` e audience;
- a interface administrativa oferece os scopes SCIM somente para clientes de
  máquina;
- suíte backend, build Angular e whitespace são verificados ao fechar a fatia.

## Limites ainda abertos

- nenhum diretório SCIM real nem PostgreSQL real foi exercitado;
- o perfil inicial não oferece `Groups`, Bulk, sort, mudança de senha, filtros
  compostos ou mapeamento de atributos empresariais;
- a mutação de `userName` é rejeitada para evitar alterar uma identidade global
  a partir do contexto de um único tenant;
- Device Authorization Grant passa a ser o próximo item do roadmap.
