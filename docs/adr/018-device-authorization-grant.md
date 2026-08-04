# ADR 018 — Device Authorization Grant com consentimento no navegador

- **Status:** aceito
- **Data:** 2026-08-04

## Contexto

TVs, terminais e outros dispositivos com entrada limitada não conseguem proteger
um secret nem executar com segurança o redirecionamento e o PKCE usados pelos
clientes web. Compartilhar senha com esses dispositivos ampliaria a superfície de
ataque e impediria que o usuário conferisse claramente o pedido.

## Decisão

Adotar o OAuth 2.0 Device Authorization Grant da RFC 8628, mantendo um tipo de
cliente `DEVICE` separado dos clientes públicos web e confidenciais de máquina.

- o cliente não possui secret nem redirect URI;
- somente escopos delegados de usuário são aceitos;
- device code e user code expiram em dez minutos e são persistidos pelo serviço
  de autorizações existente;
- autenticação e consentimento acontecem no navegador, usando a sessão e as
  proteções de identidade já existentes;
- o usuário confere código, cliente e escopos antes de aprovar ou recusar;
- o access token dura cinco minutos e o device code não emite refresh token;
- a troca é de uso único, e pedidos pendentes ou recusados retornam erros OAuth
  padronizados sem expor credenciais ao dispositivo.

## Consequências

Dispositivos limitados ganham um fluxo interoperável sem armazenar senha ou
secret. A operação passa a depender de uma URL pública da interface configurada
corretamente e de o cliente respeitar o intervalo de polling recomendado pela
RFC. A tela de consentimento possui uma rota própria porque o estado e os
parâmetros do Device Grant diferem do Authorization Code.

## Alternativas consideradas

- reutilizar Authorization Code com PKCE: inadequado para dispositivos sem
  navegador ou teclado completos;
- senha ou código proprietário: rejeitado por acoplar o dispositivo à
  credencial do usuário e perder interoperabilidade;
- cliente confidencial embarcado: rejeitado porque um secret distribuído em
  firmware ou aplicativo público não permanece confidencial.

## Evidências exigidas

- emissão de `device_code`, `user_code`, URI de verificação e expiração;
- `authorization_pending` antes da decisão do usuário;
- login opaco para navegador sem sessão e consentimento explícito;
- emissão de access token somente após aprovação e rejeição de reuso;
- cadastro administrativo de cliente `DEVICE` sem secret ou redirect URI;
- build do frontend e suíte automatizada do backend.
