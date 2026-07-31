# Fatia vertical 002 — API protegida com audience e escopo

- **Estado:** entregue
- **Data:** 2026-07-31
- **ADRs exercitadas:** 002, 003, 005 e 006

## Capacidade entregue

O cliente Angular público solicita o escopo `demo.read` durante Authorization
Code com PKCE. O Authorization Server inclui a audience `identity-hub-api` no
access token autorizado e o cliente usa esse token para consumir
`GET /api/v1/demo/resource`.

A API é uma superfície de resource server separada das cadeias de segurança do
Authorization Server e das interações baseadas em sessão. Ela não aceita o
cookie de login como credencial: exige bearer token assinado, não expirado, com
issuer esperado, audience `identity-hub-api` e autoridade `SCOPE_demo.read`.

## Evolução de dados

A migração `V2__demo_resource_scope.sql` acrescenta `demo.read` ao cliente
demonstrativo em bancos já provisionados. Em bancos limpos, o bootstrap de
desenvolvimento cadastra o cliente com o escopo. O bootstrap também reconcilia
clientes existentes quando estiver habilitado, sem criar um segundo registro.

Nenhuma tabela de domínio foi adicionada: clientes e escopos continuam na
persistência JDBC padrão do Spring Authorization Server.

## Contratos

- scope OAuth: `demo.read`;
- audience do access token: `identity-hub-api`;
- recurso protegido: `GET /api/v1/demo/resource`;
- autenticação: `Authorization: Bearer <access_token>`.

A resposta apresenta uma mensagem de sucesso, o subject, a audience e os
escopos efetivamente validados. Ela não devolve o token recebido.

## Evidências verificadas

- fluxo Authorization Code + PKCE emite token e consome UserInfo e a API;
- ausência de bearer token retorna `401`;
- token assinado para outra audience retorna `401`;
- token para a audience correta sem `demo.read` retorna `403`;
- migrações V1 e V2 aplicadas em sequência nos testes;
- suíte backend com cinco testes aprovados;
- build de produção Angular aprovado.

## Limitações conhecidas

- o resource server está no mesmo processo para preservar o monólito modular;
- a chave RSA permanece efêmera nesta etapa;
- refresh token, revogação e logout pertencem à próxima fase;
- ainda não há audience específica por tenant ou por cliente máquina a máquina;
- o cliente Angular continua sendo demonstrativo, não uma biblioteca OIDC
  certificada.
