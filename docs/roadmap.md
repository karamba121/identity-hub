# Roadmap

Este roadmap organiza o Identity Hub em fatias verticais demonstráveis. Ele não
é uma lista de funcionalidades alegadamente prontas.

## Legenda

- `[x]` concluído e verificado no nível indicado;
- `[ ]` planejado;
- `MVP` necessário para a primeira versão utilizável;
- `Evolução` capacidade posterior ao MVP.

## Fase 0 — Fundação documental

- [x] definir objetivo, escopo e princípios do projeto;
- [x] registrar a visão arquitetural inicial;
- [x] decidir monólito modular como primeira unidade de implantação;
- [x] registrar base de protocolos, clientes, tenancy, tokens, chaves,
  persistência e auditoria em ADRs;
- [x] definir fatias, critérios de aceite e evidências esperadas;
- [x] adicionar a licença MIT ao repositório.

## Fase 1 — Fundação executável `MVP`

- [x] adicionar o projeto backend Spring Boot 4.0.7 com Java 17;
- [x] adicionar ao backend as dependências de Spring Security, Spring
  Authorization Server, JPA, Flyway, PostgreSQL e Actuator;
- [x] adicionar o projeto frontend Angular 21 com o template TailAdmin;
- [ ] substituir nomes, metadados e conteúdo demonstrativo dos scaffolds pelo
  domínio do Identity Hub;
- [ ] configurar Spring Security e Spring Authorization Server;
- [ ] definir módulos, regras de dependência e testes arquiteturais;
- [ ] configurar PostgreSQL, Redis e execução local com Docker Compose;
- [ ] criar migrações versionadas e dados de desenvolvimento seguros;
- [ ] adicionar health, readiness, logs estruturados e OpenTelemetry básico;
- [ ] configurar build, testes e análise de dependências no GitHub Actions;
- [ ] documentar comandos locais sem versionar credenciais.

**Critério de aceite:** clone limpo inicia a infraestrutura e a aplicação por
instruções reproduzíveis; pipeline executa build e testes; nenhum segredo real
está no repositório.

## Fase 2 — Login, consentimento e Authorization Code `MVP`

- [ ] persistir usuários, clientes, autorizações e consentimentos;
- [ ] cadastrar um cliente público de demonstração sem client secret;
- [ ] definir no backend um contexto de interação opaco, curto e de uso
  controlado para preservar o pedido de autorização, conforme o
  [ADR-008](adr/008-tailadmin-authorization-interaction-ui.md);
- [ ] adaptar a página `sign-in` do TailAdmin como tela oficial de login do
  servidor de autorização;
- [ ] criar no TailAdmin a tela oficial de consentimento, exibindo cliente,
  tenant e escopos obtidos de uma API segura do backend;
- [ ] autenticar credenciais somente no backend e estabelecer sessão por cookie
  `HttpOnly`, `Secure` em produção e protegido contra CSRF;
- [ ] registrar no backend a aprovação ou a recusa do consentimento e retomar o
  fluxo OAuth/OIDC original;
- [ ] impedir que o frontend altere `client_id`, `redirect_uri`, escopos ou
  qualquer outro dado validado do pedido de autorização;
- [ ] exigir PKCE `S256` para cliente público;
- [ ] disponibilizar authorization, token, metadata, JWK Set e UserInfo;
- [ ] emitir access token e ID token com issuer e audience validados;
- [ ] rejeitar redirect URI não idêntica à cadastrada;
- [ ] testar interação expirada ou trocada, CSRF, code de uso único, state,
  nonce, PKCE inválido, consentimento negado e replay;
- [ ] documentar o fluxo e exemplos sem valores sensíveis.

**Critério de aceite:** o navegador inicia o pedido no endpoint de autorização,
usa as telas TailAdmin para login e consentimento e retorna ao cliente apenas
depois de o backend validar e registrar toda a decisão. Um teste de integração
percorre o fluxo completo e demonstra também os principais cenários negativos.

## Fase 3 — Superfícies Angular e cliente demonstrativo `MVP`

- [x] criar a base Angular com o design system TailAdmin;
- [ ] separar no frontend as áreas de interação OAuth, administração e cliente
  demonstrativo;
- [ ] usar o layout público de autenticação nas telas de login e consentimento,
  sem sidebar ou navegação administrativa;
- [ ] implementar no cliente demonstrativo o início do Authorization Code +
  PKCE;
- [ ] manter verifier, state e nonce apenas pelo tempo necessário;
- [ ] implementar callback com validação de state e nonce;
- [ ] consumir UserInfo e uma API protegida de demonstração;
- [ ] tratar expiração, logout e sessão inválida;
- [ ] preservar no retorno ao login o contexto opaco da interação, sem expor
  authorization code, token, verifier ou client secret;
- [ ] adaptar branding, estados de carregamento, erro, recusa e expiração ao
  padrão visual do TailAdmin;
- [ ] testar fluxo no navegador e acessibilidade básica.

**Critério de aceite:** o navegador autentica sem client secret, acessa um
resource server com audience correta e encerra a sessão de forma previsível.
Os testes também demonstram que a UI de login/consentimento e o cliente OAuth
demonstrativo exercem responsabilidades diferentes.

