# Fatia vertical 011 — autorização administrativa por tenant

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 001, 002, 004, 005 e 006

## Capacidade entregue

O Identity Hub expõe as primeiras operações administrativas de membership sem
confiar apenas em um scope do token ou em um `tenantId` fornecido pelo cliente.
Um administrador autorizado pode atribuir papel, suspender ou remover uma
membership do próprio tenant. As operações continuam centralizadas no
`TenantMembershipAdministrationService`, preservando a proteção transacional do
último administrador válido.

## Contratos

As operações usam a base
`/api/v1/admin/tenants/{tenantId}/memberships/{membershipId}`:

- `PUT /role`, com `{"roleId":"..."}`, atribui um papel do mesmo tenant;
- `POST /suspend` suspende a membership;
- `DELETE /` remove a membership.

Sucesso retorna `204`. Requisição inválida retorna `400`, recurso que não
pertence ao tenant retorna `404`, e uma operação que eliminaria o último
administrador válido retorna `409` com `ProblemDetail`.

## Autorização em camadas

Antes de alcançar o caso de uso, o access token precisa:

1. ter assinatura e issuer válidos;
2. possuir audience `identity-hub-admin-api`;
3. possuir scope `identity.admin`.

Depois da validação do token, o backend resolve pelo `sub` uma membership ativa
em tenant ativo. O usuário deve estar habilitado, possuir papel e receber por
esse papel a permissão `tenant.access.manage`. O tenant da rota participa da
consulta; um administrador de outra organização recebe `403`, mesmo com token,
audience e scope válidos.

O cliente público de desenvolvimento passa a conhecer o scope administrativo,
mas o scope não concede sozinho nenhuma operação: as permissões permanecem no
banco e são avaliadas a cada comando.

## Persistência

Esta fatia não cria migração. Memberships, papéis, permissões e vínculos já são
persistidos pelas migrações V4 a V6, com chaves compostas que impedem atribuir um
papel de outro tenant. Criar uma tabela ou coluna sem novo estado durável seria
uma migração artificial.

## Evidências executadas

- requisição sem bearer token é rejeitada com `401`;
- audience administrativa incorreta é rejeitada com `401`;
- ausência do scope `identity.admin` é rejeitada com `403`;
- ator sem `tenant.access.manage` é rejeitado com `403`;
- ator que administra outro tenant é rejeitado com `403` e não altera o alvo;
- atribuição de papel, suspensão e remoção autorizadas retornam `204` e
  persistem o resultado;
- remoção do último administrador retorna `409` e preserva a membership;
- testes anteriores de concorrência e integridade de papéis continuam verdes.

## Limites ainda abertos

- a API ainda não lista memberships e papéis nem cria novas memberships;
- não existe interface Angular administrativa para essas operações;
- auditoria append-only de sucesso e negação permanece pendente;
- o cliente público demonstrativo ainda não solicita o scope administrativo;
- os testes automatizados usam H2 em modo PostgreSQL; a execução concorrente e
  as migrações em PostgreSQL real continuam como validação operacional separada.
