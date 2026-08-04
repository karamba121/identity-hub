# Fatia vertical 005 — observabilidade e resiliência da sessão

## Capacidade entregue

O ciclo de refresh token agora produz métricas operacionais no Micrometer e as
expõe no formato Prometheus pelo endpoint protegido
`/actuator/prometheus`. A mesma fatia comprova que uma indisponibilidade da
persistência interrompe a renovação: nenhum novo token é concedido e a falha é
classificada como `unavailable`.

Os contadores de eventos só avançam depois do commit da transação. Assim, uma
rotação ou revogação revertida pelo banco não aparece como concluída nas
métricas.

## Métricas e cardinalidade

- `identity_hub.session.refresh.events`, contador com a dimensão fechada
  `event`: `family_created`, `rotated`, `revoked` ou `replay_detected`;
- `identity_hub.session.refresh.duration`, timer com a dimensão fechada
  `outcome`: `success`, `rejected`, `unavailable` ou `error`.

As dimensões são registradas antecipadamente e valores fora desse catálogo são
rejeitados. Usuário, cliente, família, token, endereço IP, mensagem e classe de
exceção não são tags. A resposta Prometheus também é testada para não conter o
refresh token usado no fluxo.

O endpoint não é público. `health` continua acessível para sondas, enquanto
`/actuator/prometheus` exige autenticação pela cadeia de segurança da
aplicação. A autenticação operacional dedicada por Bearer token externo foi
entregue na fatia 032; o acesso por sessão continua disponível para diagnóstico
manual autenticado.

## Invariantes e evidências

- criação de família, rotação, replay e revogação incrementam apenas seus
  contadores de domínio;
- tentativa válida e rejeitada incrementam timers com resultados distintos;
- falha de acesso ao repositório é classificada como `unavailable`;
- uma falha de persistência é propagada e não produz resposta de token;
- o endpoint Prometheus retorna `401` sem autenticação e `200` autenticado;
- nomes de métricas e dimensões são limitados por código, sem dados sensíveis;
- os sete testes de integração do ciclo de refresh token exercitam os cenários
  positivos, replay, concorrência, revogação, métricas e indisponibilidade.

## Limites ainda abertos

- a indisponibilidade foi simulada na fronteira do repositório sobre o banco H2;
  uma queda real do PostgreSQL e sua recuperação ainda exigem teste operacional;
- a coleta, o painel e as regras de alerta foram adicionados na fatia 032, mas
  ainda precisam ser exercitados com tráfego e infraestrutura reais;
- logs estruturados, traces OpenTelemetry e auditoria durável continuam itens
  separados do roadmap;
- o comportamento de indisponibilidade de Redis será definido quando existir
  um fluxo real que justifique a introdução dessa dependência.
