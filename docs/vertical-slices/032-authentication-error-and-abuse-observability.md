# Fatia vertical 032 — painéis e alertas operacionais

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADRs exercitadas:** 001, 006 e 007

## Capacidade entregue

O Compose opcional agora coleta o endpoint Micrometer com Prometheus 3.13.2 e
provisiona no Grafana 13.1.2 uma fonte de dados e um painel versionado. O painel
une sinais HTTP, JVM, conexões, refresh tokens, rate limiting e retenção de
auditoria sem introduzir identificadores pessoais ou de tenant como dimensões.

Seis regras Prometheus cobrem indisponibilidade, taxa de 5xx, pico de falhas de
autenticação, rejeições por abuso, replay de refresh token e falha na retenção
da auditoria. Limites, janela, severidade e categoria estão no código e no guia
operacional para que a calibração futura seja deliberada.

## Fronteira de coleta

O endpoint `/actuator/prometheus` permanece protegido. O backend aceita uma
credencial técnica Bearer de 32 a 512 caracteres carregada de arquivo externo
`file:`. A comparação é constante, a credencial não é registrada e um Bearer
inválido recebe `401`. Sem Bearer, a cadeia normal continua permitindo o acesso
manual por sessão autenticada já existente.

O overlay monta o mesmo secret somente leitura no backend e no Prometheus. O
repositório contém apenas o caminho e o contrato da credencial, nunca um valor
real.

## Evidências executadas

- token técnico válido retorna métricas e não aparece na resposta;
- token técnico inválido e ausência de autenticação retornam `401`;
- sessão autenticada continua consultando o endpoint;
- origem externa aceita arquivo válido e rejeita origem ambígua ou não externa;
- dashboard JSON, Compose combinado, configuração e regras Prometheus foram
  validados ao concluir;
- suíte completa do backend foi executada ao concluir.

## Limites ainda abertos

- Prometheus e Grafana não foram mantidos em execução nem exercitados no
  navegador com tráfego real nesta validação;
- os limites são baseline e precisam ser calibrados com volume representativo;
- as regras ficam visíveis no Prometheus/Grafana, mas entrega a e-mail, chat ou
  pager depende de Alertmanager e receivers externos;
- logs estruturados e OpenTelemetry permanecem itens independentes do roadmap;
- testes de carga dos endpoints críticos são a próxima etapa sequencial.
