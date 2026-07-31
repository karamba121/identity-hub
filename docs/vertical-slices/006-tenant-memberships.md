# Fatia vertical 006 — tenants e memberships

## Capacidade entregue

O Identity Hub agora possui a primeira fundação multitenant executável:

1. a migração V4 cria tenants e memberships duráveis;
2. o bootstrap de desenvolvimento cria uma organização demonstrativa;
3. o usuário demonstrativo recebe uma membership ativa nessa organização;
4. um access token com audience e escopo válidos consulta
   `GET /api/v1/demo/tenants`;
5. o backend usa o `sub` validado do token para localizar somente os vínculos
   ativos desse usuário;
6. o cliente Angular apresenta as organizações autorizadas após o login.

Nenhum cabeçalho informado pelo navegador é aceito como prova de tenant ou de
membership. O issuer continua canônico e único, conforme o ADR-004.

## Persistência e invariantes

A tabela `tenant` possui slug global normalizado e único, nome de apresentação,
estado e instante de criação. A tabela `tenant_membership` referencia tenant e
identidade global, possui estado próprio e garante unicidade pelo par
`(tenant_id, user_id)`.

Índices por usuário/estado e tenant/estado sustentam tanto a resolução dos
vínculos da pessoa quanto as futuras operações administrativas. Tenants ou
memberships suspensos não aparecem no contexto retornado.

## Segurança e evidências

- o endpoint exige bearer token emitido pelo issuer esperado;
- o token precisa ter a audience `identity-hub-api` e o escopo `demo.read`;
- o sujeito vem do `sub` assinado, não de parâmetro ou cabeçalho livre;
- um usuário não recebe a membership pertencente a outro usuário;
- membership suspensa não é retornada;
- o banco rejeita uma segunda membership para o mesmo tenant e usuário;
- quatro testes de integração cobrem autenticação, escopo, isolamento e
  unicidade;
- a migração é validada pelo Flyway e pelo `ddl-auto=validate` durante os testes.

## Limites ainda abertos

- esta fatia lista vínculos, mas ainda não seleciona ou troca o tenant ativo;
- tenant e membership ainda não possuem CRUD administrativo;
- papéis, catálogo de permissões e proteção do primeiro/último administrador
  permanecem nos próximos itens da Fase 5;
- tokens ainda não recebem claim de tenant porque não existe seleção ativa
  validada;
- a suíte usa H2 em modo PostgreSQL; aplicação da V4 em PostgreSQL real ainda
  depende de uma execução do ambiente Compose.
