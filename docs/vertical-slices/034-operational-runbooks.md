# Fatia vertical 034 — runbooks operacionais

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADRs exercitadas:** 001, 005, 006 e 007

## Capacidade entregue

A Fase 8 passa a ter um índice operacional e três runbooks acionáveis:
resposta a incidente, rotação de chaves e recuperação do estado durável. Eles
definem acionamento, responsabilidades, evidência mínima, passos ordenados,
condições de abortagem, validação de saída e limites de autorização externa.

Os procedimentos reutilizam os alertas Prometheus, endpoints, permissões,
variáveis de chaves, tempos de token, tabelas e comandos PostgreSQL existentes.
Não criam endpoints administrativos fictícios nem tratam logs como substitutos
da auditoria tenant-aware.

## Exercício de mesa executado

Três cenários foram percorridos estaticamente contra os contratos do código:

1. **replay de refresh token:** o alerta leva à família já revogada pelo
   backend, consulta autorizada de auditoria e recuperação de senha quando há
   tomada de conta; o runbook não promete revogação global inexistente;
2. **falha na rotação depois da ativação:** o procedimento mantém ambas as
   chaves públicas e corrige para frente, evitando invalidar tokens emitidos
   pelo novo `kid`; chave comprometida segue caminho emergencial sem rollback;
3. **restauração de snapshot:** o serviço permanece fechado, o banco candidato
   é validado em isolamento e o estado OAuth restaurado é removido em transação
   antes da reabertura.

O exercício confirmou também dependências externas: cache JWK dos resource
servers, cofre, PGPASSFILE, ingresso em manutenção, contatos de plantão e
Alertmanager não são provisionados pelo repositório e precisam ser preenchidos
pela plataforma.

## Evidências verificadas

- nomes dos seis alertas conferidos nas regras Prometheus versionadas;
- endpoints de readiness, discovery, JWK Set e auditoria conferidos no backend;
- variáveis e estados `generated`, `pem` e `rotating-pem` conferidos na
  configuração de chaves;
- retenção anterior de 5 minutos a 7 dias e access token de 5 minutos
  conferidos no código;
- tabelas e ordem de invalidação OAuth conferidas nas migrations;
- links Markdown, suíte completa do backend e whitespace validados ao concluir.

## Limites ainda abertos

- nenhum incidente, comprometimento ou corte de produção foi provocado;
- nenhum backup real foi restaurado e nenhum cofre foi acessado;
- rotação em múltiplas réplicas e expiração de caches JWK externos não foram
  exercitadas;
- contatos, SLAs de atendimento, manutenção de ingresso, Alertmanager e
  comunicação são responsabilidades da plataforma/organização;
- a próxima etapa sequencial entra nas evoluções pós-MVP da Fase 9.
