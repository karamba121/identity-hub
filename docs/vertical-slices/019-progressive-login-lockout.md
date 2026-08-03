# Fatia vertical 019 — bloqueio progressivo de login

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 009 e 011

## Capacidade entregue

Toda autenticação de usuário pelo `AuthenticationManager` passa por
`LoginProtectionAuthenticationProvider`. O decorador registra falha ou sucesso
sem mover a autoridade sobre a senha para o controller de interação.

Após quatro falhas, a quinta tentativa inválida bloqueia a identidade por um
minuto. Uma nova falha depois da expiração dobra o prazo, limitado a quinze
minutos. Enquanto o bloqueio está ativo, novas requisições não alteram estado
nem prolongam a espera.

O endpoint de login continua respondendo somente `Credenciais inválidas`, tanto
para senha incorreta quanto para identidade bloqueada. Por isso a tela TailAdmin
mantém deliberadamente a mesma mensagem e não expõe contador ou prazo.

## Persistência e consistência

A migração `V12__progressive_login_lockout.sql` acrescenta à `identity_user` o
contador, o prazo de bloqueio e o instante da última falha. Cada atualização
trava pessimisticamente a linha da identidade, preservando incrementos quando
requisições concorrem.

Login válido limpa todo o estado. A conclusão da recuperação de senha também
desbloqueia a conta, permitindo que a nova credencial seja usada imediatamente.
Contas inexistentes, desabilitadas ou sem e-mail verificado não acumulam estado.

## Configuração

- `IDENTITY_HUB_LOGIN_FAILURE_THRESHOLD`, padrão `5`;
- `IDENTITY_HUB_LOGIN_INITIAL_LOCK_DURATION`, padrão `1m`;
- `IDENTITY_HUB_LOGIN_MAXIMUM_LOCK_DURATION`, padrão `15m`.

Valores inválidos impedem a inicialização em vez de desativar silenciosamente a
proteção.

## Evidências executadas

- três testes de integração cobrem limiar, progressão, não extensão, desbloqueio
  por prazo, reset por sucesso, contas desconhecidas/inelegíveis e contrato HTTP;
- a suíte de recuperação comprova que a troca válida de senha limpa o bloqueio;
- a migração V12 foi aplicada pela suíte em H2;
- a suíte completa do backend, o build Angular, o Compose e as diferenças foram
  verificados após a implementação.

## Limites ainda abertos

- a migração não foi executada em PostgreSQL real;
- o fluxo não foi exercitado em navegador;
- rate limiting por sinais combinados e testes concorrentes específicos seguem
  como itens distintos do roadmap.
