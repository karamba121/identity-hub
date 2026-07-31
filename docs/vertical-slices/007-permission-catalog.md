# Fatia vertical 007 — catálogo explícito de permissões

## Capacidade entregue

O Identity Hub agora mantém um catálogo versionado das capacidades que serão
usadas pelos papéis administrativos das próximas fatias. A migração V5 cria e
semeia cinco permissões:

- `tenant.access.read`;
- `tenant.access.manage`;
- `oauth.clients.read`;
- `oauth.clients.manage`;
- `security.audit.read`.

Cada definição possui código estável, nome, descrição, categoria e ordem de
apresentação. Os mesmos códigos existem no enum `PermissionCode`; um teste de
integração impede que a lista Java e os registros da migração divirjam.

## Fluxo demonstrável

Um access token com issuer, audience e escopo válidos consulta
`GET /api/v1/demo/permission-catalog`. O backend lê as definições imutáveis do
banco e devolve o catálogo ordenado. O cliente Angular o apresenta após carregar
o perfil, a API protegida e as organizações autorizadas.

A resposta informa explicitamente que o catálogo descreve capacidades
disponíveis e não permissões concedidas. Nenhum campo `granted`, papel ou
membership é inferido nessa etapa.

## Segurança e evidências

- o endpoint exige bearer token para `identity-hub-api`;
- o escopo `demo.read` continua obrigatório;
- a resposta não declara concessões de acesso;
- códigos são únicos pela chave primária e a ordem também é única;
- categorias são limitadas por constraint no banco e enum no domínio;
- três testes cobrem ausência de token, ausência de escopo e catálogo completo;
- Flyway e `ddl-auto=validate` exercitam a migração V5 durante os testes.

## Limites ainda abertos

- nenhuma permission está ligada a papel ou membership nesta fatia;
- o catálogo não é editável por API: mudanças exigem revisão de código e nova
  migração versionada;
- os permission checks das APIs administrativas ainda não existem;
- a aplicação da V5 em PostgreSQL real continua dependente de uma execução do
  ambiente Compose.
