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
- [x] configurar Spring Security e Spring Authorization Server;
- [ ] definir módulos, regras de dependência e testes arquiteturais;
- [x] configurar PostgreSQL e execução local com Docker Compose;
- [ ] adicionar Redis junto ao primeiro fluxo efêmero que justifique seu uso;
- [x] criar migrações versionadas e dados de desenvolvimento restritos ao
  perfil `dev`;
- [x] disponibilizar health e readiness pelo Actuator;
- [ ] adicionar logs estruturados e OpenTelemetry básico;
- [ ] configurar build, testes e análise de dependências no GitHub Actions;
- [x] documentar comandos locais sem versionar credenciais reais.

**Critério de aceite:** clone limpo inicia a infraestrutura e a aplicação por
instruções reproduzíveis; pipeline executa build e testes; nenhum segredo real
está no repositório.

## Fase 2 — Login, consentimento e Authorization Code `MVP`

- [x] persistir usuários, clientes, autorizações e consentimentos;
- [x] cadastrar um cliente público de demonstração sem client secret;
- [x] definir no backend um contexto de interação opaco, curto e de uso
  controlado para preservar o pedido de autorização, conforme o
  [ADR-008](adr/008-tailadmin-authorization-interaction-ui.md);
- [x] adaptar a página `sign-in` do TailAdmin como tela oficial de login do
  servidor de autorização;
- [x] criar no TailAdmin a tela oficial de consentimento, exibindo cliente e
  escopos obtidos de uma API segura do backend;
- [ ] acrescentar tenant à apresentação do consentimento quando tenancy for
  implementada;
- [x] autenticar credenciais somente no backend e estabelecer sessão por cookie
  `HttpOnly`, `Secure` em produção e protegido contra CSRF;
- [x] registrar no backend a aprovação ou a recusa do consentimento e retomar o
  fluxo OAuth/OIDC original;
- [x] impedir que o frontend altere `client_id`, `redirect_uri`, escopos ou
  qualquer outro dado validado do pedido de autorização;
- [x] exigir PKCE `S256` para cliente público;
- [x] disponibilizar authorization, token, metadata, JWK Set e UserInfo;
- [x] emitir access token e ID token com issuer e audience validados;
- [x] rejeitar redirect URI não idêntica à cadastrada;
- [x] testar fluxo completo, interação pertencente a outra sessão, state, code
  de uso único e replay;
- [ ] completar a suíte negativa com interação expirada, PKCE inválido,
  consentimento negado e falha explícita de CSRF;
- [x] documentar o fluxo e exemplos sem valores sensíveis.

**Critério de aceite:** o navegador inicia o pedido no endpoint de autorização,
usa as telas TailAdmin para login e consentimento e retorna ao cliente apenas
depois de o backend validar e registrar toda a decisão. Um teste de integração
percorre o fluxo completo e demonstra também os principais cenários negativos.

## Fase 3 — Superfícies Angular e cliente demonstrativo `MVP`

- [x] criar a base Angular com o design system TailAdmin;
- [x] separar no frontend as superfícies de interação OAuth e cliente
  demonstrativo;
- [ ] delimitar a futura área administrativa e remover as páginas demonstrativas
  restantes do TailAdmin;
- [x] usar o layout público de autenticação nas telas de login e consentimento,
  sem sidebar ou navegação administrativa;
- [x] implementar no cliente demonstrativo o início do Authorization Code +
  PKCE;
- [x] manter verifier, state e nonce apenas pelo tempo necessário;
- [x] implementar callback com validação de state, nonce e audience;
- [x] consumir UserInfo;
- [x] criar e consumir uma API protegida de demonstração com audience própria;
- [x] tratar expiração, logout e sessão inválida no cliente demonstrativo;
- [x] preservar no retorno ao login o contexto opaco da interação, sem expor
  authorization code, token, verifier ou client secret;
- [ ] adaptar branding, estados de carregamento, erro, recusa e expiração ao
  padrão visual do TailAdmin;
- [x] testar o fluxo completo no navegador com PostgreSQL real;
- [ ] executar uma verificação dedicada de acessibilidade.

