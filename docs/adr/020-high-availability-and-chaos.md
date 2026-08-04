# ADR 020 — Réplicas stateless com sessão e abuso compartilhados

- **Status:** aceito
- **Data:** 2026-08-04

## Contexto

Executar duas instâncias do backend anterior não produzia alta disponibilidade:
a sessão SSO, os desafios associados e os contadores de abuso viviam no processo.
Uma troca de réplica perderia contexto, enquanto distribuir tentativas entre
instâncias enfraqueceria o rate limiting. Chaves RSA geradas por processo também
produziriam JWK Sets e assinaturas incompatíveis.

## Decisão

- manter o monólito modular como uma única unidade implantável, escalável por
  réplicas equivalentes;
- armazenar sessões HTTP no Spring Session Redis com índice por principal;
- invalidar no Redis todas as sessões de uma identidade após evento crítico,
  preservando o registro local somente como compatibilidade de teste/dev;
- executar os três sinais do rate limiting em um único script Lua atômico no
  Redis, com TTL e limite global de buckets;
- falhar com `503` quando o backend de proteção contra abuso estiver
  indisponível, sem fallback silencioso para memória;
- usar descoberta DNS dinâmica no Nginx e no Prometheus para acompanhar as
  réplicas do serviço;
- exigir o mesmo par PEM externo e os mesmos secrets em todas as réplicas;
- manter PostgreSQL como fonte de verdade durável e Redis apenas para estado
  efêmero reconstruível ou revogável.

O overlay Compose representa uma topologia local de duas réplicas e não tenta
transformar um único contêiner PostgreSQL ou Redis em cluster de produção. Em
produção, esses dois serviços precisam ser fornecidos com replicação, quorum,
backup e failover adequados à plataforma.

## Consequências

O fluxo OAuth pode atravessar réplicas sem sticky session, limites de abuso não
são multiplicados pela escala e uma recuperação de senha encerra sessões em
qualquer nó. Redis passa a ser dependência crítica de disponibilidade; sua falha
derruba readiness e bloqueia operações que dependem de sessão ou rate limiting.

O Nginx precisa de DNS dinâmico e as chaves não podem usar o modo `generated`.
Observabilidade passa a descobrir todas as réplicas, evitando métricas de um nó
aleatório. O Compose local ainda possui single points of failure nos armazenamentos.

## Alternativas consideradas

- sticky session: rejeitada por esconder estado local e falhar durante perda do nó;
- replicar apenas a sessão: rejeitada porque dividir tentativas entre réplicas
  contornaria a proteção contra abuso;
- fallback para memória durante falha Redis: rejeitado por conceder comportamento
  mais permissivo justamente durante degradação;
- extrair módulos em microsserviços: rejeitado por não ser requisito de HA e
  ampliar falhas distribuídas sem evidência operacional.

## Evidências exigidas

- sessão criada em uma réplica continua válida após sua interrupção;
- JWK Set permanece idêntico após failover;
- rate limiting combinado é compartilhado entre réplicas;
- perda do Redis falha fechada e recuperação restaura o fluxo;
- testes backend, build Angular, Compose e cenário de caos reproduzível.
