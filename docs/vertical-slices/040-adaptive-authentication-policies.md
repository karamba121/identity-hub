# Fatia vertical 040 — Políticas adaptativas de autenticação

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADR exercitada:** 019

## Capacidade entregue

- o login local captura falhas recentes antes de validar a senha e avalia os
  escopos da interação já validados pelo backend;
- três falhas nos quinze minutos anteriores exigem step-up no baseline;
- uma lista opcional de escopos sensíveis permite exigir fator forte por
  ambiente sem aceitar decisões vindas do frontend;
- `adaptive_step_up_until` mantém a exigência por cinco minutos, impedindo que o
  reset do contador após a senha correta contorne a política;
- TOTP habilitado reutiliza o desafio MFA existente; sem TOTP, a senha retorna
  `403` e a tela orienta o uso de MFA ou passkey;
- TOTP ou passkey bem-sucedidos encerram a exigência temporária;
- auditoria registra step-up exigido, senha recusada e fator forte concluído;
- a métrica `identity_hub.authentication.adaptive.decisions` usa somente tags
  fechadas de resultado e motivo.

## Configuração

| Variável | Padrão | Finalidade |
| --- | --- | --- |
| `IDENTITY_HUB_ADAPTIVE_AUTHENTICATION_ENABLED` | `true` | habilita a política no login local |
| `IDENTITY_HUB_ADAPTIVE_FAILED_ATTEMPT_THRESHOLD` | `3` | falhas que acionam step-up |
| `IDENTITY_HUB_ADAPTIVE_SIGNAL_WINDOW` | `15m` | janela das falhas recentes |
| `IDENTITY_HUB_ADAPTIVE_CHALLENGE_TTL` | `5m` | duração da exigência persistida |
| `IDENTITY_HUB_ADAPTIVE_SENSITIVE_SCOPES` | vazio | escopos separados por vírgula |

Escopos sensíveis devem ser habilitados somente depois que os usuários afetados
tenham TOTP ou passkey. Uma senha isolada nunca supera essa regra.

## Evidências verificadas

- testes unitários cobrem baixo risco, falhas recentes, escopo sensível,
  persistência da exigência e conclusão por fator forte;
- teste de integração cobre Flyway V21, login recusado sem fator e auditoria;
- regressões focadas de MFA e bloqueio progressivo permanecem verdes;
- suíte backend com 134 testes, build Angular, renderização da configuração do
  Compose e `git diff --check` passaram na validação final desta fatia.

## Limites ainda abertos

- não há perfilamento por IP, dispositivo, geolocalização ou reputação externa;
- a política foi exercitada localmente com H2, não com PostgreSQL real;
- o comportamento visual não foi exercitado em navegador real;
- issuer por tenant permanece como próximo item condicionado a requisito
  comprovado no roadmap.
