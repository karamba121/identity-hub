# Alta disponibilidade e testes de caos

## Topologia suportada

`compose.ha.yaml` escala somente a camada de aplicação. O frontend Nginx resolve
continuamente o DNS `backend`, e duas ou mais réplicas compartilham:

- PostgreSQL para identidades, clientes, autorizações e auditoria;
- Redis para sessões indexadas e rate limiting atômico;
- o mesmo par RSA PEM externo, chave MFA e configurações de issuer.

Use `.env.ha.example` como inventário e execute:

```powershell
Copy-Item .env.ha.example .env
# substitua senhas, chave MFA, URL HTTPS e caminhos dos PEMs
docker compose -f compose.yaml -f compose.ha.yaml up -d --build --scale backend=2
docker compose -f compose.yaml -f compose.ha.yaml ps
```

O modo `generated` é proibido nessa topologia: cada processo produziria uma
chave diferente. Os PEMs montados devem ser somente leitura e vir de cofre ou
mecanismo equivalente, nunca do Git.

## Contratos de falha

- perda de uma réplica: DNS remove o nó e outra réplica continua a sessão;
- perda do Redis: sessões e operações limitadas falham fechadas; readiness fica
  indisponível e não há fallback local;
- perda do PostgreSQL: readiness fica indisponível e operações duráveis falham;
- divergência de chaves ou issuer entre réplicas: abortar a implantação;
- uma única instância Redis/PostgreSQL do Compose não representa HA desses dados.

Prometheus usa DNS service discovery para coletar cada endereço retornado pelo
serviço `backend`. Em outra plataforma, substitua essa descoberta pelo mecanismo
nativo e preserve uma série `instance` distinta por réplica.

## Cenário automatizado

O script `ops/chaos/ha-failover.ps1` cria um projeto Compose isolado, gera um par
RSA descartável dentro da pasta ignorada `secrets/`, sobe duas réplicas e valida:

1. sessão compartilhada e limite combinado após nove tentativas;
2. continuidade da interação depois de interromper uma réplica;
3. JWK Set idêntico antes e depois do failover;
4. falha fechada durante perda do Redis e recuperação posterior.

```powershell
.\ops\chaos\ha-failover.ps1
```

Por padrão, o script remove apenas contêineres, rede, volumes e chaves
descartáveis do projeto `identity-hub-ha-chaos` ao terminar. Use
`-KeepEnvironment` somente para diagnóstico e remova o ambiente manualmente.

## Limites de produção

O exercício local não comprova SLO, failover entre zonas, fencing, quorum,
latência sob degradação, perda do host, rolling upgrade nem recuperação de um
serviço gerenciado. Esses cenários precisam ser executados na plataforma alvo
com autorização operacional, observabilidade e critérios de abortagem.
