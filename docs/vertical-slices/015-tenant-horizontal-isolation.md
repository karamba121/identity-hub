# Fatia vertical 015 — isolamento horizontal entre tenants

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADR exercitada:** 004

## Capacidade comprovada

A suíte `TenantHorizontalIsolationIntegrationTests` reúne a matriz negativa da
Fase 5 e exercita o isolamento desde o contexto autenticado até os recursos
administrativos. Ela cria dois tenants independentes em cada cenário e usa
tokens assinados com as audiences e os escopos reais das APIs.

As verificações cobrem:

- usuário comum observa somente as próprias memberships ativas;
- administrador com todas as permissões no tenant de origem recebe `403` ao
  consultar ou alterar clientes OAuth, memberships e auditoria do tenant alvo;
- a listagem autorizada de clientes contém somente ownerships do tenant da
  rota;
- identificador de cliente estrangeiro usado sob uma rota autorizada recebe
  `404` em leitura, atualização e remoção;
- papel estrangeiro usado em uma membership do tenant autorizado recebe `404`;
- identidade de cliente OAuth, sem membership humana, não herda acesso
  administrativo mesmo com um token assinado contendo audience e scope da API;
- clientes e memberships permanecem inalterados depois das tentativas negadas.

## Barreiras exercitadas

A suíte separa deliberadamente duas camadas. `TenantPermissionAuthorizer`
valida usuário, tenant, status e permissão antes de uma operação administrativa.
Depois dessa autorização, serviços e repositórios ainda consultam ownership,
membership e papel por chaves compostas com o tenant. Assim, conhecer um UUID ou
client ID externo não permite contornar o isolamento usando uma rota própria.

O contexto de usuário parte exclusivamente do `sub` validado e não aceita um
tenant informado pelo chamador. Da mesma forma, a identidade global do cliente
OAuth não constitui membership nem concessão administrativa.

## Evidências executadas

- quatro cenários integrados passaram com Spring Boot, MockMvc, JPA e H2;
- as nove migrações Flyway foram aplicadas do zero pelo perfil de teste;
- foram exercitados `GET`, `PUT`, `POST` e `DELETE` nas superfícies
  tenant-scoped existentes;
- os testes verificam tanto os códigos HTTP quanto a preservação do estado
  persistido após tentativas cruzadas.

## Limites ainda abertos

- a suíte automatizada usa H2; o isolamento e as migrações não foram
  reexecutados em PostgreSQL real nesta fatia;
- não houve exercício em navegador, pois a fatia testa as fronteiras HTTP e de
  persistência e não altera a interface;
- seleção ativa de tenant em tokens para resource servers tenant-aware continua
  dependente de uma fatia futura, conforme o ADR-004.