## Fase 4 — Sessões, refresh tokens e revogação `MVP`

- [ ] definir política de sessão, expiração e reautenticação;
- [ ] implementar refresh token apenas para clientes elegíveis;
- [ ] rotacionar refresh token a cada uso;
- [ ] detectar reutilização e revogar a família comprometida;
- [ ] disponibilizar revogação padronizada;
- [ ] implementar logout e encerramento de sessões;
- [ ] testar concorrência, replay, revogação e indisponibilidade parcial;
- [ ] expor métricas sem cardinalidade ou dados sensíveis excessivos.

**Critério de aceite:** testes concorrentes demonstram um único sucessor por
rotação e nenhuma sessão revogada volta a conceder acesso.

## Fase 5 — Tenants, RBAC e administração `MVP`

- [ ] persistir tenants e memberships com unicidade por tenant e usuário;
- [ ] criar catálogo explícito de permissões;
- [ ] implementar papéis administrativos por tenant;
- [ ] proteger o bootstrap do primeiro administrador;
- [ ] impedir remoção ou rebaixamento do último administrador válido;
- [ ] aplicar tenant e permission checks nas APIs administrativas;
- [ ] criar CRUD de clientes OAuth com redirect URIs e escopos;
- [ ] criar interface Angular administrativa;
- [ ] auditar alterações com ator, tenant, alvo e resultado;
- [ ] testar isolamento horizontal entre tenants.

**Critério de aceite:** uma suíte negativa comprova que usuário, administrador e
cliente de um tenant não observam nem alteram recursos de outro.

## Fase 6 — Credenciais e proteção contra abuso `MVP`

- [ ] cadastro e verificação de e-mail;
- [ ] política de senha e hash resistente;
- [ ] recuperação com token único, curto e armazenado de forma segura;
- [ ] bloqueio progressivo por tentativas inválidas;
- [ ] rate limiting por sinais combinados, sem depender só de IP;
- [ ] invalidação de sessões após eventos críticos;
- [ ] MFA TOTP opcional com códigos de recuperação;
- [ ] proteção e auditoria do ciclo de vida do MFA;
- [ ] testes de enumeração, brute force, replay e recuperação concorrente.

**Critério de aceite:** respostas públicas não permitem enumerar contas e os
testes demonstram expiração, uso único e revogação das credenciais temporárias.

## Fase 7 — Client Credentials e resource servers `MVP`

- [ ] cadastrar cliente confidencial com secret exibido uma única vez;
- [ ] persistir client secret somente com proteção não reversível;
- [ ] habilitar Client Credentials por concessão explícita;
- [ ] emitir token com sujeito de máquina, audience e escopos mínimos;
- [ ] criar resource server demonstrativo;
- [ ] publicar exemplo de validação de issuer, audience, assinatura e escopos;
- [ ] definir rotação de client secrets com janela controlada;
- [ ] testar segredo inválido, escopo excessivo e confusão de audience.

**Critério de aceite:** cliente público não consegue usar Client Credentials e
o resource server rejeita tokens destinados a outra audience.

## Fase 8 — Chaves, operação e resiliência `MVP`

- [ ] abstrair origem de chaves por ambiente;
- [ ] implementar geração e rotação planejada de chaves;
- [ ] publicar chave atual e chaves anteriores durante janela segura;
- [ ] testar tokens emitidos antes, durante e após rotação;
- [ ] definir backup, restauração e retenção de auditoria;
- [ ] criar painéis e alertas para autenticação, erro e abuso;
- [ ] executar testes de carga focados nos endpoints críticos;
- [ ] documentar runbooks de incidente, rotação e recuperação.

**Critério de aceite:** uma rotação exercitada não invalida prematuramente
tokens dentro da política, e o procedimento de recuperação possui evidência.

## Fase 9 — Evoluções pós-MVP

- [ ] PAR e outras extensões justificadas por threat model;
- [ ] passkeys/WebAuthn;
- [ ] federação com provedores externos;
- [ ] provisionamento SCIM;
- [ ] device authorization grant para dispositivos limitados;
- [ ] políticas adaptativas de autenticação;
- [ ] issuer por tenant, apenas se houver requisito comprovado;
- [ ] implantação altamente disponível e testes de caos;
- [ ] avaliação de extração de módulos, apenas com evidência operacional.

## Definição de pronto

Uma capacidade só pode ser marcada como concluída quando:

1. comportamento positivo e negativo está especificado;
2. modelo, migração e autorização foram tratados juntos quando aplicáveis;
3. testes proporcionais ao risco foram executados;
4. logs e auditoria não expõem segredos;
5. documentação e exemplos refletem o comportamento real;
6. limitações de validação externa estão declaradas;
7. a mudança não viola ADR aceito sem novo ADR que o substitua.

## Indicadores de qualidade

- build reproduzível e testes verdes;
- zero segredo real detectado no repositório;
- dependências sem vulnerabilidades conhecidas críticas não justificadas;
- cobertura dos invariantes de segurança, não apenas cobertura de linhas;
- migrações aplicáveis a banco limpo e banco evoluído;
- ausência de acesso cross-tenant nos testes;
- latência e taxa de erro observáveis nos endpoints de protocolo;
- documentação separando claramente implementado, planejado e experimental.
