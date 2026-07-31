# Fatia vertical 010 — proteção do último administrador do tenant

## Capacidade entregue

O Identity Hub agora impede que um tenant perca seu último administrador válido.
Para esta regra, um administrador válido é um usuário habilitado com membership
ativa e papel de código `administrator` no tenant.

O `TenantMembershipAdministrationService` centraliza três mutações sensíveis:
atribuição de papel, suspensão e remoção de membership. Antes de alterar o
estado, o serviço bloqueia transacionalmente a linha do tenant e conta os
administradores válidos. Se a operação remover a última capacidade
administrativa, ela falha com `LastTenantAdministratorException`.

## Concorrência e isolamento

Todas as mutações desse tenant disputam o mesmo lock pessimista. Assim, duas
remoções simultâneas não conseguem observar o mesmo total desatualizado: a
primeira pode concluir quando existe outro administrador, enquanto a segunda
reconta o estado confirmado e é rejeitada.

As consultas de membership e papel sempre incluem o `tenantId`, evitando que o
serviço administre uma associação de outro tenant por identificador isolado.

## Segurança e evidências

- rebaixamento do último administrador para um papel comum é rejeitado;
- suspensão e remoção do último administrador também são rejeitadas;
- usuário desabilitado ou membership suspensa não contam como administrador
  válido;
- um teste concorrente comprova que duas remoções deixam exatamente um
  administrador válido;
- um teste negativo cobre as três formas de perda da capacidade administrativa.

## Evolução de banco

Esta fatia não exige nova migração. A linha de `tenant`, já persistida desde a
V4, é o recurso de lock compartilhado, e os índices da V6 atendem à contagem por
tenant, papel e status.

## Limites ainda abertos

- ainda não existe API administrativa externa; as APIs futuras deverão usar o
  serviço central desta fatia;
- permission checks do ator da operação pertencem à próxima fatia;
- alterações diretas no banco não passam pela regra da aplicação;
- a concorrência foi validada em H2; a prova em PostgreSQL real continua
  dependente da execução do ambiente Compose.
