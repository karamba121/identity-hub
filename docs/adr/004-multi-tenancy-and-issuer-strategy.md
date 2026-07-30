# ADR-004: Multi-tenancy e estratégia de issuer

- **Status:** aceito
- **Data:** 2026-07-30

## Contexto

Multi-tenancy afeta identidade, autorização, clientes, consentimentos, claims,
unicidades, auditoria e operação. Adicionar apenas um filtro `tenant_id` ao fim
do projeto não protege contra acesso horizontal.

Ao mesmo tempo, um issuer distinto por tenant cria múltiplos domínios de
segurança percebidos por clientes e resource servers. Isso aumenta o custo de
discovery, cadastro, cookies, chaves, observabilidade e validação.

## Decisão

O modelo inicial terá:

- identidade de usuário global;
- membership explícita entre usuário e tenant;
- papéis e permissões vinculados à membership;
- recursos tenant-scoped com `tenant_id` obrigatório;
- unicidades e índices compostos pelo tenant;
- contexto de tenant resolvido por fluxo autenticado e validado.

O MVP usará um **issuer canônico único**. O tenant ativo poderá aparecer em
claim documentado para audiences que conheçam essa semântica, sem substituir
issuer ou audience.

Cabeçalho enviado livremente pelo cliente não será aceito como prova de
identidade ou membership. Issuer por tenant só será considerado em novo ADR
após requisito concreto de isolamento, domínio ou federação.

## Consequências

- uma pessoa pode acessar mais de um tenant sem duplicar credenciais;
- troca de tenant exige reavaliação de membership e emissão apropriada;
- consultas, comandos, caches e auditoria precisam carregar tenant;
- resource servers continuam configurados contra um issuer estável;
- isolamento físico de banco não faz parte do MVP;
- testes cross-tenant são obrigatórios.

## Alternativas consideradas

### Usuário duplicado por tenant

Rejeitado inicialmente por duplicar credenciais e dificultar recuperação,
auditoria e experiência multitenant.

### Issuer por tenant desde o início

Adiado porque introduz custo relevante sem requisito comprovado.

### Tenant confiado por cabeçalho

Rejeitado como mecanismo de segurança. Cabeçalhos podem transportar contexto
somente depois de autenticação e validação em uma fronteira confiável.

## Evidências exigidas

- unicidades e índices tenant-aware;
- testes de leitura e escrita cruzada entre tenants;
- cache keys com tenant quando o dado for tenant-scoped;
- token rejeitado quando issuer ou audience não corresponder;
- trilha de auditoria contendo o tenant efetivamente autorizado.
