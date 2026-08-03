# Fatia vertical 014 — auditoria de segurança administrativa

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 001, 004, 006 e 007

## Capacidade entregue

As mutações administrativas existentes passam a produzir uma trilha de
auditoria estruturada e tenant-aware. Criação, atualização e remoção de
clientes OAuth, além de atribuição de papel, suspensão e remoção de membership,
registram ações concluídas, negadas ou falhas.

Cada evento contém instante UTC, tipo, resultado, motivo normalizado quando
aplicável, identificadores internos de ator, tenant e alvo e um identificador
de correlação. Não são persistidos payload da requisição, nomes, e-mails,
redirect URIs, escopos, passwords, códigos ou tokens.

## Consistência da escrita

`AdministrativeActionAuditor` abre a fronteira transacional que engloba
autorização, mutação e evento de sucesso. Se a auditoria de uma operação
concluída não puder ser persistida, a alteração também sofre rollback.

Negações e falhas usam uma transação independente, pois a transação da mutação
precisa ser revertida enquanto a tentativa permanece investigável. Os motivos
são códigos estáveis, como `MISSING_PERMISSION`, `VALIDATION_ERROR`,
`RESOURCE_NOT_FOUND`, `CONFLICT` e `LAST_ADMINISTRATOR`; mensagens internas ou
dados recebidos não entram no evento.

## Persistência append-only

A migração `V9__security_audit_events.sql` cria `security_audit_event` e índices
para leitura cronológica por tenant e ator. No modelo da aplicação:

- a entidade é imutável e todos os campos são não atualizáveis;
- o repositório expõe somente inserção e consulta, sem update ou delete;
- nenhum vínculo com o alvo impede preservar eventos após remoções;
- tentativas contra tenants ou atores inexistentes também podem ser registradas
  pelos identificadores recebidos de um token já validado.

## Consulta protegida

`GET /api/v1/admin/tenants/{tenantId}/audit-events?page=0&size=20` retorna os
eventos mais recentes, com paginação limitada a cem itens por página. A chamada
exige token administrativo e a permissão efetiva `security.audit.read` no
tenant da rota. Um ator de outra organização recebe `403`.

A área `/admin/oauth-clients` mostra os dez eventos recentes quando a permissão
está presente. A permissão controla apenas a affordance; o backend permanece a
fonte de verdade.

## Evidências executadas

- alteração concluída registra ator, tenant, alvo, correlação e `SUCCEEDED`;
- tentativa horizontal registra `DENIED` e `MISSING_PERMISSION` no tenant alvo;
- proteção do último administrador registra `FAILED` e
  `LAST_ADMINISTRATOR`, preservando a membership;
- atribuição, suspensão e remoção de memberships produzem eventos distintos;
- consulta sem `security.audit.read` ou em outro tenant retorna `403`;
- a resposta auditada não contém nome, redirect URI ou nomes de tokens;
- as nove migrações são aplicadas do zero no banco automatizado.

## Limites ainda abertos

- append-only é imposto pelo modelo e pelo repositório da aplicação; proteção
  contra um operador com acesso SQL direto exige política e privilégios do
  PostgreSQL de produção;
- retenção, arquivamento e exportação ainda precisam de decisão operacional;
- autenticação, emissão, consentimento e ciclo de tokens ainda não produzem a
  cobertura completa de eventos prevista pelo ADR-007;
- o fluxo autenticado no navegador e a migração V9 ainda não foram exercitados
  em PostgreSQL real.
