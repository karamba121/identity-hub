# Fatia vertical 004 — logout OIDC e encerramento da sessão

## Capacidade entregue

O cliente demonstrativo agora encerra de forma coordenada os dois estados que
compõem a sessão:

1. revoga a família renovável pelo endpoint RFC 7009;
2. inicia RP-Initiated Logout no `end_session_endpoint` OIDC;
3. o servidor invalida a sessão HTTP e seu contexto de autenticação;
4. o navegador retorna somente para a URI pós-logout cadastrada;
5. o Angular valida o `state`, limpa os artefatos locais e confirma o término.

O fluxo usa o endpoint padrão `/connect/logout` publicado na metadata OIDC. Não
foi criado um protocolo de logout proprietário.

## Registro do cliente

O cliente `identity-hub-demo` possui a URI exata
`http://localhost:4200/demo/logout` no desenvolvimento. No Compose ela é
derivada de `IDENTITY_HUB_PUBLIC_URL`, evitando retorno acidental para outro
host ou ambiente. O bootstrap também reconcilia clientes já persistidos.

O Angular conserva o ID token em `sessionStorage` somente durante a sessão para
enviá-lo como `id_token_hint`. O retorno carrega um `state` aleatório correlato;
um retorno sem correspondência é tratado como erro e não como logout confirmado.

## Invariantes e evidências

- a metadata publica `end_session_endpoint`;
- a família de refresh token é revogada antes do redirecionamento de logout;
- o endpoint OIDC invalida a sessão HTTP autenticada;
- nova autorização após o logout exige autenticação novamente;
- o refresh token revogado não concede novos tokens;
- `post_logout_redirect_uri` cadastrada recebe o mesmo `state`;
- uma URI não cadastrada retorna `400` e não recebe redirecionamento;
- proxy Angular e Nginx encaminham `/connect/**` ao backend.

## Limites ainda abertos

- o logout encerra a sessão atual do navegador, não todas as sessões do usuário;
- access tokens JWT já emitidos permanecem válidos até o limite de cinco
  minutos, pois não há introspecção obrigatória no resource server;
- se a revogação estiver indisponível, o cliente não declara sucesso nem inicia
  silenciosamente um logout parcial; a falha fechada do armazenamento de
  sessões foi exercitada na
  [fatia 005](005-session-observability-and-resilience.md);
- front-channel/back-channel logout entre múltiplos clientes ainda não foi
  implementado.
