# Fatia vertical 009 — bootstrap protegido do primeiro administrador

## Capacidade entregue

O provisionamento do primeiro administrador agora é uma operação atômica e
idempotente mesmo quando várias instâncias iniciam simultaneamente. A migração
V7 cria um registro estável em `administrative_bootstrap_lock`, usado como ponto
de exclusão mútua entre processos conectados ao mesmo banco e como referência
imutável ao usuário e tenant provisionados primeiro.

O `FirstAdministratorBootstrapService` abre uma transação, adquire esse registro
com lock pessimista e somente então consulta ou cria o usuário, tenant, papel
`administrator`, permissões e membership. Instâncias concorrentes aguardam a
transação atual e depois reconciliam os registros registrados no lock. Uma
configuração divergente posterior não cria outro administrador.

## Fluxo demonstrável

Quando `identity-hub.bootstrap.enabled=true`, o runner de inicialização delega o
contexto administrativo ao serviço protegido. O provisionamento continua
desabilitado por padrão fora dos ambientes que o habilitam explicitamente e o
cliente OAuth demonstrativo permanece reconciliado pelo runner existente.

## Segurança e evidências

- o lock pertence ao PostgreSQL/H2 e não à memória de uma instância;
- toda a criação do contexto administrativo ocorre na mesma transação do lock;
- as constraints de usuário, tenant, papel e membership continuam como segunda
  barreira contra duplicidade;
- um teste dispara seis provisionamentos simultâneos e comprova que todos
  terminam com exatamente um contexto administrativo e cinco permissões;
- o mesmo teste altera e-mail e tenant depois da conclusão e comprova que a
  configuração divergente não cria um segundo administrador;
- Flyway e `ddl-auto=validate` exercitam a migração V7 nos testes.

## Limites ainda abertos

- a proteção depende de todas as instâncias compartilharem o mesmo banco;
- a credencial inicial ainda deve ser fornecida por segredo do ambiente;
- a remoção ou o rebaixamento do último administrador válido continua pendente;
- ainda não existe API administrativa para executar essas alterações;
- a aplicação da V7 em PostgreSQL real depende de uma execução do ambiente
  Compose.
