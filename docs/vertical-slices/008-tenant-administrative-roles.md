# Fatia vertical 008 — papéis administrativos por tenant

## Capacidade entregue

O Identity Hub agora possui papéis administrativos pertencentes a um tenant.
Cada papel tem código e nome próprios dentro da organização e reúne permissões
do catálogo versionado. Uma membership pode receber um papel do mesmo tenant.

A migração V6 cria `tenant_role` e `tenant_role_permission`, adiciona `role_id`
à membership e estabelece uma chave estrangeira composta por papel e tenant.
Assim, uma atribuição cruzada é rejeitada pelo domínio e também pelo banco.

## Fluxo demonstrável

No ambiente de desenvolvimento, o bootstrap cria ou reconcilia o papel de
sistema `administrator`, concede as cinco permissões do catálogo e o atribui à
membership do usuário demonstrativo.

Depois da autenticação, `GET /api/v1/demo/tenants` devolve, para cada vínculo do
sujeito, o papel efetivo e seus códigos de permissão. O cliente Angular apresenta
essas concessões junto à organização e mantém o catálogo completo em uma seção
separada, sem confundir capacidade disponível com acesso concedido.

## Segurança e evidências

- o código do papel é único apenas dentro do tenant;
- a chave estrangeira composta impede papel de outro tenant na membership;
- a consulta parte do sujeito autenticado e não aceita tenant arbitrário;
- quatro testes cobrem bootstrap, concessões efetivas, isolamento entre tenants,
  unicidade e rejeição de atribuição cruzada;
- Flyway e `ddl-auto=validate` exercitam a migração V6 durante os testes.

## Limites ainda abertos

- cada membership possui no máximo um papel nesta fatia;
- ainda não existe CRUD administrativo de papéis ou atribuições;
- as APIs administrativas ainda não aplicam permission checks;
- o bootstrap ainda não possui proteção concorrente para o primeiro
  administrador nem regra do último administrador válido;
- a aplicação da V6 em PostgreSQL real depende de uma execução do ambiente
  Compose.
