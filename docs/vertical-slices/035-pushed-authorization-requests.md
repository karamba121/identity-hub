# Fatia vertical 035 — Pushed Authorization Requests (PAR)

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADRs exercitadas:** 002, 003 e 008
- **Referência normativa:** RFC 9126

## Threat model e decisão

Os fluxos Authorization Code da SPA demonstrativa e da área administrativa
enviavam `scope`, `state`, `nonce`, `redirect_uri` e o desafio PKCE pelo front
channel. Mesmo protegidos por HTTPS em produção, esses parâmetros podiam ser
copiados para histórico, telemetria de proxy e ferramentas do navegador, além
de ampliar a superfície para adulteração antes da validação pelo servidor.

PAR foi adotado porque reduz esse conjunto a `client_id` e uma referência
opaca, curta e expirável. PKCE, `state`, `nonce` e comparação exata de
`redirect_uri` continuam obrigatórios: PAR complementa essas proteções, não as
substitui. O cliente público autentica-se como `none`, pois não pode guardar um
segredo; sua prova no token endpoint continua sendo o `code_verifier`.

Não há, nesta fatia, evidência que justifique outra extensão pós-MVP. JAR,
RAR, DPoP ou mTLS só devem entrar com ameaça, interoperabilidade e custo
operacional documentados, evitando acumular protocolo sem consumidor real.

## Capacidade entregue

- `POST /oauth2/par` usa a implementação do Spring Authorization Server e é
  publicado em `pushed_authorization_request_endpoint` no metadata RFC 8414;
- clientes confidenciais usam os métodos de autenticação já suportados; o
  cliente público é aceito somente quando está cadastrado com método `none` e
  Authorization Code;
- parâmetros são validados antes da persistência, inclusive PKCE obrigatório
  para clientes públicos, redirect URI e escopos cadastrados;
- a resposta `201 Created` contém `request_uri` opaca e `expires_in`, com vida
  padrão de 300 segundos;
- a referência fica vinculada ao cliente, expira, rejeita adulteração e é
  removida após o primeiro consumo válido;
- o adaptador de interação TailAdmin resolve o contexto persistido sem recolocar
  os parâmetros sensíveis na URL de login;
- os fluxos demonstrativo e administrativo do Angular fazem PAR e falham
  fechados quando a referência não pode ser criada.

## Evidências verificadas

- teste de integração cobre metadata, criação por cliente público e início do
  fluxo usando somente `client_id` e `request_uri` no front channel;
- testes negativos cobrem ausência de PKCE, vínculo a outro cliente,
  adulteração, expiração com remoção e reutilização;
- compilação do backend, suíte completa, build Angular e whitespace são
  executados ao concluir a fatia.

## Limites ainda abertos

- o endpoint e a persistência usam os contratos nativos da versão atual do
  Spring Authorization Server; uma atualização deve reexecutar a matriz PAR;
- não houve exercício em navegador real, proxy externo ou PostgreSQL real;
- logs e proxies externos ainda precisam manter query strings e corpos OAuth
  fora da telemetria; PAR reduz a exposição no front channel, mas não corrige
  observabilidade externa mal configurada;
- alta disponibilidade e testes de caos permanecem itens posteriores do
  roadmap.
