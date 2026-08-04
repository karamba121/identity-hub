# Testes de carga dos endpoints críticos

O cenário versionado usa k6 e mede três contratos do servidor de autorização:
discovery OIDC, JWK Set e início do Authorization Code com PKCE. Os dois
primeiros representam leituras frequentes por clientes e resource servers; o
terceiro inclui validação do pedido e persistência da interação opaca.

O overlay desabilita somente o health indicator de SMTP, pois envio de e-mail
não pertence a este workload e o Compose local não provisiona um servidor de
e-mail. Banco e readiness da aplicação continuam obrigatórios.

Login por senha, MFA, recuperação e cadastro ficam deliberadamente fora da
carga padrão. Repeti-los com uma única identidade acionaria corretamente
rate limiting ou bloqueio progressivo e poderia afetar uma conta real. O teste
também não aceita client secret nem senha como parâmetro.

## Perfil e critérios padrão

Durante 30 segundos, o k6 solicita 25 iterações de leitura por segundo — duas
requisições por iteração — e inicia 5 autorizações por segundo. Isso corresponde
a uma meta de 55 requisições por segundo, além da sondagem inicial.

O processo falha quando qualquer condição abaixo não é atendida:

- mais de 0,5% dos checks ou contratos falham;
- mais de 1% das requisições de um workload falha;
- p95 de discovery ou JWK Set chega a 500 ms, ou p99 a 1 segundo;
- p95 do início de autorização chega a 750 ms, ou p99 a 1,5 segundo;
- ao menos uma iteração é descartada por falta de VUs.

Esses valores são um baseline local explícito, não um SLO de produção. Para
comparar execuções, preserve hardware, limites dos containers, estado do banco
e demais cargas da máquina.

## Executar

Defina no `.env` as três credenciais já exigidas pelo Compose base e execute:

```powershell
docker compose -f compose.yaml -f compose.load-test.yaml up -d --build postgres backend
docker compose -f compose.yaml -f compose.load-test.yaml run --rm k6
```

As taxas e a duração podem ser alteradas pelas variáveis documentadas em
`.env.example`. Para uma verificação curta antes do baseline:

```powershell
$env:IDENTITY_HUB_LOAD_TEST_DURATION='10s'
$env:IDENTITY_HUB_LOAD_TEST_READ_RATE='2'
$env:IDENTITY_HUB_LOAD_TEST_AUTHORIZATION_RATE='1'
docker compose -f compose.yaml -f compose.load-test.yaml run --rm k6
```

Um resultado só é válido se o processo terminar com código zero e todos os
thresholds aparecerem como aprovados. Registre data, versão da aplicação,
hardware, configuração e percentis; não compare números obtidos em ambientes
distintos como se fossem equivalentes.

## Cuidados operacionais

Execute somente em ambiente autorizado e isolado. Cada início de autorização
persiste uma interação com expiração curta, portanto o cenário gera dados
operacionais temporários. Não aponte o teste padrão para produção e não eleve
taxas sem verificar espaço no banco, observabilidade e capacidade de
interrupção.

O teste mede capacidade e regressão sob uma carga controlada; não demonstra
capacidade máxima, concorrência geográfica, resistência a DDoS nem desempenho
de login, MFA, emissão de tokens, refresh token ou resource servers.
