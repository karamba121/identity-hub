# ADR-003: Tipos de cliente e PKCE

- **Status:** aceito
- **Data:** 2026-07-30

## Contexto

SPAs, backends web e serviços automatizados têm capacidades de proteção
diferentes. Um segredo embutido em JavaScript distribuído ao navegador não é
segredo. Tratar todos os clientes da mesma forma cria autenticação ilusória e
abre espaço para interceptação de códigos e confusão de redirect URI.

## Decisão

O registro de cliente distinguirá explicitamente:

- **cliente público:** não possui client secret e usa Authorization Code com
  PKCE `S256`;
- **cliente confidencial:** autentica-se no token endpoint por método
  compatível com sua capacidade de proteger credenciais;
- **cliente máquina a máquina:** é confidencial, usa Client Credentials e
  recebe apenas escopos previamente concedidos.

Redirect URIs serão previamente registradas e comparadas de forma exata.
Curingas, prefixos e redirect URI dinâmica não serão aceitos.

Authorization Code será curto, de uso único e vinculado ao cliente, redirect URI
e code challenge. `state` e `nonce` serão exigidos e validados nos fluxos em que
se aplicam.

Client secrets serão exibidos somente na criação ou rotação e armazenados com
proteção não reversível. A rotação permitirá sobreposição controlada quando
necessária.

## Consequências

- o cliente Angular não contém secret;
- PKCE não substitui validação de state, nonce, issuer ou redirect URI;
- cada grant precisa estar habilitado por cliente;
- registro de clientes exige validações mais rigorosas;
- integrações legadas incompatíveis não serão aceitas silenciosamente.

## Alternativas consideradas

### Um secret compartilhado com a SPA

Rejeitado porque qualquer usuário pode recuperar o valor distribuído ao
navegador.

### Redirect URI com curinga

Rejeitada pelo risco de redirecionamento e exfiltração de códigos.

### PKCE opcional

Rejeitado para clientes públicos; o projeto adota `S256` como requisito.

## Evidências exigidas

- teste demonstrando que cliente público não autentica com secret;
- rejeição de PKCE ausente, método inadequado e verifier inválido;
- rejeição de redirect URI semelhante, mas não idêntica;
- code não reutilizável;
- secret nunca retornado novamente nem presente em logs ou banco em claro.
