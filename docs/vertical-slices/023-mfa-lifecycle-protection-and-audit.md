# Fatia vertical 023 — proteção e auditoria do ciclo MFA

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 007 e 014

## Capacidade entregue

O ciclo MFA passa a produzir eventos append-only para início da configuração,
ativação, regeneração de códigos, desativação e desafios aceitos ou recusados.
Falhas permanecem registradas mesmo quando a operação protegida sofre rollback,
com motivos normalizados como `INVALID_FACTOR`, `STATE_CONFLICT`,
`CHALLENGE_EXPIRED_OR_INVALID` e `RATE_LIMITED`.

As operações de configuração compartilham o limitador por identidade, origem e
combinação dos dois. O desafio de login preserva o limite já aplicado ao login
e também registra rejeições por excesso de tentativas.

## Isolamento e minimização

A migração V15 permite eventos sem tenant para casos de segurança da identidade
e adiciona índice cronológico por tipo e alvo. Esses eventos usam somente o ID
interno como ator e alvo. Não armazenam e-mail, segredo TOTP, OTP, código de
recuperação, cookie, token ou payload da requisição.

`GET /api/v1/mfa/audit-events?page=0&size=20` exige a sessão autenticada e
resolve o alvo pelo principal corrente; não aceita um identificador escolhido
pelo cliente. A tela de segurança apresenta os dez eventos mais recentes.

## Consistência

Sucesso, mutação e auditoria compartilham a mesma transação: se o evento não
puder ser persistido, a mudança MFA também é revertida. Falhas usam a gravação
independente já adotada pela auditoria administrativa, preservando a tentativa
enquanto a mutação é desfeita.

## Evidências executadas

- ativação, regeneração e desativação produzem eventos distintos;
- TOTP repetido e código antigo produzem `FAILED` com `INVALID_FACTOR`;
- desafio inválido e desafio aceito são diferenciados;
- a nona tentativa de gerenciamento na janela retorna `429` e registra
  `RATE_LIMITED`;
- a consulta não expõe e-mail, segredo TOTP nem códigos de recuperação;
- outra identidade não observa os eventos do alvo;
- Flyway aplica V1 a V15 e o Hibernate valida o modelo no banco automatizado;
- suíte completa do backend e build Angular verificados ao concluir a fatia.

## Limites ainda abertos

- retenção, arquivamento e proteção append-only no PostgreSQL permanecem
  decisões operacionais;
- rate limiting e desafios continuam locais à instância;
- PostgreSQL real, navegador real e múltiplas réplicas não foram exercitados;
- a suíte ampliada de enumeração, força bruta, replay e concorrência foi
  consolidada pela fatia 024.
