# ADR-008: TailAdmin como interface de login e consentimento

- **Status:** aceito
- **Data:** 2026-07-30

## Contexto

O repositório possui um backend Spring Boot com Spring Security e Spring
Authorization Server e um frontend Angular baseado no TailAdmin. Login e
consentimento fazem parte da experiência do servidor de autorização, mas não
precisam ser renderizados pelo backend para que este continue sendo a autoridade
de segurança.

Uma SPA não é uma fronteira confiável. Se o frontend receber o pedido OAuth
integral e puder devolver `client_id`, `redirect_uri` ou escopos alterados, a
aparência ficará desacoplada das garantias do protocolo. Também é necessário
distinguir a UI de interação do SSO de um cliente OAuth demonstrativo: a
primeira autentica a pessoa para o servidor; o segundo solicita uma autorização
como relying party.

## Decisão

As telas oficiais de login, MFA, recuperação e consentimento serão
implementadas no frontend Angular/TailAdmin, usando o layout público de
autenticação e sem navegação administrativa.

O backend continuará responsável por:

- receber e validar o pedido de autorização;
- criar e manter o contexto completo da interação;
- autenticar credenciais e fatores adicionais;
- manter a sessão do servidor de autorização;
- resolver cliente, tenant e escopos permitidos;
- registrar aprovação ou recusa;
- emitir a resposta OAuth/OIDC e efetuar o redirect final.

O frontend receberá apenas um `interaction_id` opaco, curto e de uso
controlado. Por meio dele, consultará dados de apresentação já validados e
submeterá credenciais ou decisão. A submissão não poderá redefinir cliente,
redirect URI ou escopos.

Em produção, frontend e backend deverão preferencialmente ser publicados sob a
mesma origem externa. A sessão será mantida por cookie `HttpOnly`, `Secure` e
com política `SameSite` compatível com o fluxo validado. Requisições mutáveis
terão proteção CSRF. No desenvolvimento, o proxy do Angular encaminhará rotas
do backend, preservando o mesmo modelo de contrato.

O cliente Angular demonstrativo será uma superfície lógica separada. Ele poderá
iniciar Authorization Code com PKCE e manter seu próprio `state`, `nonce` e
verifier, mas esses valores não pertencem às telas internas de login e
consentimento.

## Consequências

- login e consentimento seguem a identidade visual e os componentes TailAdmin;
- o backend não precisa renderizar páginas HTML de autenticação;
- será necessário definir APIs internas de interação e uma retomada segura do
  pedido de autorização;
- sessão, CSRF, proxy e redirecionamentos precisam ser testados no navegador;
- o frontend não pode ser a fonte de verdade dos parâmetros OAuth;
- expiração ou reutilização de uma interação deve produzir erro seguro e
  reinício controlado do fluxo;
- áreas de interação, administração e cliente demonstrativo devem ter limites
  explícitos mesmo que compartilhem o mesmo projeto Angular.

## Alternativas consideradas

### Páginas renderizadas pelo Spring

É uma alternativa válida e mais próxima do comportamento padrão da biblioteca,
mas foi rejeitada para este projeto porque duplicaria a camada visual e deixaria
login e consentimento fora do frontend TailAdmin escolhido.

### Pedido OAuth integral armazenado no navegador

Rejeitado porque aumentaria exposição de dados e permitiria que parâmetros
controlados pelo cliente retornassem como se fossem decisões confiáveis.

### Frontend emitir a resposta OAuth

Rejeitado. Somente o backend possui contexto, sessão, chaves e autoridade para
emitir códigos e tokens.

## Evidências exigidas

- teste de navegador cobrindo login, consentimento, recusa e retorno ao cliente;
- interação expirada, reutilizada ou pertencente a outra sessão rejeitada;
- alteração de parâmetros no navegador incapaz de trocar cliente, redirect URI
  ou escopos;
- credenciais processadas apenas pelo backend e nunca persistidas no frontend;
- cookie inacessível ao JavaScript em produção;
- proteção CSRF verificada nas operações mutáveis;
- logs, traces e histórico de navegação sem senha, token, code, verifier ou
  material de sessão.
