# Fatia vertical 013 — administração Angular de clientes OAuth

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 001, 002, 003, 004, 005, 006 e 008

## Capacidade entregue

O TailAdmin possui sua primeira superfície administrativa real em
`/admin/oauth-clients`. Um administrador autentica a SPA por Authorization Code
com PKCE, recebe o contexto de tenants permitido pelo backend, seleciona a
organização ativa e cria, consulta, atualiza ou remove clientes OAuth públicos.

A interface representa explicitamente os estados sem sessão, carregando, lista
vazia, somente leitura, formulário de gestão, sucesso e erro. A navegação do
template aponta para a nova área sem remover ainda as páginas demonstrativas
restantes.

## Fluxo de autenticação

O cliente público `identity-hub-demo` passa a registrar também o callback exato
`/admin/oauth-clients/callback`. A área solicita `openid`, `profile` e
`identity.admin`, usa challenge PKCE `S256` e valida `state`, `nonce` e audience
do ID token antes de aceitar a sessão.

O access token permanece somente em memória. O refresh token rotativo fica em
`sessionStorage`, separado da sessão do cliente demonstrativo, e restaura a
área após recarregar a página. Encerrar a sessão administrativa chama o
endpoint RFC 7009 antes de limpar o estado local.

## Contexto e autorização

`GET /api/v1/admin/context` retorna apenas memberships ativas do sujeito em
tenants ativos, com papel e permissões efetivas. O endpoint exige o mesmo
issuer, audience `identity-hub-admin-api` e scope `identity.admin` das demais
APIs administrativas.

A interface usa as permissões como affordance, nunca como fonte de verdade:

- `oauth.clients.read` habilita a listagem;
- `oauth.clients.manage` habilita formulário, edição e remoção;
- o backend repete essas verificações e o isolamento de tenant a cada chamada.

## Manutenção de clientes

O formulário mantém o `clientId` imutável após a criação, aceita redirect URIs
e retornos pós-logout em linhas separadas e oferece apenas os escopos suportados
pelo backend. A lista apresenta tipo público, exigência de PKCE, escopos,
callback principal e criação. A exclusão exige confirmação e informa que
autorizações e sessões renováveis serão revogadas.

## Persistência

Esta fatia não cria migração. Ela consome o ownership e o CRUD persistidos pela
V8. O único ajuste de cadastro adiciona ao cliente público de desenvolvimento
o callback administrativo derivado da URL pública da UI.

## Evidências executadas

- o contexto administrativo retorna apenas o tenant do ator autenticado;
- o contexto expõe as permissões efetivas de leitura e gestão;
- os testes do CRUD e do Authorization Code permanecem verdes;
- o build de produção Angular valida rota, serviços, bindings e template;
- a rota sem sessão foi renderizada no navegador, com navegação, conteúdo e
  console sem erros;
- a suíte backend completa e a configuração Compose continuam verificadas no
  fechamento da fatia.

## Limites ainda abertos

- a área ainda não administra memberships, papéis ou tenants;
- o refresh token permanece acessível ao JavaScript em `sessionStorage`, como
  no cliente demonstrativo atual; uma evolução para BFF reduziria exposição a
  XSS;
- expiração durante uma mutação solicita nova autenticação, sem repetição
  automática do comando;
- o fluxo autenticado completo ainda não foi exercitado no navegador nem em
  PostgreSQL real;
- auditoria append-only das ações administrativas permanece planejada.
