# ADR-015: Passkeys WebAuthn para autenticação sem senha

- **Status:** aceito
- **Data:** 2026-08-04

## Contexto

Senha e TOTP continuam úteis, mas são vulneráveis a phishing e exigem que o
servidor proteja segredos verificadores. A evolução pós-MVP prevê uma credencial
resistente a phishing, integrada à interação OAuth existente e gerenciável pela
própria identidade sem transformar o Angular em autoridade de autenticação.

## Decisão

- usar WebAuthn com credenciais descobríveis (`residentKey=required`) para
  permitir login sem informar o e-mail;
- exigir verificação local do usuário (`userVerification=required`) no cadastro
  e na autenticação;
- vincular as cerimônias ao RP ID e ao conjunto explícito de origens
  configuradas, falhando na inicialização quando a configuração estiver
  incompleta;
- persistir identificador, chave pública, contador, estado de backup,
  transportes e metadados da credencial, nunca biometria ou chave privada;
- conferir o estado ativo e não bloqueado da identidade antes da validação
  criptográfica e da atualização do contador;
- concluir a interação OAuth somente quando a sessão corrente possuir a
  autoridade de fator WebAuthn criada pela cerimônia;
- permitir listar e remover apenas credenciais da identidade autenticada, com
  erro uniforme para identificadores inexistentes ou pertencentes a terceiros;
- auditar cadastro, remoção e autenticação, e aplicar o limite existente aos
  endpoints públicos da cerimônia.

## Consequências

- autenticadores de plataforma e chaves de segurança compatíveis podem
  substituir a senha no login;
- trocar domínio, RP ID ou origem exige planejamento porque credenciais já
  cadastradas são vinculadas ao RP ID;
- cadastro requer uma sessão autenticada existente; a passkey não substitui o
  processo de recuperação de conta;
- uma falha de auditoria após a prova criptográfica invalida a sessão e devolve
  indisponibilidade, preservando a política fail-closed;
- navegadores exigem contexto seguro; `localhost` é a exceção destinada ao
  desenvolvimento.

## Alternativas consideradas

### Credenciais não descobríveis

Rejeitadas nesta fatia porque exigiriam identificar o usuário antes da
cerimônia e não ofereceriam o login passkey-first definido para a interface.

### Verificação do usuário apenas preferencial

Rejeitada porque permitiria autenticadores sem PIN, biometria ou desbloqueio
local, reduzindo a passkey a uma prova de posse.

### Implementação criptográfica própria

Rejeitada em favor dos contratos WebAuthn do Spring Security e WebAuthn4J,
mantendo no projeto somente política, integração, persistência e auditoria.

## Evidências exigidas

- migração Flyway para usuários WebAuthn e credenciais;
- opções de cadastro e autenticação com verificação obrigatória;
- login, cadastro, listagem e remoção no Angular;
- isolamento de credenciais entre identidades e interação opaca exigindo o
  fator WebAuthn;
- rejeição de cerimônia ausente ou inválida;
- suíte backend e build frontend verdes.
