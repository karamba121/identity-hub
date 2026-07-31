# Fatia vertical 003 — refresh token rotativo e revogação

## Capacidade entregue

O cliente público demonstrativo passou a ser elegível para continuidade de
sessão sem client secret. O fluxo Authorization Code + PKCE emite um refresh
token opaco com validade absoluta de oito horas; cada uso emite um novo access
token e um novo refresh token e invalida o valor apresentado.

O Angular mantém somente o refresh token atual em `sessionStorage`, substitui
o valor após cada rotação e oferece ações explícitas para renovar e revogar a
sessão. Essa escolha é restrita ao cliente demonstrativo: uma aplicação real
deve avaliar BFF e cookies protegidos conforme seu threat model.

## Persistência e invariantes

A migração `V3__refresh_token_families.sql` cria:

- uma família por autorização, com estado `ACTIVE`, `COMPROMISED` ou
  `REVOKED`, versão e hash do token atual;
- histórico somente com SHA-256 dos refresh tokens e estados `CURRENT`,
  `USED` ou `REVOKED`;
- índice para percorrer e revogar todo o histórico da família.

O valor opaco atual continua na tabela operacional do Spring Authorization
Server, necessária para validar o grant. A tabela de histórico nunca persiste
os valores reutilizados em claro.

A consulta do token, a geração do sucessor e a atualização da família ocorrem
na mesma transação. Um lock pessimista na família garante que usos concorrentes
tenham um único vencedor. Quando um token `USED` ou `REVOKED` reaparece, a
autorização corrente é removida e toda a família passa a não conceder novos
tokens.

## Superfícies de protocolo

- `POST /oauth2/token` com `grant_type=refresh_token`;
- `POST /oauth2/revoke`, conforme RFC 7009;
- autenticação sem segredo limitada a clientes públicos cadastrados com método
  `none` e grant `refresh_token`;
- access token de 5 minutos e refresh token de 8 horas no cliente de
  desenvolvimento.

## Evidências executadas

- emissão inicial inclui refresh token;
- rotação devolve sucessor diferente;
- replay do token consumido invalida também o sucessor;
- revogação padronizada impede nova rotação;
- duas rotações concorrentes produzem exatamente uma resposta de sucesso, e a
  tentativa perdedora compromete a família;
- migrações V1, V2 e V3 foram validadas em sequência pelo teste com H2 em modo
  PostgreSQL;
- build Angular valida as ações de renovação e revogação.

## Limites ainda abertos

- logout OIDC e encerramento do cookie foram entregues na
  [fatia 004](004-oidc-logout.md); permanecem separados da revogação no
  protocolo e coordenados pelo cliente;
- indisponibilidade parcial e métricas específicas de rotação ainda não foram
  exercitadas;
- PostgreSQL real deve continuar sendo validado no deploy conjunto; H2 cobre a
  suíte automatizada, mas não substitui um teste concorrente no banco alvo;
- access tokens JWT já emitidos permanecem válidos até sua expiração de cinco
  minutos.
