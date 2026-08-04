# Fatia vertical 036 — Passkeys WebAuthn

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADR exercitada:** 015
- **Referência normativa:** W3C Web Authentication Level 2

## Capacidade entregue

- a migração V18 cria as tabelas duráveis de usuários WebAuthn e credenciais,
  com vínculo e exclusão em cascata;
- o Spring Security gera e valida as opções e respostas WebAuthn por meio do
  WebAuthn4J, exigindo credencial descobrível e verificação local do usuário;
- o backend valida RP ID e origens permitidas na inicialização e rejeita
  configuração incompleta;
- o login TailAdmin oferece passkey antes da senha, usa a API nativa do
  navegador e só retoma a interação OAuth após receber uma autoridade WebAuthn
  na mesma sessão;
- o perfil permite cadastrar, nomear, listar e remover somente as passkeys da
  identidade corrente;
- cadastro, remoção e autenticação integram a trilha de segurança já existente;
- endpoints públicos de início e conclusão da autenticação compartilham o rate
  limiting de login;
- contas desabilitadas, bloqueadas, expiradas ou sem usuário persistido são
  rejeitadas antes da atualização criptográfica da credencial.

## Contratos HTTP

- `POST /webauthn/register/options` cria as opções de cadastro autenticado;
- `POST /webauthn/register` valida e persiste a credencial;
- `POST /webauthn/authenticate/options` cria as opções públicas de login;
- `POST /login/webauthn` valida a assinatura e autentica a sessão;
- `GET /api/v1/passkeys` lista as credenciais da identidade autenticada;
- `DELETE /api/v1/passkeys/{credentialId}` remove uma credencial própria;
- `POST /api/v1/interactions/{interactionId}/passkey` retoma a autorização
  opaca depois da cerimônia válida.

Todos os endpoints mutáveis permanecem protegidos por CSRF. A remoção nativa
do Spring foi desabilitada para que nenhuma exclusão contorne propriedade e
auditoria da API do Identity Hub.

## Evidências verificadas

- Flyway aplica as 18 migrações no H2 em modo PostgreSQL;
- testes de integração verificam opções WebAuthn, isolamento de remoção,
  exigência da autoridade de fator na interação e rejeição de cerimônia
  ausente;
- compilação e suíte completa do backend, build Angular, configuração Compose e
  whitespace são verificados ao concluir a fatia.

## Limites ainda abertos

- a cerimônia não foi exercitada neste ciclo com navegador e autenticador
  físicos reais;
- a migração V18 não foi aplicada a uma instância PostgreSQL real;
- attestation permanece sem política empresarial própria; a autenticação usa a
  validação padrão da biblioteca;
- RP ID e origens de produção precisam ser definidos antes do deploy e devem
  corresponder exatamente ao domínio público servido por HTTPS;
- recuperação de conta, federação externa e políticas adaptativas continuam
  itens separados do roadmap.
