# Identity Hub

[![CI](https://github.com/karamba121/identity-hub/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/karamba121/identity-hub/actions/workflows/ci.yml)
![Status](https://img.shields.io/badge/status-em%20desenvolvimento-yellow)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4-6DB33F?logo=springboot)
![OAuth 2.1](https://img.shields.io/badge/OAuth-2.1-3C3C3D)
![OpenID Connect](https://img.shields.io/badge/OpenID-Connect-F78C40?logo=openid)
![License](https://img.shields.io/github/license/karamba121/identity-hub)

Provedor de identidade e acesso empresarial construído com Java e Spring para
demonstrar, de ponta a ponta, como autenticação, autorização e federação devem
ser tratadas além da simples emissão de um JWT.

O projeto será desenvolvido como um **monólito modular**, com limites internos
explícitos e uma única unidade de implantação inicial. A solução pretende
oferecer OAuth 2.0, OpenID Connect e um perfil de segurança alinhado às
recomendações modernas associadas ao OAuth 2.1, sem transformar cada bounded
context em um microsserviço artificial.

> **Estado atual:** trinta e quatro fatias verticais executáveis. O repositório contém
> Authorization Code com PKCE, login e consentimento no Angular/TailAdmin,
> persistência PostgreSQL, tokens OIDC, um cliente público demonstrativo e uma
> API protegida por issuer, audience e escopo. A terceira fatia acrescenta
> refresh tokens rotativos, detecção de replay, concorrência com sucessor único
> e revogação RFC 7009. A quarta fatia coordena essa revogação com logout OIDC,
> invalidação da sessão SSO e retorno pós-logout validado. A quinta fatia
> acrescenta métricas Prometheus protegidas e de cardinalidade limitada, além
> de comprovar falha fechada quando a persistência de refresh tokens fica
> indisponível. A sexta fatia inicia a tenancy com organizações e memberships
> persistidas, resolução pelo sujeito autenticado e apresentação dos vínculos
> no cliente Angular. A sétima fatia versiona um catálogo mínimo de permissões
> administrativas, sem confundi-lo com concessões a usuários. A oitava fatia
> vincula memberships a papéis do próprio tenant, associa permissões aos papéis
> e expõe as concessões efetivas no contexto autenticado. A nona fatia protege
> o bootstrap do primeiro administrador com exclusão mútua transacional e
> reconciliação idempotente. A décima fatia protege o último administrador
> válido contra rebaixamento, suspensão e remoção, inclusive sob concorrência. A
> décima primeira fatia expõe essas mutações por uma API administrativa que
> combina audience e scope próprios com a permissão efetiva do ator no tenant,
> rejeitando acesso horizontal entre organizações. A décima segunda fatia
> acrescenta CRUD administrativo de clientes OAuth públicos por tenant, com
> PKCE obrigatório, redirect URIs e escopos validados e autorização distinta
> para consulta e mutação. A décima terceira fatia entrega a primeira área
> administrativa no Angular/TailAdmin, com autenticação PKCE, seleção de tenant
> pelas permissões efetivas e manutenção visual desses clientes. A décima
> quarta fatia registra em trilha append-only as mutações administrativas
> concluídas, negadas ou falhas e oferece consulta paginada e tenant-aware. A
> décima quinta fatia consolida uma suíte negativa de isolamento horizontal
> para contexto de usuário, administração, clientes OAuth, memberships e
> auditoria, incluindo tentativas de aliasing por identificador estrangeiro. A
> décima sexta fatia entrega cadastro público e confirmação de e-mail por link
> de uso único, com token persistido somente como hash, envio SMTP configurável
> e bloqueio de login enquanto a conta estiver pendente. A décima sétima fatia
> centraliza a política de senha, adota Argon2id para novas credenciais e migra
> hashes BCrypt legados depois de uma autenticação válida. A décima oitava
> fatia entrega recuperação de senha sem enumeração de contas, com link curto,
> expirável e de uso único, persistido somente como hash. A décima nona fatia
> acrescenta bloqueio progressivo após falhas de login, sem revelar o estado da
> conta e sem permitir que tentativas durante o bloqueio prolonguem o prazo. A
> vigésima fatia limita login, cadastro, verificação e recuperação combinando
> identificador, origem e o par dos dois, sem persistir esses sinais em claro. O
> vigésimo primeiro incremento encerra grants OAuth, refresh tokens, access
> tokens anteriores e sessões SSO depois de uma recuperação de senha válida. O
> vigésimo segundo incremento adiciona MFA TOTP opcional, desafio após a senha,
> proteção contra replay e códigos de recuperação de uso único. O vigésimo
> terceiro protege as operações MFA com rate limiting e registra ativações,
> desativações, regenerações e desafios em auditoria consultável pela própria
> identidade, sem armazenar fatores ou dados pessoais. O vigésimo quarto
> consolida a suíte negativa contra enumeração, força bruta e replay e comprova
> que duas recuperações concorrentes produzem um único vencedor. O vigésimo
> quinto entrega clientes confidenciais por tenant, segredo revelado somente na
> criação e persistido como Argon2id, além do grant Client Credentials com
> sujeito de máquina, audience e escopo mínimo. O vigésimo sexto publica um
> resource server Spring Boot independente que valida assinatura pelo JWK Set,
> issuer, audience e escopo e pode ser executado no Compose. O vigésimo sétimo
> permite rotacionar client secrets com janela controlada para o segredo
> anterior, mantendo apenas hashes Argon2id e auditando a operação. O vigésimo
> oitavo consolida a matriz negativa de Client Credentials para segredo
> inválido, escopo excessivo e confusão entre audiences demonstrativa e
> administrativa. O vigésimo nono extrai a origem das chaves de assinatura e
> permite selecionar chave efêmera ou par RSA PEM externo por ambiente, com
> validação criptográfica e falha fechada. O trigésimo incremento planeja e
> exercita rotação de chaves com publicação antecipada e sobreposição segura. O
> trigésimo primeiro define backup, restauração e retenção executável da
> auditoria. O trigésimo segundo entrega coleta Prometheus autenticada, painel
> Grafana e alertas para autenticação, erros, indisponibilidade e abuso. O
> trigésimo terceiro adiciona carga k6 reproduzível sobre discovery, JWK Set e
> início do Authorization Code, com limites de latência, erro e saturação. O
> trigésimo quarto fecha a operação do MVP com runbooks acionáveis para
> incidente, rotação planejada ou emergencial e recuperação do PostgreSQL. O
> [roadmap](docs/roadmap.md) diferencia claramente o que está concluído do que
> está apenas planejado.

## Executar a primeira fatia com Docker Compose

O Compose da raiz constrói e inicia conjuntamente o frontend, o backend e o
PostgreSQL. O Nginx do frontend serve a SPA e encaminha os endpoints OAuth/OIDC
ao backend pela rede interna; somente a porta HTTP do frontend é publicada.

Em PowerShell:

```powershell
Copy-Item .env.example .env
# Edite .env e substitua as duas senhas marcadas com "change-this"
docker compose up -d --build
docker compose ps
```

Abra `http://localhost:4200/demo`, ou a URL definida em
`IDENTITY_HUB_PUBLIC_URL`. Em um host com HTTPS, essa URL deve conter o endereço
público exato, sem barra no final, e `IDENTITY_HUB_COOKIE_SECURE` deve ser
`true`. O Compose serve HTTP; em produção, a terminação TLS deve ficar em um
proxy reverso externo.

Para acompanhar a inicialização e encerrar os contêineres sem apagar o banco:

```powershell
docker compose logs -f
docker compose stop
```

`docker compose down` remove os contêineres e a rede, mas preserva o volume por
padrão. Não use `docker compose down -v` se quiser manter os dados.

O arquivo `.env` é ignorado pelo Git. Não versione senhas reais. O volume
`identity_hub_postgres_data` é exclusivo deste deploy conjunto e não reutiliza
automaticamente volumes criados pela versão anterior do Compose.

## Executar a primeira fatia para desenvolvimento

Pré-requisitos: Java 17, Node.js, Docker e portas `4200`, `5432` e `8080`
disponíveis. Em PowerShell, execute em três terminais:

```powershell
# terminal 1 - raiz do repositório
docker compose up -d postgres

# terminal 2
cd Backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# terminal 3
cd Frontend
npm ci
npm start
```

Abra `http://localhost:4200/demo`. O perfil `dev` provisiona somente para uso
local o usuário `admin@identityhub.local`, com senha `ChangeMe123!`, o cliente
público `identity-hub-demo` e a organização `identity-hub-demo` vinculada ao
usuário. Senha, slug e nome da organização podem ser substituídos pelas
variáveis correspondentes do arquivo `.env`. O bootstrap permanece
desabilitado por padrão fora do perfil `dev`.

Para encerrar a infraestrutura, use `docker compose stop` na raiz. Tanto no
Compose completo quanto na execução de desenvolvimento, esta fatia gera uma
chave RSA efêmera a cada inicialização e ainda não representa uma configuração
de produção. Consulte a
[evidência da fatia 001](docs/vertical-slices/001-authorization-code-pkce.md).

## Objetivo

O Identity Hub será uma referência pública de servidor de autorização e
provedor OpenID Connect capaz de atender aplicações web, SPAs, backends e
integrações máquina a máquina.

O projeto deve comprovar:

- domínio de Java, Spring Boot, Spring Security e Spring Authorization Server;
- entendimento dos limites de confiança entre navegador, cliente público,
  cliente confidencial, servidor de autorização e resource server;
- aplicação correta de Authorization Code com PKCE, Client Credentials,
  refresh tokens, revogação, discovery e JWK Set;
- modelagem de usuários, credenciais, tenants, memberships, papéis, permissões
  e clientes OAuth;
- decisões explícitas sobre consistência, concorrência, idempotência,
  auditoria, cache e proteção de dados sensíveis;
- capacidade de evoluir um sistema empresarial sem complexidade distribuída
  prematura.

## Escopo funcional planejado

### Protocolos e sessões

- OAuth 2.0 e OpenID Connect 1.0;
- Authorization Code com PKCE obrigatório para clientes públicos;
- Client Credentials para integrações serviço a serviço;
- refresh token com rotação e detecção de reutilização;
- revogação e introspecção quando aplicável ao tipo de token;
- discovery metadata, UserInfo, JWK Set e logout;
- consentimento explícito quando o cliente ou o escopo exigir;
- gestão de sessões e encerramento centralizado.

### Identidades e acesso

- cadastro, ativação, suspensão e recuperação de conta;
- verificação de e-mail;
- bloqueio progressivo por tentativas inválidas;
- MFA opcional, iniciado por TOTP;
- RBAC com permissões granulares;
- memberships e administração por tenant;
- provisionamento administrativo e trilha de alterações;
- aplicação Angular de administração e cliente SPA demonstrativo.

### Operação e segurança

- PostgreSQL como fonte de verdade;
- Redis para dados efêmeros, rate limiting e coordenação com semântica
  explicitamente documentada;
- chaves assimétricas fora do código-fonte e rotação sem interrupção;
- auditoria append-only de eventos relevantes;
- métricas, logs estruturados e traces com OpenTelemetry;
- migrações versionadas, testes automatizados e pipeline no GitHub Actions;
- execução local reproduzível com Docker Compose.

## Princípios arquiteturais

1. **Protocol first:** endpoints e respostas seguem padrões publicados; regras
   proprietárias ficam fora do núcleo dos protocolos.
2. **Seguro por padrão:** PKCE, redirect URIs exatas, senhas com hash forte,
   segredos não versionados e menor privilégio não são opcionais.
3. **Monólito modular primeiro:** módulos lógicos não implicam processos,
   bancos ou deploys independentes.
4. **Fonte de verdade explícita:** PostgreSQL guarda estado durável; Redis não
   substitui consistência transacional.
5. **Tenancy no domínio:** tenant não será apenas um filtro adicionado no fim do
   projeto.
6. **Auditoria sem vazamento:** registrar decisões e resultados de segurança,
   nunca senhas, códigos, tokens, secrets ou material criptográfico privado.
7. **Evidência antes de alegação:** um item só é marcado como entregue após
   testes e documentação compatíveis com seu risco.

## Arquitetura pretendida

```mermaid
flowchart LR
    subgraph Clients["Clientes"]
        SPA["SPA Angular<br/>cliente público"]
        WEB["Aplicação web/BFF<br/>cliente confidencial"]
        M2M["Serviço<br/>machine-to-machine"]
    end

    subgraph Hub["Identity Hub — uma unidade de implantação"]
        PROTOCOL["Protocolos OAuth/OIDC"]
        IDENTITY["Identidades e credenciais"]
        ACCESS["Tenants, papéis e permissões"]
        CLIENTS["Clientes e consentimentos"]
        AUDIT["Auditoria de segurança"]
    end

    DB[("PostgreSQL")]
    CACHE[("Redis")]
    KEYS["Provedor de chaves"]
    RS["Resource servers"]

    SPA --> PROTOCOL
    WEB --> PROTOCOL
    M2M --> PROTOCOL
    PROTOCOL --> IDENTITY
    PROTOCOL --> ACCESS
    PROTOCOL --> CLIENTS
    PROTOCOL --> AUDIT
    IDENTITY --> DB
    ACCESS --> DB
    CLIENTS --> DB
    AUDIT --> DB
    PROTOCOL --> CACHE
    PROTOCOL --> KEYS
    PROTOCOL --> RS
```

A visão completa, os limites dos módulos e os fluxos de confiança estão em
[Visão de arquitetura](docs/architecture/overview.md).

## Módulos lógicos previstos

| Módulo | Responsabilidade |
| --- | --- |
| `Identity` | usuários, credenciais, recuperação, bloqueio e MFA |
| `Tenancy` | tenants, memberships, contexto e isolamento |
| `AccessControl` | papéis, permissões e políticas administrativas |
| `OAuthClient` | clientes, redirect URIs, escopos e consentimentos |
| `AuthorizationServer` | endpoints e composição dos protocolos OAuth/OIDC |
| `KeyManagement` | ciclo de vida de chaves públicas e privadas |
| `SecurityAudit` | eventos de autenticação, autorização e administração |
| `Administration` | casos de uso e API da operação administrativa |

Esses nomes representam fronteiras lógicas. A estrutura final de pacotes será
validada no primeiro incremento e não precisa reproduzir uma camada por pasta
quando isso não trouxer isolamento real.

## Estratégia de entrega

O desenvolvimento seguirá **fatias verticais**. Cada incremento deve incluir o
comportamento de domínio, persistência, endpoint, segurança, migração, testes e
documentação necessários para produzir uma capacidade executável.

Exemplo da primeira fatia:

1. aplicação Spring inicial e infraestrutura local;
2. usuário administrativo de desenvolvimento provisionado com segurança;
3. cliente público cadastrado;
4. login e consentimento;
5. Authorization Code com PKCE;
6. emissão e validação de ID token e access token;
7. discovery e JWK Set;
8. teste de integração do fluxo completo.

Essa fatia foi entregue em 2026-07-31. As limitações e evidências verificadas
estão registradas em
[`docs/vertical-slices/001-authorization-code-pkce.md`](docs/vertical-slices/001-authorization-code-pkce.md).
A continuação com resource server está documentada em
[`docs/vertical-slices/002-protected-resource-api.md`](docs/vertical-slices/002-protected-resource-api.md),
e o ciclo de sessão renovável está em
[`docs/vertical-slices/003-rotating-refresh-tokens.md`](docs/vertical-slices/003-rotating-refresh-tokens.md).
O encerramento coordenado está documentado em
[`docs/vertical-slices/004-oidc-logout.md`](docs/vertical-slices/004-oidc-logout.md),
e a observabilidade e resiliência do ciclo de sessão estão em
[`docs/vertical-slices/005-session-observability-and-resilience.md`](docs/vertical-slices/005-session-observability-and-resilience.md).
A fundação multitenant está registrada em
[`docs/vertical-slices/006-tenant-memberships.md`](docs/vertical-slices/006-tenant-memberships.md),
e o catálogo de permissões está documentado em
[`docs/vertical-slices/007-permission-catalog.md`](docs/vertical-slices/007-permission-catalog.md).
Os papéis administrativos por tenant estão registrados em
[`docs/vertical-slices/008-tenant-administrative-roles.md`](docs/vertical-slices/008-tenant-administrative-roles.md),
e a proteção concorrente do primeiro administrador em
[`docs/vertical-slices/009-first-administrator-bootstrap.md`](docs/vertical-slices/009-first-administrator-bootstrap.md).
A invariância do último administrador está documentada em
[`docs/vertical-slices/010-last-tenant-administrator.md`](docs/vertical-slices/010-last-tenant-administrator.md),
e a autorização das primeiras operações administrativas em
[`docs/vertical-slices/011-tenant-administration-authorization.md`](docs/vertical-slices/011-tenant-administration-authorization.md).
O CRUD de clientes OAuth públicos por tenant está registrado em
[`docs/vertical-slices/012-tenant-oauth-client-crud.md`](docs/vertical-slices/012-tenant-oauth-client-crud.md).
A primeira superfície administrativa Angular está documentada em
[`docs/vertical-slices/013-oauth-client-administration-ui.md`](docs/vertical-slices/013-oauth-client-administration-ui.md).
A trilha de auditoria administrativa está registrada em
[`docs/vertical-slices/014-administrative-security-audit.md`](docs/vertical-slices/014-administrative-security-audit.md).
A matriz negativa de isolamento horizontal está documentada em
[`docs/vertical-slices/015-tenant-horizontal-isolation.md`](docs/vertical-slices/015-tenant-horizontal-isolation.md).
O cadastro e a confirmação de e-mail estão documentados em
[`docs/vertical-slices/016-email-registration-and-verification.md`](docs/vertical-slices/016-email-registration-and-verification.md).
A política de senha e a evolução de hashes estão documentadas em
[`docs/vertical-slices/017-password-policy-and-hash-evolution.md`](docs/vertical-slices/017-password-policy-and-hash-evolution.md).
A recuperação segura de senha está documentada em
[`docs/vertical-slices/018-secure-password-recovery.md`](docs/vertical-slices/018-secure-password-recovery.md).
O bloqueio progressivo de login está documentado em
[`docs/vertical-slices/019-progressive-login-lockout.md`](docs/vertical-slices/019-progressive-login-lockout.md).
O rate limiting por sinais combinados está documentado em
[`docs/vertical-slices/020-combined-signal-rate-limiting.md`](docs/vertical-slices/020-combined-signal-rate-limiting.md).
A invalidação após eventos críticos de credencial está documentada em
[`docs/vertical-slices/021-critical-session-invalidation.md`](docs/vertical-slices/021-critical-session-invalidation.md).

CQRS será usado apenas onde modelos de leitura e escrita tiverem necessidades
materialmente diferentes. Eventos de domínio não serão usados como sinônimo de
mensageria, e RabbitMQ só será introduzido quando existir uma integração
assíncrona real.

## Estrutura do repositório

```text
identity-hub/
├── README.md
├── docs/
│   ├── architecture/
│   │   └── overview.md
│   ├── adr/
│   │   ├── README.md
│   │   ├── 001-modular-monolith.md
│   │   ├── 002-protocol-first-authorization-server.md
│   │   ├── 003-client-types-and-pkce.md
│   │   ├── 004-multi-tenancy-and-issuer-strategy.md
│   │   ├── 005-token-and-key-lifecycle.md
│   │   ├── 006-persistence-cache-and-consistency.md
│   │   └── 007-security-audit-and-sensitive-data.md
│   └── roadmap.md
├── Backend/      # scaffold Spring Boot existente
├── Frontend/     # scaffold Angular/TailAdmin existente
└── compose.yaml  # deploy conjunto de frontend, backend e PostgreSQL
```

## Decisões arquiteturais

- [ADR-001: monólito modular como unidade inicial de implantação](docs/adr/001-modular-monolith.md)
- [ADR-002: servidor de autorização orientado por padrões](docs/adr/002-protocol-first-authorization-server.md)
- [ADR-003: tipos de cliente e PKCE](docs/adr/003-client-types-and-pkce.md)
- [ADR-004: multi-tenancy e estratégia de issuer](docs/adr/004-multi-tenancy-and-issuer-strategy.md)
- [ADR-005: ciclo de vida de tokens e chaves](docs/adr/005-token-and-key-lifecycle.md)
- [ADR-006: persistência, cache e consistência](docs/adr/006-persistence-cache-and-consistency.md)
- [ADR-007: auditoria e dados sensíveis](docs/adr/007-security-audit-and-sensitive-data.md)
- [ADR-008: TailAdmin como interface de login e consentimento](docs/adr/008-tailadmin-authorization-interaction-ui.md)

## Referências normativas e técnicas

- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OAuth 2.0 Authorization Framework — RFC 6749](https://www.rfc-editor.org/rfc/rfc6749)
- [PKCE — RFC 7636](https://www.rfc-editor.org/rfc/rfc7636)
- [OAuth 2.0 Security Best Current Practice — RFC 9700](https://www.rfc-editor.org/rfc/rfc9700)
- [OAuth 2.0 Authorization Server Metadata — RFC 8414](https://www.rfc-editor.org/rfc/rfc8414)
- [OAuth 2.0 Token Revocation — RFC 7009](https://www.rfc-editor.org/rfc/rfc7009)
- [JWT Profile for OAuth 2.0 Access Tokens — RFC 9068](https://www.rfc-editor.org/rfc/rfc9068)
- [Spring Authorization Server](https://docs.spring.io/spring-authorization-server/reference/)

## Como acompanhar

O [roadmap](docs/roadmap.md) contém as fatias planejadas, critérios de aceite e
evidências esperadas. ADRs registram decisões duradouras; o roadmap registra
sequência e progresso. Mudanças arquiteturais relevantes devem atualizar ambos,
sem reescrever silenciosamente o histórico de uma decisão aceita.

O [resource server independente](examples/resource-server/README.md) demonstra
como uma API separada valida os tokens emitidos pelo Identity Hub e inclui um
fluxo completo de Client Credentials.
O [guia de chaves de assinatura](docs/operations/signing-keys.md) diferencia a
fonte efêmera local, o par PEM externo e a rotação planejada com publicação
antecipada e retenção segura da chave anterior.
O [procedimento de backup, restauração e retenção](docs/operations/backup-restore-and-audit-retention.md)
define o baseline de recuperação e o expurgo controlado da auditoria.
O [guia de observabilidade](docs/operations/observability.md) descreve a coleta
autenticada, o painel provisionado, os alertas e seus limites operacionais.
O [guia de testes de carga](docs/operations/load-testing.md) descreve o workload
k6, os thresholds e os cuidados para execução em ambiente isolado.
O [índice de runbooks](docs/runbooks/README.md) organiza resposta a incidente,
rotação de chaves e recuperação do estado durável.

## Licença

Distribuído sob a licença MIT. Consulte o arquivo [LICENSE](LICENSE).
