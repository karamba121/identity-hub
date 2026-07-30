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
- [ ] escolher e adicionar a licença do repositório.

## Fase 1 — Fundação executável `MVP`

- [ ] criar backend Spring Boot com Java LTS;
- [ ] integrar Spring Security e Spring Authorization Server;
- [ ] definir módulos, regras de dependência e testes arquiteturais;
- [ ] configurar PostgreSQL, Redis e execução local com Docker Compose;
- [ ] criar migrações versionadas e dados de desenvolvimento seguros;
- [ ] adicionar health, readiness, logs estruturados e OpenTelemetry básico;
- [ ] configurar build, testes e análise de dependências no GitHub Actions;
- [ ] documentar comandos locais sem versionar credenciais.

**Critério de aceite:** clone limpo inicia a infraestrutura e a aplicação por
instruções reproduzíveis; pipeline executa build e testes; nenhum segredo real
está no repositório.

## Fase 2 — Authorization Code, OIDC e PKCE `MVP`

- [ ] persistir usuários, clientes, autorizações e consentimentos;
- [ ] cadastrar um cliente público de demonstração sem client secret;
- [ ] implementar login e consentimento;
- [ ] exigir PKCE `S256` para cliente público;
- [ ] disponibilizar authorization, token, metadata, JWK Set e UserInfo;
- [ ] emitir access token e ID token com issuer e audience validados;
- [ ] rejeitar redirect URI não idêntica à cadastrada;
- [ ] testar code de uso único, state, nonce, PKCE inválido e replay;
- [ ] documentar o fluxo e exemplos sem valores sensíveis.

**Critério de aceite:** um teste de integração percorre o fluxo completo e
demonstra também os principais cenários negativos.

## Fase 3 — Cliente Angular demonstrativo `MVP`

- [ ] criar aplicação Angular seguindo um design system consistente;
- [ ] implementar login Authorization Code + PKCE;
- [ ] manter verifier, state e nonce apenas pelo tempo necessário;
- [ ] implementar callback com validação de state e nonce;
- [ ] consumir UserInfo e uma API protegida de demonstração;
- [ ] tratar expiração, logout e sessão inválida;
- [ ] testar fluxo no navegador e acessibilidade básica.

**Critério de aceite:** o navegador autentica sem client secret, acessa um
resource server com audience correta e encerra a sessão de forma previsível.

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
