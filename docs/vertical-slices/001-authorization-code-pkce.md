# Fatia vertical 001 — Authorization Code com PKCE

- **Estado:** entregue
- **Data:** 2026-07-31
- **ADRs exercitadas:** 001, 002, 003, 005, 006 e 008

## Capacidade entregue

Um cliente Angular público inicia Authorization Code com PKCE `S256`, cria
`state`, `nonce` e verifier no navegador, autentica o usuário nas telas públicas
TailAdmin e apresenta consentimento antes de receber o callback. O cliente troca
o code por ID token e access token e consulta UserInfo.

O backend é a única autoridade sobre cliente, redirect URI, escopos, sessão,
consentimento, code e tokens. O frontend de interação recebe somente um
`interaction_id` opaco e dados de apresentação.

## Persistência

A migração `V1__identity_hub_foundation.sql` cria:

- usuários locais com senha BCrypt;
- clientes OAuth;
- autorizações, consentimentos, codes e tokens;
- interações de login e consentimento.

O valor bruto do `interaction_id` não é persistido. O banco mantém apenas seu
hash SHA-256, o hash do identificador da sessão, tipo, estado, usuário quando
aplicável e expiração. A interação expira em cinco minutos e não pode ser usada
por outra sessão.

## Contratos web

- `GET /oauth2/authorize`: inicia e valida o pedido;
- `POST /oauth2/token`: troca o code com PKCE;
- `GET /.well-known/openid-configuration`: metadata OIDC;
- `GET /oauth2/jwks`: JWK Set;
- `GET /userinfo`: claims autorizadas;
- `GET /api/v1/interactions/{id}`: apresentação segura da interação;
- `POST /api/v1/interactions/{id}/login`: autenticação e rotação da sessão;
- `POST /api/v1/interactions/{id}/consent`: aprovação ou recusa.

As operações mutáveis da API de interação exigem CSRF. A sessão usa cookie
`HttpOnly` e `SameSite=Lax`; `IDENTITY_HUB_COOKIE_SECURE=true` é obrigatório sob
HTTPS. O bootstrap local só é habilitado pelo perfil `dev`.

## Evidências verificadas

- migração Flyway aplicada em H2/PostgreSQL compatível nos testes;
- teste de integração atravessando login, consentimento, code, PKCE, ID token,
  access token e UserInfo;
- interação rejeitada quando apresentada por outra sessão;
- `state` preservado e code rejeitado na segunda utilização;
- build Maven com 2 testes aprovados;
- build de produção Angular aprovado;
- execução real com PostgreSQL 17 e fluxo completo validado no navegador;
- frontend sem erros de console durante o fluxo validado.

## Limitações conhecidas

- a chave RSA é efêmera e tokens deixam de validar após reinício;
- ainda não há refresh token, logout OIDC, MFA, recuperação ou bloqueio;
- tenancy, RBAC, administração e auditoria estruturada permanecem planejados;
- o cliente demonstrativo valida state, nonce e audience, mas não substitui uma
  biblioteca OIDC certificada para aplicações de produção;
- a suíte negativa ainda deve cobrir PKCE inválido, expiração, recusa e CSRF de
  forma dedicada;
- Redis e alta disponibilidade não entram nesta fatia.
