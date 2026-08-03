# Fatia vertical 020 — rate limiting por sinais combinados

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADR exercitada:** 012

## Capacidade entregue

Login, cadastro, confirmação de e-mail e as duas etapas de recuperação de senha
agora consultam `RateLimitService` antes de executar trabalho sensível. Cada
requisição é avaliada simultaneamente por identificador, origem e combinação
dos dois, com namespace próprio por operação.

O cliente recebe `429 Too Many Requests`, mensagem genérica e `Retry-After`.
As telas TailAdmin de login, cadastro e recuperação apresentam essa mensagem;
a confirmação de e-mail distingue excesso de tentativas de link inválido sem
revelar dados da conta.

## Proteção de dados e operação

As janelas mantêm somente hashes SHA-256 das chaves compostas. Métricas usam
apenas operações e tipos de sinal previamente enumerados. Nenhum e-mail, token
ou endereço de origem entra nas tags.

Por padrão, a janela dura um minuto e permite 10 tentativas por identificador,
60 por origem e 8 pelo par. O armazenamento local é limitado a 100.000 buckets,
remove entradas expiradas e rejeita novas combinações quando cheio. Todos os
valores podem ser configurados pelas variáveis `IDENTITY_HUB_RATE_LIMIT_*`.

## Evidências executadas

- três testes unitários cobrem os três sinais, normalização, expiração, limite
  de memória, fechamento seguro e cardinalidade das métricas;
- um teste HTTP comprova `429`, `Retry-After`, mensagem genérica e que uma nova
  identidade na mesma origem ainda possui orçamento sob o limite configurado;
- a suíte completa do backend, o build Angular, o Compose e as diferenças foram
  verificados após a implementação.

## Limites ainda abertos

- janelas são locais à instância e reiniciam com o processo;
- escala horizontal exige armazenamento coordenado antes de afirmar limite
  global;
- a confiança em forwarded headers depende da publicação atrás de proxy
  confiável;
- o comportamento não foi exercitado em navegador real nem sob teste de carga.
