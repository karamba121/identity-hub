# Fatia vertical 037 — Federação com provedor OIDC externo

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADR exercitada:** 016
- **Referências normativas:** OpenID Connect Core 1.0 e OAuth 2.0 Authorization Code

## Capacidade entregue

- um conector OIDC genérico pode ser habilitado por ambiente com endpoints,
  client ID, client secret, nome e escopos explícitos;
- o início do login parte da interação opaca já vinculada à sessão e o callback
  retoma exatamente essa autorização depois da autenticação externa;
- Spring Security gera e valida `state`, `nonce`, PKCE, ID token, issuer e
  assinatura do provedor;
- contas novas são provisionadas apenas com `email_verified=true`; uma conta
  local preexistente exige vínculo iniciado no perfil autenticado;
- a persistência usa `registration_id + sub`, mantém e-mail somente como
  metadado histórico e aplica unicidade por usuário e provedor;
- o perfil lista, vincula e remove a identidade externa, sem permitir que uma
  conta exclusivamente federada remova seu último método de acesso;
- o principal OIDC é convertido em identidade local ativa antes da retomada do
  fluxo, sem importar papéis ou aceitar tokens externos nas APIs;
- contas com TOTP local habilitado continuam obrigadas a concluir o desafio;
- vínculo, remoção e login entram na trilha de segurança da própria identidade.

## Configuração operacional

Com `IDENTITY_HUB_FEDERATION_ENABLED=true`, são obrigatórios client ID, client
secret, issuer, authorization endpoint, token endpoint e JWK Set em HTTPS. O
callback a cadastrar no provedor é:

```text
{IDENTITY_HUB_PUBLIC_URL}/login/oauth2/code/{IDENTITY_HUB_FEDERATION_REGISTRATION_ID}
```

O `IDENTITY_HUB_PUBLIC_URL` e os cabeçalhos encaminhados pelo proxy precisam
produzir exatamente essa URL. O segredo não deve ser colocado no repositório,
na imagem ou em logs.

## Evidências verificadas

- Flyway aplica as 19 migrações no H2 em modo PostgreSQL;
- testes cobrem descoberta do provedor, início por interação opaca, redirecionamento
  com `state`, `nonce` e PKCE, provisionamento, repetição pelo mesmo `sub`,
  e-mail não verificado, colisão com conta local, vínculo explícito, isolamento,
  remoção e proteção do último método de acesso;
- a troca do principal externo pelo usuário local e a conclusão da interação
  possuem teste dedicado;
- suíte backend, build Angular, Compose e whitespace são verificados ao fechar
  a fatia.

## Limites ainda abertos

- nenhum provedor OIDC real, navegador ou callback externo foi exercitado;
- a migração V19 não foi aplicada a PostgreSQL real;
- a implantação atual oferece uma inscrição OIDC configurável por vez;
- logout federado, sincronização de grupos, múltiplos provedores simultâneos e
  políticas adaptativas permanecem evoluções independentes;
- provisionamento SCIM continua sendo o próximo item do roadmap.
