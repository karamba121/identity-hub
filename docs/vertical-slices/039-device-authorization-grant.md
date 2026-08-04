# Fatia vertical 039 — Device Authorization Grant

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADR exercitada:** 018
- **Referência normativa:** RFC 8628

## Capacidade entregue

- `/oauth2/device_authorization` emite device code, user code, URI simples e URI
  completa para clientes públicos do tipo `DEVICE`;
- `/oauth2/token` aceita o grant
  `urn:ietf:params:oauth:grant-type:device_code` sem exigir um secret público;
- a administração permite criar e editar clientes de dispositivo sem redirect
  URI, limitados a `openid`, `profile`, `email` e `demo.read`;
- a rota pública `/device` aceita e normaliza o código, conduz login quando a
  sessão não existe e apresenta cliente, código e escopos antes da decisão;
- aprovação e recusa são processadas pelo endpoint protocolar do servidor, e o
  navegador recebe uma confirmação sem transportar tokens;
- códigos duram dez minutos, access tokens cinco minutos, não há refresh token e
  a autorização consumida não pode emitir um segundo token.

## Evidências verificadas

- teste de integração cobre emissão, `authorization_pending`, consentimento,
  access token, ausência de refresh token, código inválido e tentativa de reuso;
- teste cobre a criação de interação opaca de login para a verificação sem
  sessão autenticada;
- teste administrativo confirma grant, autenticação `none`, consentimento
  obrigatório, ausência de secret e de redirect URI;
- compilação backend e build Angular passaram durante a implementação.

## Limites ainda abertos

- o fluxo não foi exercitado em navegador real nem contra PostgreSQL real;
- clientes devem respeitar o intervalo de polling da RFC; a implementação base
  do servidor retorna os estados protocolares, mas não mantém penalização
  distribuída por polling acelerado;
- políticas adaptativas de autenticação passam a ser o próximo item do roadmap.
