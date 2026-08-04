# Fatia vertical 033 — carga nos endpoints críticos

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADRs exercitadas:** 001, 002, 005 e 006

## Capacidade entregue

O repositório passa a ter um perfil k6 reproduzível para discovery OIDC, JWK
Set e início do Authorization Code com PKCE. O workload usa taxa constante,
separa os endpoints por tags e falha automaticamente quando ultrapassa limites
de erro, latência ou capacidade de agendamento.

O overlay `compose.load-test.yaml` fixa a imagem oficial do k6, espera a saúde
do backend e monta o script somente para leitura. Duração e taxas podem variar
por ambiente sem editar o cenário ou incluir credenciais no repositório.
O health indicator de SMTP é desabilitado apenas nesse overlay, já que e-mail
não pertence ao workload e nenhum servidor SMTP é provisionado pelo Compose.

## Invariantes e critérios

- readiness deve responder antes do início da medição;
- discovery e JWK Set devem responder `200`;
- autorização deve responder `302` para uma interação opaca;
- checks e falhas HTTP têm tolerâncias explícitas;
- p95 e p99 são avaliados individualmente para cada endpoint;
- qualquer iteração descartada reprova o cenário;
- nomes fixos de requisição evitam cardinalidade por `state` e `nonce`;
- nenhuma senha, client secret, token ou identificador pessoal é solicitado ou
  registrado.

## Evidências executadas

- script validado pela imagem k6 fixada no Compose;
- configuração combinada do Compose validada;
- baseline local de 30 segundos contra backend e PostgreSQL 17 reais executado
  em Docker Desktop, sem limites explícitos de CPU ou memória: 1.654
  requisições, 55,09 req/s, 1.654 checks aprovados, zero falhas e zero
  iterações descartadas;
- p95 local de 2,86 ms em discovery, 2,26 ms no JWK Set e 17,99 ms no início
  da autorização; respectivos p99 de 4,84 ms, 3,65 ms e 25,14 ms;
- suíte completa do backend e verificação de whitespace executadas ao concluir.

## Limites ainda abertos

- o resultado local caracteriza apenas esta execução compartilhada no Docker
  Desktop, sem isolamento ou inventário de hardware, e não é um benchmark de
  produção;
- login, MFA e recuperação foram excluídos para não contornar proteções contra
  abuso nem bloquear identidades reais;
- token, refresh token, resource server, soak, stress e capacidade máxima não
  foram medidos por esta fatia;
- os thresholds ainda precisam ser calibrados com volume e SLOs reais;
- runbooks de incidente, rotação e recuperação são a próxima etapa sequencial.
