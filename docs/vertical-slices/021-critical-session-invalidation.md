# Fatia vertical 021 — invalidação após eventos críticos

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADR exercitada:** 013

## Capacidade entregue

A conclusão da recuperação de senha agora incrementa a versão da credencial,
remove todas as autorizações OAuth do usuário, revoga suas famílias de refresh
token e expira suas sessões SSO. A interface confirma que acessos anteriores
foram encerrados e direciona a pessoa para uma nova autenticação.

Access tokens e ID tokens novos carregam `credential_version`. As APIs
demonstrativa e administrativa consultam a versão corrente da identidade ao
validar JWT; tokens emitidos antes da troca de senha passam a responder `401`.

## Consistência transacional

A senha, a versão e a remoção dos grants são alteradas na mesma transação. O
registro de sessões servlet só é modificado depois do commit, evitando encerrar
cookies quando a mudança persistida for revertida. A remoção usa o serviço OAuth
decorado existente, portanto mantém a trilha de revogação das famílias.

## Evidências executadas

- teste integrado percorre Authorization Code + PKCE, emite access e refresh
  tokens, acessa a API e conclui uma recuperação real;
- após o evento, o teste comprova versão incrementada, grant removido, sessão
  SSO expirada, JWT antigo rejeitado e refresh token recusado;
- os testes de recuperação verificam também o incremento da versão;
- a suíte completa do backend, o build Angular e as diferenças foram
  verificados após a implementação.

## Limites ainda abertos

- somente a recuperação de senha é hoje um evento crítico implementado;
- o registro de sessões e a checagem de versão atendem à implantação única;
- resource servers externos sem consulta ao Identity Hub ainda dependem do
  vencimento do JWT;
- PostgreSQL real, múltiplas réplicas e navegador real não foram exercitados.
