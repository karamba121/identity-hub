# ADR-006: Persistência, cache e consistência

- **Status:** aceito
- **Data:** 2026-07-30

## Contexto

Identidades, clientes, autorizações e auditoria são estado durável. Rate limits,
desafios temporários e caches têm outro ciclo de vida. Misturar essas
responsabilidades pode transformar expiração de cache em perda de segurança ou
usar o banco relacional como contador distribuído ineficiente.

Alguns fluxos também possuem invariantes concorrentes: code de uso único,
rotação de refresh token, último administrador e bloqueio de conta.

## Decisão

PostgreSQL será a fonte de verdade durável. Cada módulo será proprietário de
suas tabelas e migrações, ainda que compartilhe a mesma instância.

Redis será usado apenas para dados:

- efêmeros e com TTL;
- reconstruíveis;
- de coordenação ou limitação de taxa;
- cuja semântica de falha esteja definida.

Cache miss nunca concederá acesso. Ausência ou indisponibilidade do Redis terá
política fail-safe específica por fluxo.

Invariantes simples usarão concorrência otimista. Operações que exigem um único
vencedor usarão transação e lock apropriados. Idempotency keys serão introduzidas
em comandos externos sujeitos a repetição, não em toda operação por padrão.

Outbox só será adicionada junto à primeira integração assíncrona que necessite
garantia após commit.

## Consequências

- perda do Redis não remove identidades nem concessões duráveis;
- algumas funcionalidades poderão degradar ou negar temporariamente por
  segurança;
- modelos de cache exigem namespace por tenant e versão;
- migrações precisam respeitar propriedade modular;
- cenários concorrentes exigem testes com infraestrutura real;
- mensageria não faz parte da fundação sem consumidor real.

## Alternativas consideradas

### Redis como armazenamento primário de sessão e autorização

Rejeitado para estado que não possa ser reconstruído ou perdido com segurança.

### Banco separado por módulo

Rejeitado no MVP por antecipar complexidade operacional; propriedade lógica de
dados será preservada.

### Lock global para todas as operações

Rejeitado por reduzir throughput e esconder invariantes que podem ser tratados
com unicidade ou concorrência otimista.

## Evidências exigidas

- migrações aplicáveis em banco limpo e evoluído;
- constraints que reforcem invariantes importantes;
- testes de concorrência para operações de único vencedor;
- testes de comportamento com Redis indisponível;
- nenhuma chave de cache tenant-scoped sem tenant;
- métricas de conflito, cache e rate limiting sem dados sensíveis.
