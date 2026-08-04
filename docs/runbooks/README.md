# Runbooks operacionais

Estes runbooks transformam os contratos implementados no Identity Hub em
checklists de resposta. Eles não substituem autorização organizacional,
contatos de plantão, cofre de secrets, política jurídica ou procedimentos da
plataforma onde o serviço estiver implantado.

| Runbook | Acionamento | Condição de saída |
| --- | --- | --- |
| [Resposta a incidente](incident-response.md) | alerta, denúncia ou comportamento anômalo | serviço estável, ameaça contida, evidência preservada e acompanhamento definido |
| [Rotação de chaves](signing-key-rotation.md) | calendário, expiração ou suspeita de comprometimento | emissores e resource servers convergiram para o `kid` aprovado |
| [Recuperação](recovery.md) | perda, corrupção ou indisponibilidade não recuperável do estado durável | banco restaurado, estado OAuth invalidado e fluxo novo validado |

## Regras comuns

1. Abra um identificador de incidente ou mudança e registre horários em UTC.
2. Nomeie comandante, operador e aprovador. Rotação de chave privada e corte de
   banco exigem duas pessoas distintas quando a organização permitir.
3. Preserve evidências antes de reiniciar, restaurar ou substituir recursos.
4. Registre versões, `kid`, checksums e nomes de secrets; nunca copie o conteúdo
   de senhas, tokens, cookies, códigos OAuth, OTPs, dumps ou chaves privadas.
5. Aplique mudanças em todas as réplicas. Não deixe emissores com relógios,
   pares PEM ou configurações divergentes.
6. Defina previamente o ponto de retorno e a condição de abortar. Depois de
   aceitar escritas em um banco restaurado, voltar ao banco anterior pode criar
   split-brain e deixa de ser um rollback simples.
7. Só encerre depois de validar por fora do processo alterado e registrar riscos
   residuais e ações posteriores.

O registro operacional deve conter, no mínimo: identificador, severidade,
detecção, início e fim, responsáveis, serviços/tenants afetados, linha do tempo,
hipóteses e evidências, ações aprovadas, impacto de dados, verificações de
recuperação, decisão de reabertura e pendências com responsável e prazo.

## Estados

Use a sequência `detectado → triado → contido → recuperado → monitorado →
encerrado`. Se uma validação falhar, retorne a `contido`; não avance apenas
porque o endpoint voltou a responder.

O repositório provisiona regras Prometheus, mas não Alertmanager, escala ou
lista de contatos. A plataforma deve ligar cada severidade ao canal e à pessoa
responsável antes da produção.
