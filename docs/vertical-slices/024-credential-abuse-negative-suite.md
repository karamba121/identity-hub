# Fatia vertical 024 — suíte negativa de abuso de credenciais

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 007, 009, 010, 011, 012, 013 e 014

## Capacidade entregue

A suíte automatizada consolida os invariantes de segurança da Fase 6 para
cadastro, login, verificação de e-mail, recuperação de senha e MFA. A entrega
não cria um novo endpoint: transforma as proteções implementadas nas fatias
anteriores em critérios regressivos explícitos.

## Matriz exercitada

| Risco | Evidência automatizada |
| --- | --- |
| Enumeração no cadastro | e-mail novo e existente retornam exatamente o mesmo status e corpo; o existente não é duplicado nem recebe novo link |
| Enumeração na recuperação | conta elegível e e-mail inexistente retornam exatamente a mesma resposta pública; envio ocorre somente para a conta elegível |
| Enumeração no login | credencial incorreta, conta inexistente e conta bloqueada permanecem indistinguíveis na resposta pública |
| Força bruta de senha | falhas acumulam bloqueio progressivo, com prazo crescente e limite máximo; sucesso posterior limpa o estado |
| Rotação de origem | tentativas contra a mesma identidade por origens diferentes atingem o limite por sujeito e recebem `429` |
| Abuso combinado | identidade, origem e combinação possuem limites independentes e `Retry-After` |
| Replay de verificação | token de e-mail aceito uma vez e recusado na repetição |
| Replay de recuperação | token de recuperação aceito uma vez e recusado na repetição |
| Replay de refresh token | reutilização compromete e revoga a família |
| Replay de TOTP e recuperação MFA | janela TOTP e códigos consumidos não podem ser reutilizados |
| Recuperação concorrente | duas trocas simultâneas com o mesmo token produzem exatamente um sucesso e uma rejeição |

## Recuperação concorrente

O novo teste inicia duas transações reais em threads distintas, sincronizadas
antes do consumo do mesmo token. O lock pessimista por identidade serializa os
competidores. Ao final:

- exatamente uma senha candidata é persistida;
- a outra tentativa recebe o mesmo erro genérico de token inválido;
- `credential_version` avança uma única vez;
- apenas um token fica marcado como consumido.

## Evidências executadas

- respostas públicas de cadastro e recuperação comparadas byte a byte;
- limite por identidade comprovado através de quatro origens diferentes;
- recuperação concorrente executada com barreira e dois workers;
- matriz de replay preservada pelas suítes de registro, recuperação, sessão e
  MFA;
- suíte completa do backend verificada ao concluir a fatia.

## Limites ainda abertos

- os testes automatizados usam H2 em modo PostgreSQL; a disputa deve ser
  repetida no PostgreSQL real antes de produção;
- não há teste de carga distribuído nem múltiplas instâncias do limitador;
- comparação de respostas garante igualdade contratual, mas não constitui
  análise estatística de canal lateral de tempo;
- navegador real e ferramentas externas de pentest não foram exercitados.
