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

## Template

Todo novo ADR deve conter:

- status e data;
- contexto e forças relevantes;
- decisão;
- consequências positivas e negativas;
- alternativas consideradas;
- evidências que a implementação deverá produzir.
