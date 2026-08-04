# Runbook — recuperação do estado durável

Use este procedimento para perda, corrupção ou indisponibilidade não
recuperável do PostgreSQL. A política de RPO de 24 horas, RTO de 4 horas,
retenção de 35 cópias e comandos detalhados de backup estão na
[referência de backup e restauração](../operations/backup-restore-and-audit-retention.md).

## Conter e selecionar o ponto de recuperação

1. Declare incidente crítico, coloque o ingresso em manutenção e impeça novas
   escritas. Registre o último instante confiável e não altere o banco fonte.
2. Selecione um dump anterior à corrupção e valide checksum, catálogo com
   `pg_restore --list`, versão PostgreSQL e disponibilidade do conjunto de
   configuração/secrets correspondente.
3. Confirme especialmente a chave `IDENTITY_HUB_MFA_ENCRYPTION_KEY` do snapshot.
   Sem ela, TOTP persistido não pode ser recuperado e os fatores afetados terão
   de ser removidos e recadastrados por procedimento aprovado.
4. Preserve também pares PEM, versão da imagem e configuração sem secrets.

## Restaurar em isolamento

Nunca restaure primeiro sobre o banco ativo. Use PostgreSQL da mesma versão
principal e credenciais por `PGPASSFILE` ou cofre:

```powershell
createdb --host <host-isolado> --username <usuario-restore> <banco-vazio>
if ($LASTEXITCODE -ne 0) { throw 'createdb falhou' }
pg_restore --host <host-isolado> --username <usuario-restore> `
  --dbname <banco-vazio> --clean --if-exists --no-owner --no-privileges <backup.dump>
if ($LASTEXITCODE -ne 0) { throw 'pg_restore falhou' }
```

Mantenha o banco isolado sem acesso público. Inicie uma instância candidata do
backend com `IDENTITY_HUB_BOOTSTRAP_ENABLED=false`, SMTP desabilitado ou ligado
a um sink e os secrets corretos montados externamente.

## Validar o candidato

Registre somente valores agregados:

```sql
SELECT max(version) AS flyway_version
FROM flyway_schema_history
WHERE success = true;

SELECT 'identity_user' AS relation, count(*) FROM identity_user
UNION ALL SELECT 'oauth2_registered_client', count(*) FROM oauth2_registered_client
UNION ALL SELECT 'tenant', count(*) FROM tenant
UNION ALL SELECT 'tenant_membership', count(*) FROM tenant_membership
UNION ALL SELECT 'security_audit_event', count(*) FROM security_audit_event;
```

Compare com a evidência do backup e com tendências conhecidas, sem exportar
linhas pessoais. Confirme migrations, readiness, discovery, JWK Set e um login
controlado. Falha de descriptografia MFA indica secret incompatível; não
substitua a chave e não marque o restore como válido.

## Preparar o corte

Um snapshot contém estado OAuth antigo que não pode voltar a conceder acesso.
Ainda em manutenção, execute no banco candidato e registre contagens antes e
depois:

```sql
BEGIN;
DELETE FROM oauth_refresh_token_history;
DELETE FROM oauth_refresh_token_family;
DELETE FROM oauth2_authorization;
DELETE FROM authorization_interaction;
COMMIT;
```

Mantenha resource servers fechados por pelo menos os 5 minutos de vida máxima
atual do access token mais a margem de relógio, ou faça rotação emergencial de
chave se houver suspeita de comprometimento. Em seguida:

1. aponte todas as réplicas para o banco candidato e reinicie-as;
2. confirme issuer e JWK Set idênticos entre réplicas;
3. execute Authorization Code + PKCE completo e valide token novo no resource
   server correto;
4. confirme que tokens, codes, interações e refresh tokens do snapshot não são
   aceitos;
5. reabra tráfego gradualmente e acompanhe erros, autenticação, abuso e pool do
   banco;
6. registre RPO real, RTO real, checksum, snapshot, responsáveis e decisão de
   reabertura.

## Retorno e cenários especiais

Antes de aceitar escritas no candidato, o ingresso pode voltar ao banco fonte
se ele continuar íntegro. Depois da primeira escrita, não alterne de volta sem
plano de reconciliação: isso cria duas fontes de verdade.

| Perda | Consequência | Recuperação |
| --- | --- | --- |
| dump inválido | ponto não restaurável | escolha cópia anterior e registre o aumento do RPO |
| chave MFA ausente | TOTP não pode ser decifrado | mantenha contas protegidas e execute recadastro aprovado dos fatores |
| chave de assinatura ausente | JWTs existentes deixam de ser verificáveis | implante par novo, force atualização JWK e nova autenticação |
| configuração/secrets incompatíveis | backend falha fechado ou dados ficam ilegíveis | não reabra; recupere o conjunto correlacionado ao snapshot |
| corrupção reaparece | candidato não é confiável | volte a `contido`, escolha ponto anterior e preserve a amostra para investigação |

Não elimine banco fonte, dump ou evidência durante o incidente. A destinação
ocorre depois do encerramento, conforme retenção e autorização formais.
