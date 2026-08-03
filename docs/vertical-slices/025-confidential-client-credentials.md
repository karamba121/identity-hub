# Fatia vertical 025 — cliente confidencial e Client Credentials

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 002, 003, 004 e 005

## Capacidade entregue

Administradores com `oauth.clients.manage` podem cadastrar, no tenant ativo,
um cliente confidencial destinado à integração entre sistemas. A resposta de
criação apresenta um `client_secret` aleatório de 256 bits uma única vez. As
consultas e atualizações posteriores nunca devolvem esse valor.

O registro padrão do Spring Authorization Server persiste somente a codificação
Argon2id do segredo. O cliente recebe exclusivamente:

- autenticação `client_secret_basic`;
- grant `client_credentials`;
- escopo de máquina `demo.read`;
- access token de cinco minutos, sem refresh token;
- sujeito `client:<clientId>`, claim `client_id` e audience
  `identity-hub-api`.

Clientes públicos continuam sem segredo, limitados a Authorization Code e
Refresh Token, com PKCE obrigatório. O tipo do cliente não pode ser convertido
por uma atualização administrativa.

## Caminho vertical

- o CRUD tenant-aware diferencia clientes `PUBLIC` e `CONFIDENTIAL` sem criar
  uma segunda fonte de verdade;
- a validação rejeita redirect URIs e escopos humanos ou administrativos no
  cliente confidencial;
- a tela Angular permite escolher o tipo durante a criação e exibe um aviso
  destacado para copiar o segredo naquele momento;
- listagem e edição exibem apenas o tipo, nunca o segredo;
- o customizador de JWT preserva a audience derivada do escopo e explicita o
  sujeito de máquina.

## Evidências executadas

- teste HTTP cria o cliente confidencial e confirma que o valor persistido não
  é o segredo bruto e corresponde ao hash Argon2id;
- nova consulta ao cliente comprova a ausência de `clientSecret`;
- troca real em `/oauth2/token` comprova grant, escopo, ausência de refresh
  token, sujeito, `client_id` e audience;
- teste negativo comprova que um cliente público não autentica no grant Client
  Credentials;
- a rejeição de audience incorreta pelo resource server permanece coberta pela
  suíte existente;
- suíte completa do backend e build Angular verificados ao concluir a fatia.

## Limites ainda abertos

- rotação de client secrets com janela controlada ainda não foi implementada;
- segredo inválido e solicitação de escopo excessivo ainda precisam de uma
  matriz negativa dedicada;
- não foi publicado exemplo independente de resource server;
- os testes usam H2 em modo PostgreSQL; não houve execução contra PostgreSQL
  real nem exercício em navegador nesta fatia.
