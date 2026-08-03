# Fatia vertical 028 — suíte negativa de Client Credentials

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 002, 003, 004, 005 e 007

## Capacidade entregue

A Fase 7 passa a ter uma suíte regressiva dedicada às fronteiras negativas do
grant Client Credentials. Os testes usam o endpoint real `/oauth2/token`, um
cliente registrado com `client_secret_basic` e as cadeias de segurança reais
das APIs demonstrativa e administrativa.

Esta fatia não cria um protocolo alternativo nem altera respostas OAuth. Ela
torna explícitos os comportamentos de falha que já fazem parte do contrato.

## Matriz exercitada

| Cenário | Resultado comprovado |
| --- | --- |
| Client secret incorreto | `401` com `invalid_client`, sem access token, refresh token, client ID ou segredo na resposta |
| Escopo além da concessão | `400` com `invalid_scope`, sem emissão de token |
| Token de máquina correto | acessa a API demonstrativa com sujeito `client:<clientId>` |
| Token da audience demonstrativa na API administrativa | `401`, antes da autorização funcional |
| Token da audience administrativa na API demonstrativa | `401`, mesmo contendo `demo.read` |
| Audience administrativa sem `identity.admin` | `403`, demonstrando que audience e escopo são controles independentes |
| Cliente público em Client Credentials | `401 invalid_client`, preservado pela suíte administrativa existente |
| Audience incorreta no exemplo independente | `invalid_token`, preservado pelos testes do resource server publicado |

## Evidências executadas

- quatro novos testes de integração cobrem credencial, concessão e isolamento
  bidirecional de audience;
- segredo inválido e client ID não aparecem no corpo de erro;
- pedido combina `demo.read` com `identity.admin` para comprovar a rejeição de
  escopo excessivo;
- o access token emitido é reutilizado contra os dois resource servers para
  evitar uma simulação apenas estrutural;
- suíte completa do backend, testes do resource server independente e build
  Angular verificados ao concluir a fatia.

## Critério da Fase 7

O critério de aceite fica coberto de forma regressiva: cliente público não
autentica em Client Credentials e cada resource server rejeita tokens cuja
audience pertence ao outro contrato, independentemente dos escopos presentes.

## Limites ainda abertos

- os testes automatizados usam H2 em modo PostgreSQL;
- não houve tráfego real entre containers nem execução em PostgreSQL real;
- análise estatística de timing para comparação de client secrets não foi
  executada;
- rate limiting específico do endpoint de token permanece uma evolução
  orientada por sinais operacionais.
