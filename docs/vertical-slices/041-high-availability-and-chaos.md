# Fatia vertical 041 — Alta disponibilidade e testes de caos

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADR exercitada:** 020

## Capacidade entregue

- Spring Session Redis compartilha a sessão SSO sem afinidade por réplica;
- sessões são indexadas por principal e invalidadas globalmente após eventos
  críticos de credencial;
- o rate limiting usa script Lua atômico, TTL e limite de buckets compartilhado;
- indisponibilidade do Redis produz `503` e não ativa fallback permissivo;
- Nginx resolve dinamicamente as réplicas e Prometheus descobre todos os seus IPs;
- o overlay HA exige par RSA PEM externo comum e remove a porta individual do
  backend antes de escalar;
- um cenário PowerShell isolado interrompe réplica e Redis e valida recuperação.

## Evidências verificadas

- 137 testes backend passaram, incluindo rejeição Redis, falha fechada,
  invalidação de todas as sessões indexadas e bootstrap concorrente;
- build de produção Angular passou;
- as configurações Compose base e HA foram aceitas;
- o cenário `ha-failover.ps1` passou em volumes novos com duas réplicas:
  sessão e rate limit compartilhados, JWK estável, failover de réplica e falha
  fechada/recuperação do Redis;
- o laboratório, seus volumes e as chaves RSA descartáveis foram removidos após
  a execução.

## Limites ainda abertos

- o Compose usa uma única instância PostgreSQL e Redis para o laboratório;
- HA real desses armazenamentos pertence à plataforma alvo;
- o teste local não representa múltiplas zonas nem mede SLO durante degradação;
- issuer por tenant continua adiado por ausência de requisito comprovado;
- extração de módulos continua condicionada a evidência operacional.
