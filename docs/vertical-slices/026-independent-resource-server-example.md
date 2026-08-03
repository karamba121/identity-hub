# Fatia vertical 026 — exemplo de resource server independente

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 002, 004 e 005

## Capacidade entregue

O repositório passa a publicar um serviço Spring Boot independente em
`examples/resource-server`. Ele não compartilha banco, sessão ou classes de
domínio com o Authorization Server e aceita somente access tokens que atendem
simultaneamente a quatro controles:

- assinatura RSA válida, verificada pelas chaves públicas do JWK Set;
- issuer exatamente igual ao configurado;
- audience `identity-hub-api`;
- escopo `demo.read` no endpoint `GET /api/v1/messages`.

O Compose executa o exemplo na porta `8081`. O issuer continua sendo a URL
pública do Identity Hub, enquanto o endereço do JWK Set usa a rede interna do
Compose. Essa separação evita confundir o valor contratual de `iss` com o
endereço de transporte usado para buscar chaves.

## Evidências automatizadas

- audience esperada é aceita e outra audience produz `invalid_token`;
- endpoint sem bearer token responde `401`;
- token decodificado com `demo.read` acessa o endpoint e preserva o sujeito de
  máquina;
- token sem o escopo exigido responde `403`;
- falha de validação do decoder responde `401`;
- testes do exemplo, suíte completa do backend, build Angular e validação do
  Compose executados ao concluir a fatia.

## Uso demonstrado

O README do exemplo documenta as variáveis necessárias e um fluxo PowerShell
completo: autenticação `client_secret_basic`, troca por token em
`/oauth2/token` e chamada ao resource server com bearer token. O documento
também explicita que client secrets pertencem a um cofre e não ao repositório,
arquivos de configuração ou logs.

## Limites ainda abertos

- não foi executado um fluxo vivo entre os containers nesta fatia;
- não há cache ou política operacional específica para indisponibilidade do JWK
  Set além do comportamento padrão da biblioteca;
- rotação das chaves de assinatura e de client secrets permanece planejada;
- os testes do exemplo substituem o decoder no teste HTTP; a validação isolada
  de audience e a composição do decoder de produção são verificadas em níveis
  distintos.
