# Observabilidade operacional

Esta operação opcional acrescenta Prometheus e Grafana ao Compose do Identity
Hub. A coleta usa uma credencial exclusiva, lida de arquivo somente leitura; o
token não é uma conta de usuário, não é persistido no banco e não aparece em
tags, painéis ou respostas.

## Preparar a credencial

Crie a pasta ignorada pelo Git e gere ao menos 32 caracteres aleatórios. Em
PowerShell:

```powershell
New-Item -ItemType Directory -Force secrets | Out-Null
$bytes = [Security.Cryptography.RandomNumberGenerator]::GetBytes(48)
$token = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
Set-Content -NoNewline -Path secrets/identity-hub-metrics-token -Value $token
```

Copie `.env.example` para `.env`, substitua as senhas de exemplo e mantenha
`IDENTITY_HUB_METRICS_TOKEN_FILE` apontando para esse arquivo. A aplicação
recusa tokens com menos de 32 caracteres, arquivos inacessíveis, recursos que
não sejam `file:` e a configuração simultânea de token inline e arquivo.

## Iniciar e consultar

```powershell
docker compose -f compose.yaml -f compose.observability.yaml up -d --build
docker compose -f compose.yaml -f compose.observability.yaml ps
```

- Grafana: `http://localhost:3000`, ou a porta definida em
  `IDENTITY_HUB_GRAFANA_PORT`;
- Prometheus: `http://localhost:9090`, ou a porta definida em
  `IDENTITY_HUB_PROMETHEUS_PORT`;
- painel provisionado: pasta **Identity Hub**, painel
  **Identity Hub — visão operacional**.

O painel cobre disponibilidade, volume por endpoint, erros 5xx, falhas de
login/MFA/token, rejeições por rate limit, ciclo de refresh token, latência p95,
heap e pool de conexões. A retenção local do Prometheus é de 15 dias por padrão
e pode ser ajustada por `IDENTITY_HUB_PROMETHEUS_RETENTION`.

## Catálogo inicial de alertas

| Alerta | Condição inicial | Severidade |
| --- | --- | --- |
| `IdentityHubUnavailable` | coleta indisponível por 2 minutos | crítica |
| `IdentityHubHighServerErrorRate` | mais de 5% de 5xx por 10 minutos, com mais de 0,1 req/s | aviso |
| `IdentityHubAuthenticationFailureSpike` | mais de 20 respostas 400/401/403/429 em login, MFA ou token por 5 minutos | aviso |
| `IdentityHubRateLimitRejectionSpike` | mais de 20 rejeições por rate limit em 5 minutos | aviso |
| `IdentityHubRefreshTokenReplayDetected` | ao menos um replay em 5 minutos | crítica |
| `IdentityHubAuditRetentionFailure` | ao menos uma falha de retenção em 15 minutos | aviso |

Os valores são um baseline explícito, não um SLO universal. Após obter tráfego
representativo, calibre-os por ambiente para reduzir silêncio e ruído. As
regras ficam em `ops/observability/prometheus/alerts.yml` e os alertas ativos
aparecem no Prometheus e no painel. O envio para e-mail, chat ou pager exige um
Alertmanager/receiver da plataforma e não está configurado neste repositório.

## Segurança e diagnóstico

O endpoint `/actuator/prometheus` continua retornando `401` sem autenticação. Um
Bearer token correto cria apenas a autoridade técnica usada durante a coleta;
um Bearer inválido falha imediatamente e não tenta autenticação de usuário. O
acesso pela sessão autenticada existente foi preservado para diagnóstico
manual.

Se o alvo aparecer como `DOWN`, confirme primeiro se backend e Prometheus usam
o mesmo arquivo secreto montado e, depois, verifique a saúde do backend. Evite
imprimir o conteúdo do arquivo em logs ou comandos de diagnóstico. Troque a
credencial substituindo o arquivo e recriando backend e Prometheus na mesma
janela; não existe período de sobreposição para o token de coleta.

Ao confirmar um alerta, continue pelo
[runbook de resposta a incidente](../runbooks/incident-response.md).
