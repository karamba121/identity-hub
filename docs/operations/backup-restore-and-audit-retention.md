# Backup, restauração e retenção de auditoria

Este documento define a política operacional mínima do Identity Hub. Backup não
é sinônimo de alta disponibilidade nem de arquivo consultável: ele existe para
recuperação de desastre, enquanto a trilha de auditoria permanece consultável no
banco somente durante sua retenção online.

## Objetivos do MVP

- **RPO:** até 24 horas de dados, com um backup lógico completo diário;
- **RTO:** até 4 horas para provisionar um banco limpo, restaurar, validar e
  reabrir o serviço;
- **retenção dos backups:** 35 cópias diárias, cifradas e fora do host primário;
- **ensaio:** restauração mensal e antes de mudanças relevantes de PostgreSQL;
- **auditoria online:** 365 dias, sujeita à validação jurídica e regulatória do
  ambiente antes da produção.

Uma exigência legal diferente substitui esses números por configuração e
procedimento aprovados. Um backup vencido deve ser eliminado pelo cofre de
backup; restaurá-lo não o transforma em extensão silenciosa da retenção.

## Conjunto mínimo de recuperação

Preserve como conjuntos separados e correlacionados por data:

1. dump lógico completo do PostgreSQL em formato custom do `pg_dump`;
2. configuração de implantação sem valores sensíveis;
3. secrets cifrados no cofre operacional, especialmente
   `IDENTITY_HUB_MFA_ENCRYPTION_KEY` e os pares PEM atual/próximo;
4. versão da imagem implantada e checksums do dump e dos arquivos criptográficos.

Sem a chave MFA correspondente ao banco restaurado, os segredos TOTP persistidos
não podem ser decifrados. Chaves privadas, senhas e dumps nunca devem entrar no
Git, em tickets ou em logs de automação.

## Criar e verificar o backup

Use clientes PostgreSQL 17 e credenciais fornecidas por `PGPASSFILE` ou pelo
cofre do ambiente, sem senha na linha de comando. Exemplo em PowerShell 7:

```powershell
$timestamp = Get-Date -AsUTC -Format 'yyyyMMddTHHmmssZ'
$backupPath = "identity-hub-$timestamp.dump"
pg_dump --host <host> --username <usuario-backup> --dbname <banco> `
  --format custom --no-owner --no-privileges --file $backupPath
if ($LASTEXITCODE -ne 0) { throw 'pg_dump falhou' }
pg_restore --list $backupPath | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'backup inválido para pg_restore' }
Get-FileHash -Algorithm SHA256 $backupPath
```

O sucesso do comando e o checksum são necessários, mas não provam recuperação.
Envie o dump ao armazenamento cifrado e imutável e confirme a política de 35
cópias. O arquivo local temporário deve ser removido pelo processo operacional
após confirmar o envio.

## Ensaio e restauração

Nunca restaure primeiro sobre o banco ativo. Crie um PostgreSQL isolado e vazio,
com a mesma versão principal, e execute:

```powershell
createdb --host <host-isolado> --username <usuario-restore> <banco-vazio>
pg_restore --host <host-isolado> --username <usuario-restore> `
  --dbname <banco-vazio> --clean --if-exists --no-owner --no-privileges <backup.dump>
```

No banco isolado, registre como evidência sem exportar dados pessoais:

- checksum e data do backup;
- versão mais alta bem-sucedida em `flyway_schema_history` — atualmente V17;
- contagens agregadas das tabelas críticas;
- inicialização do backend com `readiness` saudável;
- autenticação controlada com uma conta de teste criada para o ensaio.

Se o banco restaurado substituir produção, mantenha o serviço em manutenção e
invalide todo estado OAuth restaurado antes de reabrir. Isso evita ressuscitar
authorization codes ou refresh tokens presentes em um snapshot antigo:

```sql
DELETE FROM oauth_refresh_token_history;
DELETE FROM oauth_refresh_token_family;
DELETE FROM oauth2_authorization;
DELETE FROM authorization_interaction;
```

Depois, reinicie todas as réplicas, confirme issuer/JWK Set, readiness e emissão
de um fluxo novo. Como access tokens JWT são autônomos, aguarde ao menos os 5
minutos de vida máxima atuais mais a margem de relógio antes de reabrir os
resource servers, ou execute a rotação de comprometimento das chaves. Uma
restauração só está concluída após registrar responsável, horários, backup usado,
checksum, validações e decisão de reabertura.

## Retenção online da auditoria

O backend oferece expurgo controlado da tabela `security_audit_event`:

```properties
IDENTITY_HUB_AUDIT_RETENTION_ENABLED=true
IDENTITY_HUB_AUDIT_RETENTION_PERIOD=365d
IDENTITY_HUB_AUDIT_RETENTION_BATCH_SIZE=1000
IDENTITY_HUB_AUDIT_RETENTION_INITIAL_DELAY=1m
IDENTITY_HUB_AUDIT_RETENTION_INTERVAL=1h
```

O recurso nasce desabilitado. Ative-o somente depois de um backup validado e da
aprovação da política aplicável. O período aceito vai de 30 a 3650 dias e o lote
de 1 a 10.000 eventos. Cada execução remove no máximo um lote estritamente mais
antigo que o corte, em transação, usando o índice cronológico dedicado.

As métricas `identity_hub.audit.retention.events.deleted` e
`identity_hub.audit.retention.failures` não possuem identificadores nem outras
dimensões de alta cardinalidade. Durante preservação legal, desabilite o expurgo
antes do próximo ciclo e registre formalmente o início e o fim da suspensão.

## Limites desta política

- não houve backup nem restauração de uma instância PostgreSQL real nesta fatia;
- o repositório não provisiona cofre, criptografia, imutabilidade ou replicação;
- retenção de logs, métricas e traces é separada da auditoria persistida;
- exportação consultável de auditoria e automação de preservação legal não foram
  implementadas.
