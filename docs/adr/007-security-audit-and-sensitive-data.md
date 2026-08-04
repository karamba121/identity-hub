# ADR-007: Auditoria e dados sensíveis

- **Status:** aceito
- **Data:** 2026-07-30

## Contexto

Um provedor de identidade precisa explicar quem fez o quê, em qual tenant, para
qual cliente e com qual resultado. Ao mesmo tempo, observabilidade mal
configurada pode transformar logs e traces em uma segunda base de credenciais.

Logs operacionais mutáveis não substituem uma trilha de auditoria com acesso,
retenção e semântica próprios.

## Decisão

Eventos relevantes de segurança serão registrados em trilha append-only,
incluindo:

- sucesso e falha de autenticação;
- bloqueio, desbloqueio e recuperação de conta;
- cadastro, remoção e rotação de credenciais e MFA;
- consentimento concedido ou revogado;
- emissão, refresh, revogação e replay detectado;
- criação e alteração de cliente OAuth;
- mudanças de tenant, membership, papel e permissão;
- rotação e alteração de estado de chaves;
- ações administrativas negadas ou concluídas.

Cada evento terá, quando aplicável:

- instante em UTC;
- tipo, resultado e motivo normalizado;
- ator e sujeito por identificadores internos;
- tenant e cliente;
- correlação e contexto de rede minimizado;
- metadados estruturados aprovados.

É proibido registrar:

- senha ou hash de senha;
- client secret;
- access, ID ou refresh token;
- authorization code;
- PKCE verifier;
- cookie de sessão;
- OTP, código de recuperação ou token de reset;
- chave privada;
- payload integral de requisições de autenticação.

Dados pessoais serão minimizados, protegidos por controle de acesso e submetidos
a política explícita de retenção.

## Consequências

- investigação de incidente terá uma fonte estruturada;
- auditoria aumenta volume de armazenamento e responsabilidade regulatória;
- eventos precisam de versionamento compatível;
- mascaramento de log será testado, não presumido;
- acesso à auditoria será uma permissão separada;
- a política inicial de retenção e recuperação foi definida na
  [fatia 031](../vertical-slices/031-backup-restore-and-audit-retention.md);
  exportação consultável e preservação legal automatizada continuam posteriores.

## Alternativas consideradas

### Usar apenas logs da aplicação

Rejeitado porque logs podem mudar de formato, nível e retenção e não possuem
necessariamente integridade ou autorização próprias.

### Guardar requisições completas para investigação

Rejeitado porque aumenta drasticamente o impacto de vazamento e captura
credenciais desnecessárias.

### Não registrar falhas

Rejeitado porque abuso, enumeração e tentativa de replay dependem desses sinais.

## Evidências exigidas

- testes assegurando ausência de segredos em logs, traces e auditoria;
- acesso à consulta de auditoria protegido e tenant-aware;
- evento produzido para cada caso de uso de segurança definido;
- correlação entre operação e auditoria sem armazenar token;
- política de retenção e procedimento de acesso documentados antes da produção.
