# Architecture Decision Records

Esta pasta registra decisões arquiteturais que condicionam o desenvolvimento do
Identity Hub.

## Estados

- **proposto:** em discussão e ainda não vinculante;
- **aceito:** orienta a implementação;
- **substituído:** preservado como histórico e apontando para o ADR sucessor;
- **rejeitado:** alternativa avaliada e não adotada.

ADRs aceitos não devem ser reescritos para esconder uma mudança de direção.
Correções editoriais são permitidas; mudanças de decisão exigem um novo ADR que
declare qual registro foi substituído.

## Índice

| ADR | Decisão | Estado |
| --- | --- | --- |
| [001](001-modular-monolith.md) | Monólito modular como unidade inicial de implantação | Aceito |
| [002](002-protocol-first-authorization-server.md) | Servidor de autorização orientado por padrões | Aceito |
| [003](003-client-types-and-pkce.md) | Tipos de cliente e PKCE | Aceito |
| [004](004-multi-tenancy-and-issuer-strategy.md) | Multi-tenancy e estratégia de issuer | Aceito |
| [005](005-token-and-key-lifecycle.md) | Ciclo de vida de tokens e chaves | Aceito |
| [006](006-persistence-cache-and-consistency.md) | Persistência, cache e consistência | Aceito |
| [007](007-security-audit-and-sensitive-data.md) | Auditoria e dados sensíveis | Aceito |
| [008](008-tailadmin-authorization-interaction-ui.md) | TailAdmin como interface de login e consentimento | Aceito |
| [009](009-password-policy-and-hash-evolution.md) | Política de senha e evolução de hash | Aceito |
| [010](010-secure-password-recovery.md) | Recuperação segura de senha | Aceito |
| [011](011-progressive-login-lockout.md) | Bloqueio progressivo de login | Aceito |
| [012](012-combined-signal-rate-limiting.md) | Rate limiting por sinais combinados | Aceito |
| [013](013-critical-session-invalidation.md) | Invalidação após eventos críticos de credencial | Aceito |
| [014](014-totp-mfa-and-recovery-codes.md) | MFA TOTP e códigos de recuperação | Aceito |
| [015](015-passkeys-webauthn.md) | Passkeys WebAuthn para autenticação sem senha | Aceito |
| [016](016-external-oidc-federation.md) | Federação OIDC e vínculo seguro de identidades | Aceito |
| [017](017-scim-user-provisioning.md) | Provisionamento SCIM isolado por tenant | Aceito |
| [018](018-device-authorization-grant.md) | Device Authorization Grant com consentimento no navegador | Aceito |
| [019](019-adaptive-authentication-policies.md) | Step-up adaptativo para autenticação por senha | Aceito |
| [020](020-high-availability-and-chaos.md) | Réplicas stateless com sessão e abuso compartilhados | Aceito |

## Template

Todo novo ADR deve conter:

- status e data;
- contexto e forças relevantes;
- decisão;
- consequências positivas e negativas;
- alternativas consideradas;
- evidências que a implementação deverá produzir.
