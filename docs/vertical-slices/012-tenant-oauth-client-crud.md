# Fatia vertical 012 — CRUD de clientes OAuth por tenant

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 001, 002, 003, 004, 005 e 006

## Capacidade entregue

Um administrador autorizado pode cadastrar e manter clientes OAuth públicos
pertencentes ao próprio tenant. A fatia conecta ownership persistido, registro
nativo do Spring Authorization Server, API administrativa, validação de
redirect URIs e escopos, autorização por permissão e testes de isolamento.

Clientes criados por essa API usam Authorization Code e refresh token, não
possuem secret, exigem PKCE e consentimento e preservam as políticas já
adotadas: authorization code por 2 minutos, access token por 5 minutos,
refresh token por 8 horas e rotação sem reutilização.

## Contratos

A base é `/api/v1/admin/tenants/{tenantId}/oauth-clients`:

- `GET /` lista os clientes do tenant;
- `GET /{clientId}` consulta um cliente;
- `POST /` cria um cliente e retorna `201` com `Location`;
- `PUT /{clientId}` altera nome, redirect URIs e escopos;
- `DELETE /{clientId}` remove o cliente e retorna `204`.

Criação recebe `clientId`, `clientName`, `redirectUris`,
`postLogoutRedirectUris` e `scopes`. Atualização mantém o `clientId` imutável.
A resposta não contém credenciais: informa os dados públicos, tipo `PUBLIC`,
PKCE obrigatório e instante de criação.

Entrada inválida retorna `400`, cliente ausente no tenant retorna `404` e
`clientId` já registrado globalmente retorna `409`, com `ProblemDetail`.

## Segurança e isolamento

Todas as rotas continuam protegidas pela validação do access token
administrativo: issuer, assinatura, audience `identity-hub-admin-api` e scope
`identity.admin`. Em seguida, a autorização efetiva considera a membership
ativa do ator no tenant da rota:

- consultas exigem `oauth.clients.read`;
- criação, atualização e remoção exigem `oauth.clients.manage`.

O ownership entre tenant e registro OAuth fica explícito na persistência. Uma
consulta ou mutação com o mesmo `clientId` sob outro tenant não encontra o
recurso, e um ator sem vínculo administrativo com o tenant da rota recebe
`403` antes do caso de uso.

## Validações do cliente

- `clientId` possui formato restrito e permanece único em toda a instalação;
- ao menos uma redirect URI e um escopo são obrigatórios;
- redirect URIs usam HTTPS, exceto HTTP para loopback local;
- curingas, fragmentos e credenciais embutidas na URI são rejeitados;
- os escopos aceitos são `openid`, `profile`, `email`, `demo.read` e
  `identity.admin`;
- no máximo dez URIs de cada tipo são aceitas, com limite agregado de tamanho.

## Persistência e exclusão

A migração `V8__tenant_oauth_clients.sql` cria `tenant_oauth_client`, ligando o
tenant ao identificador interno de `oauth2_registered_client`, com unicidade de
`client_id` e chave estrangeira para ambos os lados. O bootstrap de
desenvolvimento vincula o cliente demonstrativo ao tenant provisionado.

A remoção apaga consentimentos, autorizações e famílias de refresh tokens do
cliente antes de excluir o registro. Assim, material renovável já persistido
não sobrevive a um cliente removido.

## Evidências executadas

- criação, listagem, consulta, atualização e remoção persistem o resultado;
- permissões de leitura e gestão são verificadas separadamente;
- administrador de outro tenant não observa nem altera o cliente;
- redirect URI insegura e escopo desconhecido retornam `400`;
- `clientId` duplicado entre tenants retorna `409`;
- a migração V8 e as oito anteriores são aplicadas do zero no banco de teste;
- o fluxo Authorization Code existente continua verde após o novo cadastro.

## Limites ainda abertos

- a interface Angular administrativa permanece planejada;
- clientes confidenciais, Client Credentials e ciclo de secrets pertencem a
  uma fatia posterior e não são simulados neste CRUD;
- alterações ainda não produzem auditoria append-only;
- a listagem ainda não possui paginação;
- atualizar o cadastro não revoga access tokens já emitidos, que expiram pela
  política vigente;
- os testes automatizados usam H2 em modo PostgreSQL; a migração e o fluxo em
  PostgreSQL real continuam como validação operacional separada.
