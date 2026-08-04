# ADR 019 — Step-up adaptativo para autenticação por senha

- **Status:** aceito
- **Data:** 2026-08-04

## Contexto

Bloqueio progressivo e rate limiting reduzem brute force, mas uma senha correta
logo após várias falhas ainda pode representar comprometimento. Alguns escopos
também podem justificar fator forte sem tornar MFA obrigatório para todo login.
Uma política baseada em geolocalização, fingerprint ou reputação externa exigiria
novos dados pessoais, fornecedores e operação que o projeto ainda não possui.

## Decisão

Aplicar uma política determinística e configurável no login local por senha:

- três falhas dentro de quinze minutos constituem o sinal padrão de step-up;
- escopos sensíveis podem ser listados explicitamente por ambiente e começam
  vazios para não bloquear usuários antes do provisionamento de fatores;
- o sinal é capturado antes da autenticação da senha e sua exigência é mantida
  na identidade por cinco minutos, mesmo que o acerto da senha zere o contador;
- com TOTP habilitado, o fluxo usa o desafio MFA já protegido contra replay;
- sem TOTP, a senha é recusada com resposta genérica de política; uma passkey
  registrada pode concluir a mesma interação como fator forte;
- TOTP ou passkey bem-sucedidos removem a exigência temporária;
- decisões produzem auditoria e métricas somente com resultados e motivos de
  cardinalidade fechada.

Os limites e durações são configuração operacional validada na inicialização.
Escopos sensíveis só devem ser ativados depois que a população afetada puder
usar TOTP ou passkey.

## Consequências

O Identity Hub responde a sinais já confiáveis sem armazenar IP, user-agent,
geolocalização ou fingerprint. A política é explicável e testável, mas não cobre
anomalias de rede, impossível travel ou reputação de dispositivo. Usuários sem
fator forte podem precisar aguardar a expiração do sinal de falhas; um escopo
marcado como sensível bloqueia permanentemente a senha isolada por desenho.

A federação OIDC e a passkey são autenticações fortes independentes e não passam
pela avaliação específica de senha local. Evoluções que reclassifiquem a força
de provedores externos precisarão de contrato de confiança próprio.

## Alternativas consideradas

- exigir MFA em todo login: rejeitado por eliminar a natureza opcional do fator;
- pontuação opaca com muitos sinais: rejeitada por falta de dados, explicabilidade
  e operação comprovada;
- usar apenas o contador de falhas em memória: rejeitado porque o sucesso da
  senha o zera e permitiria contornar o step-up repetindo a tentativa;
- armazenar IP ou fingerprint: adiado até existir necessidade, base legal,
  retenção e threat model específicos.

## Evidências exigidas

- decisão de baixo risco, falhas recentes, escopo sensível e exigência ativa;
- persistência do step-up depois que a senha correta zera o contador;
- recusa sem TOTP e conclusão por fator forte;
- auditoria e métricas sem sinais ou identificadores de alta cardinalidade;
- migração, suíte backend, build Angular e validação do Compose.
