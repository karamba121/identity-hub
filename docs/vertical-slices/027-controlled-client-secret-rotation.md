# Fatia vertical 027 — rotação controlada de client secrets

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 003, 005, 006 e 007

## Capacidade entregue

Administradores com `oauth.clients.manage` podem rotacionar o secret de um
cliente confidencial pelo endpoint:

`POST /api/v1/admin/tenants/{tenantId}/oauth-clients/{clientId}/rotate-secret`

A operação gera 256 bits aleatórios e devolve o novo segredo somente nessa
resposta. O operador escolhe por quantos minutos o segredo anterior continuará
válido, entre `0` e `1440`; na ausência do campo, a janela padrão é de 15
minutos.

## Persistência e autenticação

A migration `V16__rotating_oauth_client_secrets.sql` amplia a coluna padrão do
Spring Authorization Server para comportar um envelope versionado. Esse
envelope contém apenas:

- hash Argon2id do segredo atual;
- hash Argon2id do segredo imediatamente anterior;
- instante UTC de expiração do segredo anterior.

O `RotatingClientSecretPasswordEncoder` reconhece tanto hashes simples legados
quanto o envelope de rotação. Durante a janela ele verifica as duas gerações;
no instante de expiração, somente a atual permanece válida. Uma rotação
subsequente promove o hash atual para anterior e descarta gerações mais antigas.

A linha de ownership do cliente é adquirida com lock pessimista antes da troca.
Assim, duas rotações concorrentes são serializadas e os dois valores devolvidos
continuam utilizáveis como geração atual e imediatamente anterior, em vez de
uma resposta ser perdida por sobrescrita.

## Administração e auditoria

A tela de clientes OAuth oferece `Rotacionar secret` apenas para clientes
confidenciais. Ela valida a janela, pede confirmação, mostra o novo segredo em
destaque uma única vez e informa até quando a geração anterior permanece
válida. A auditoria append-only registra `OAUTH_CLIENT_SECRET_ROTATED`, sem
segredo, hash ou janela no evento.

## Evidências executadas

- criação e rotação reais pela API administrativa;
- segredo atual e anterior aceitos no endpoint `/oauth2/token` durante a janela;
- segredo bruto ausente do registro persistido e das consultas posteriores;
- corte exato do segredo anterior no instante de expiração com relógio
  controlado;
- rotação consecutiva preserva somente a geração imediatamente anterior;
- cliente público e janela acima do limite são rejeitados;
- evento de auditoria identifica ação e cliente sem material secreto;
- migration aplicada em H2, suíte completa do backend e build Angular
  verificados ao concluir a fatia.

## Limites ainda abertos

- a migration não foi executada em PostgreSQL real nesta fatia;
- não houve exercício concorrente em PostgreSQL nem fluxo em navegador;
- notificações operacionais antes do encerramento da janela não foram
  implementadas;
- a matriz negativa consolidada de segredo inválido, escopo excessivo e
  confusão de audience permanece como próximo item da Fase 7.
