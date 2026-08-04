# Fatia vertical 031 — backup, restauração e retenção de auditoria

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADRs exercitadas:** 005, 006 e 007

## Capacidade entregue

A política do MVP passa a definir RPO de 24 horas, RTO de 4 horas, backup lógico
diário completo com 35 cópias cifradas e ensaio mensal de restauração em banco
isolado. O conjunto de recuperação separa dump PostgreSQL, configuração, versão
da imagem e secrets externos; a chave MFA correspondente ao snapshot é tratada
como indispensável.

A restauração exige validação de checksum, migrações, contagens agregadas,
readiness e autenticação controlada antes de qualquer reabertura. Quando um
snapshot substitui produção, authorization codes, interações e famílias de
refresh token restauradas devem ser invalidadas para não ressuscitar sessões.

## Retenção executável

`SecurityAuditRetentionService` remove eventos anteriores ao corte UTC em lotes
configuráveis. O recurso nasce desabilitado e só é criado quando
`IDENTITY_HUB_AUDIT_RETENTION_ENABLED=true`. A política padrão conserva 365 dias,
aceita de 30 a 3650 dias e limita cada lote entre 1 e 10.000 eventos.

A migração V17 adiciona o índice `(occurred_at, id)` usado para selecionar os
eventos mais antigos. Seleção e remoção pertencem à mesma transação; o predicado
de data é repetido no `DELETE`, e execuções concorrentes em réplicas permanecem
idempotentes. Métricas agregadas contam eventos removidos e falhas sem tenant,
ator ou identificador como dimensão.

## Evidências executadas

- V17 aplicada do zero no banco automatizado;
- eventos anteriores ao corte são removidos em ordem e respeitam o lote;
- evento exatamente no corte e eventos recentes permanecem;
- execuções sucessivas drenam o backlog sem ultrapassar o lote;
- retenção menor que 30 dias e lote acima de 10.000 falham na configuração;
- métrica de eventos removidos soma apenas exclusões efetivas;
- suíte completa do backend e validação do Compose verificadas ao concluir.

## Limites ainda abertos

- nenhum dump ou restore foi executado contra PostgreSQL real;
- cofre, criptografia, imutabilidade, replicação e agenda de backup pertencem à
  plataforma de implantação e não são provisionados por este repositório;
- a migração V17 foi exercitada em H2 compatível, não em PostgreSQL;
- exportação consultável, preservação legal automatizada e painéis/alertas serão
  evoluções posteriores.
