# Runbook — resposta a incidente

## Quando usar

Acione este runbook para os alertas `IdentityHubUnavailable`,
`IdentityHubHighServerErrorRate`, `IdentityHubAuthenticationFailureSpike`,
`IdentityHubRateLimitRejectionSpike`, `IdentityHubRefreshTokenReplayDetected`
ou `IdentityHubAuditRetentionFailure`, além de denúncia de credencial ou chave
comprometida.

Classifique como crítica a indisponibilidade ampla, comprometimento confirmado
de chave de assinatura ou acesso administrativo, emissão indevida de tokens e
perda/corrupção do banco. Degradação parcial, pico de erro ou abuso contido são
inicialmente altos; eventos isolados sem impacto confirmado são moderados. O
comandante pode elevar a severidade a qualquer momento, nunca reduzi-la antes
de conhecer o alcance.

## Primeiros passos

1. Abra o registro, marque o início em UTC, nomeie comandante e operador e
   congele mudanças não relacionadas.
2. Confirme o sinal em uma segunda fonte. Um alerta sozinho inicia a triagem,
   mas não comprova causa ou comprometimento.
3. Preserve o intervalo relevante de métricas, eventos de auditoria e logs com
   acesso restrito. Não cole payloads integrais de autenticação em tickets.
4. Verifique estado e contratos públicos a partir de uma estação autorizada:

```powershell
$baseUrl = 'https://<identity-hub>'
docker compose ps
docker compose logs --since 15m backend
Invoke-RestMethod "$baseUrl/actuator/health/readiness"
Invoke-RestMethod "$baseUrl/.well-known/openid-configuration"
(Invoke-RestMethod "$baseUrl/oauth2/jwks").keys | Select-Object kid, kty, use, alg
```

Use os comandos Docker somente no deployment correto. Saída de logs permanece
dado operacional: revise e restrinja antes de compartilhá-la.

## Triagem por sinal

| Sinal | Confirmar | Contenção inicial |
| --- | --- | --- |
| indisponibilidade | readiness, processo, conectividade e saúde do PostgreSQL | retirar réplica defeituosa; se o estado durável não for recuperável, seguir o [runbook de recuperação](recovery.md) |
| taxa de 5xx | endpoints/status afetados, pool JDBC, banco e mudança recente | pausar rollout ou tráfego causador; não mascarar falha relaxando autenticação |
| falhas de autenticação | distribuição temporal, versão do cliente e auditoria autorizada | separar regressão de cliente de ataque; preservar respostas uniformes contra enumeração |
| rate limit | operação e sinal agregado, origem de tráfego e capacidade | limitar no ingresso quando autorizado; não aumentar limites durante ataque sem aprovação |
| replay de refresh token | evento, família revogada automaticamente e identidade afetada | manter a revogação, avisar o responsável e exigir recuperação de senha se houver tomada de conta |
| falha de retenção | erro do banco, espaço e métrica de falha | desabilitar temporariamente o expurgo para preservar dados e corrigir a causa antes de reativá-lo |
| chave suspeita | `kid`, primeiro uso indevido e sistemas que confiam nele | seguir imediatamente a seção emergencial do [runbook de rotação](signing-key-rotation.md) |

A trilha administrativa tenant-aware está em
`GET /api/v1/admin/tenants/{tenantId}/audit-events` e exige audience, escopo e
permissão `security.audit.read`. O histórico MFA da própria identidade está em
`GET /api/v1/mfa/audit-events`. Use essas APIs com uma sessão controlada; não
consulte diretamente o banco como atalho de autorização durante uma triagem
normal.

## Contenção e erradicação

- Preserve auditoria e backups. Não execute expurgo, restauração ou rotação em
  massa sem identificar o alvo exato.
- Mantenha PKCE, CSRF, bloqueio progressivo e rate limiting ativos. Contornar um
  controle para recuperar disponibilidade pode ampliar o incidente.
- Revogue pelo endpoint RFC 7009 os tokens conhecidos quando ainda estiverem
  disponíveis. Replay confirmado já revoga a família correspondente.
- Recuperação de senha válida invalida grants, famílias de refresh token,
  access tokens persistidos e sessões SSO da identidade. Ela é o caminho
  implementado para uma conta comprometida; não presuma existir revogação
  administrativa global.
- Se a origem for release ou configuração, reverta somente para uma versão e
  conjunto de secrets conhecidos e compatíveis. Registre a aprovação e os
  hashes das imagens/configurações, nunca os secrets.

## Validar a recuperação

Confirme, nesta ordem:

1. readiness saudável em todas as réplicas;
2. discovery e JWK Set coerentes com o issuer e `kid` esperados;
3. Authorization Code + PKCE completo com conta e cliente controlados;
4. emissão e validação de um token novo no resource server correto;
5. rejeição de audience incorreta e de sessão/token revogado aplicável;
6. taxa de 5xx, falhas de autenticação e rejeições por abuso retornando ao
   baseline;
7. evento e linha do tempo preservados sem dados sensíveis.

O comandante define e registra um período de observação proporcional ao risco.
Reabra tráfego gradualmente quando a plataforma permitir. Se qualquer contrato
falhar, volte ao estado `contido`.

## Encerramento

Registre impacto, causa confirmada ou limite da investigação, dados perdidos
dentro do RPO, duração, controles que funcionaram, ações corretivas e dono de
cada pendência. Rotacione credenciais usadas na resposta se puderem ter sido
expostas. Um post-mortem não deve reproduzir tokens, e-mails completos, IPs ou
material criptográfico desnecessário.
