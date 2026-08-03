# ADR-012: Rate limiting por sinais combinados

- **Status:** aceito
- **Data:** 2026-08-03

## Contexto

Bloqueio progressivo protege uma identidade conhecida, mas não contém criação
em massa, enumeração distribuída ou rotação de identificadores. Limitar apenas
por IP penaliza redes compartilhadas e é facilmente contornado por troca de
origem. Também não é aceitável colocar e-mail, token ou endereço de origem em
métricas e chaves observáveis.

A implantação atual possui uma única instância de backend. A solução precisa
ser real nesse modelo, ter memória limitada e declarar claramente o que muda
quando houver múltiplas réplicas.

## Decisão

- login, cadastro, verificação de e-mail, solicitação e conclusão de recuperação
  passam pelo mesmo serviço de rate limiting;
- cada operação consome atomicamente três janelas: identificador normalizado,
  origem efetiva e combinação identificador+origem;
- as operações possuem namespaces separados, impedindo que uma recuperação
  consuma o orçamento de login;
- somente SHA-256 das chaves compostas é mantido; e-mail, token e origem não são
  usados como tags de métrica;
- a janela padrão é de um minuto, com limites de 10 por identificador, 60 por
  origem e 8 por combinação;
- a resposta de rejeição é `429`, contém `Retry-After` e uma mensagem genérica;
- janelas expiradas são removidas periodicamente;
- o mapa possui limite padrão de 100.000 buckets e falha fechado quando não há
  capacidade para os três sinais de uma nova requisição;
- rejeições geram a métrica `identity_hub.abuse.rate_limit.rejections` apenas
  com as dimensões enumeradas `operation` e `signal`;
- a origem vem de `HttpServletRequest.getRemoteAddr()`, já processada pela
  estratégia de forwarded headers; produção deve aceitar esses headers somente
  de proxies confiáveis;
- limiares e capacidade são configuráveis e valores inválidos impedem a
  inicialização.

## Consequências

- trocar apenas o IP não elimina o limite por identificador;
- trocar apenas o identificador não elimina o limite por origem;
- ataques concentrados no mesmo par encontram o limite mais restritivo;
- nenhuma migração é necessária enquanto o backend permanecer em instância
  única;
- reiniciar o processo limpa as janelas;
- múltiplas réplicas teriam orçamentos independentes e exigirão um backend
  coordenado, como Redis ou armazenamento atômico compartilhado, antes de serem
  consideradas protegidas pelo mesmo limite global;
- rate limiting reduz abuso, mas não substitui bloqueio progressivo, MFA ou
  monitoramento operacional.

## Alternativas consideradas

### Limitar somente por IP

Rejeitado porque NAT, proxies e redes móveis agrupam usuários legítimos,
enquanto atacantes podem rotacionar origens.

### Persistir cada tentativa no PostgreSQL

Rejeitado para a implantação única atual pelo custo de escrita e contenção nos
endpoints mais atacados. Deve ser reconsiderado junto com escala horizontal.

### Mapa sem limite de capacidade

Rejeitado porque valores controlados pelo atacante poderiam causar crescimento
de memória sem limite.

## Evidências exigidas

- limite por combinação antes do limite de origem;
- mesmo identificador limitado ao trocar origem;
- mesma origem limitada ao trocar identificador;
- expiração reabrindo a janela;
- capacidade máxima falhando fechado;
- contrato `429` com `Retry-After`;
- métricas sem sinais controlados pelo usuário;
- build da UI cobrindo o tratamento seguro de rejeição.
