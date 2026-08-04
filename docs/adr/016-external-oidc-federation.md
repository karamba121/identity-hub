# ADR-016: Federação OIDC e vínculo seguro de identidades

- **Status:** aceito
- **Data:** 2026-08-04

## Contexto

O Identity Hub precisa aceitar autenticação de uma autoridade externa sem
entregar a ela as decisões locais de autorização. O principal risco está no
vínculo de contas: e-mails podem mudar, ser reciclados ou não ter sido
verificados, portanto não são identificadores federados estáveis.

## Decisão

- integrar um conector OIDC genérico e configurável usando Authorization Code,
  `state`, `nonce` e PKCE fornecidos pelo Spring Security;
- exigir HTTPS nos endpoints do provedor, `openid` e `email` nos escopos e
  manter o client secret exclusivamente em configuração externa;
- usar o par imutável `registration_id + sub` como identidade federada;
- aceitar provisionamento automático somente quando o provedor devolve um
  e-mail confirmado e ainda não existe conta local com esse e-mail;
- exigir autenticação local prévia para vincular um provedor a uma conta
  existente, mesmo quando os e-mails coincidirem;
- substituir o principal externo por uma identidade local ativa antes de
  retomar a interação OAuth do Identity Hub;
- manter o desafio TOTP local quando ele estiver habilitado para a identidade;
- impedir a remoção do último vínculo de uma conta sem credencial local;
- manter papéis, memberships, consentimentos e tokens sob autoridade local;
- auditar vínculo, remoção e autenticação federada sem persistir tokens do
  provedor.

## Consequências

- provedores OIDC compatíveis podem ser trocados por configuração, mas a fatia
  atual habilita uma inscrição por implantação;
- contas provisionadas pelo provedor não recebem automaticamente memberships
  ou privilégios administrativos;
- redefinir uma senha passa a criar uma credencial local para uma conta
  originalmente federada;
- o callback exato precisa ser registrado no provedor e refletir corretamente
  proxy reverso, esquema HTTPS, host e `registration_id`;
- tokens externos são usados somente durante a cerimônia OIDC e não se tornam
  tokens aceitos pelas APIs do Identity Hub.

## Alternativas consideradas

### Vincular automaticamente por e-mail

Rejeitada porque transforma controle ou reciclagem de um endereço em tomada de
uma conta local já existente.

### Usar o e-mail como chave federada

Rejeitada porque o identificador estável definido pelo OIDC é `sub`, no escopo
do issuer/provedor configurado.

### Aceitar access token externo nas APIs locais

Rejeitada porque confundiria fronteiras de issuer, audience, escopo e
autorização local.

## Evidências exigidas

- migração durável com unicidade por provedor e sujeito;
- redirecionamento OIDC com `state`, `nonce` e PKCE;
- provisionamento apenas com e-mail confirmado;
- rejeição de vínculo silencioso e de acesso horizontal;
- interface de login, vínculo e remoção;
- testes backend e build Angular verdes.