**Critério de aceite:** o navegador autentica sem client secret, acessa um
resource server com audience correta e encerra a sessão de forma previsível.
Os testes também demonstram que a UI de login/consentimento e o cliente OAuth
demonstrativo exercem responsabilidades diferentes.

## Fase 4 — Sessões, refresh tokens e revogação `MVP`

- [x] definir política inicial de access token de 5 minutos e refresh token de
  8 horas, exigindo nova autenticação após expiração ou comprometimento;
- [x] implementar refresh token apenas para clientes elegíveis;
- [x] rotacionar refresh token a cada uso;
- [x] detectar reutilização e revogar a família comprometida;
- [x] disponibilizar revogação padronizada;
- [x] implementar logout OIDC e encerramento da sessão atual;
- [x] testar concorrência, replay e revogação;
- [x] testar indisponibilidade parcial;
- [x] expor métricas sem cardinalidade ou dados sensíveis excessivos.

**Critério de aceite:** testes concorrentes demonstram um único sucessor por
rotação e nenhuma sessão revogada volta a conceder acesso.

## Fase 5 — Tenants, RBAC e administração `MVP`

- [x] persistir tenants e memberships com unicidade por tenant e usuário;
- [x] criar catálogo explícito de permissões;
- [x] implementar papéis administrativos por tenant;
- [x] proteger o bootstrap do primeiro administrador;
- [x] impedir remoção ou rebaixamento do último administrador válido;
- [x] aplicar tenant e permission checks nas APIs administrativas;
- [x] criar CRUD de clientes OAuth com redirect URIs e escopos;
- [x] criar interface Angular administrativa;
- [x] auditar alterações com ator, tenant, alvo e resultado;
- [x] testar isolamento horizontal entre tenants.

**Critério de aceite:** uma suíte negativa comprova que usuário, administrador e
cliente de um tenant não observam nem alteram recursos de outro.

## Fase 6 — Credenciais e proteção contra abuso `MVP`

- [x] cadastro e verificação de e-mail;
- [x] política de senha e hash resistente;
- [x] recuperação com token único, curto e armazenado de forma segura;
- [x] bloqueio progressivo por tentativas inválidas;
- [x] rate limiting por sinais combinados, sem depender só de IP;
- [x] invalidação de sessões após eventos críticos;
- [x] MFA TOTP opcional com códigos de recuperação;
- [x] proteção e auditoria do ciclo de vida do MFA;
- [x] testes de enumeração, brute force, replay e recuperação concorrente.

**Critério de aceite:** respostas públicas não permitem enumerar contas e os
testes demonstram expiração, uso único e revogação das credenciais temporárias.

## Fase 7 — Client Credentials e resource servers `MVP`

- [x] cadastrar cliente confidencial com secret exibido uma única vez;
- [x] persistir client secret somente com proteção não reversível;
- [x] habilitar Client Credentials por concessão explícita;
- [x] emitir token com sujeito de máquina, audience e escopos mínimos;
- [x] criar resource server demonstrativo, inicialmente exercitado pelo cliente
  público e reutilizável na futura fatia de Client Credentials;
- [x] publicar exemplo de validação de issuer, audience, assinatura e escopos;
- [x] definir rotação de client secrets com janela controlada;
- [x] testar segredo inválido, escopo excessivo e confusão de audience.

**Critério de aceite:** cliente público não consegue usar Client Credentials e
o resource server rejeita tokens destinados a outra audience.

## Fase 8 — Chaves, operação e resiliência `MVP`

- [x] abstrair origem de chaves por ambiente;
- [x] implementar geração e rotação planejada de chaves;
- [x] publicar chave atual e chaves anteriores durante janela segura;
- [x] testar tokens emitidos antes, durante e após rotação;
- [x] definir backup, restauração e retenção de auditoria;
- [x] criar painéis e alertas para autenticação, erro e abuso;
- [x] executar testes de carga focados nos endpoints críticos;
- [x] documentar runbooks de incidente, rotação e recuperação.

**Critério de aceite:** uma rotação exercitada não invalida prematuramente
tokens dentro da política, e o procedimento de recuperação possui evidência.

## Fase 9 — Evoluções pós-MVP

- [x] PAR e outras extensões justificadas por threat model;
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
