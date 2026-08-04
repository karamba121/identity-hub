# Fatia vertical 030 — rotação planejada de chaves de assinatura

- **Estado:** entregue
- **Data:** 2026-08-04
- **ADRs exercitadas:** 002, 005 e 007

## Capacidade entregue

O modo `rotating-pem` recebe dois pares RSA externos: a chave atual e a próxima
chave. O material é carregado e validado na inicialização, mas a seleção da chave
de assinatura é reavaliada pelo relógio a cada consulta ao `JWKSource`; portanto,
a ativação ocorre no instante UTC configurado sem reiniciar o processo.

Antes da ativação, a chave atual assina e a próxima já é publicada sem material
privado. A partir da ativação, a próxima chave assina e a anterior continua
publicada durante a janela de retenção. Encerrada a janela, a chave anterior é
retirada do JWK Set.

## Invariantes de segurança

- os dois pares usam as mesmas validações de formato, tamanho e correspondência
  criptográfica do modo `pem`;
- `activation-at` é obrigatório e usa um instante ISO-8601 absoluto em UTC;
- os `kid` e os materiais criptográficos dos pares devem ser distintos;
- a retenção aceita valores de 5 minutos a 7 dias e assume 10 minutos por padrão;
- somente a chave ativa permanece privada no JWK Set consultado pelo encoder;
- a chave futura ou anterior é exposta apenas como JWK pública;
- configuração incompleta ou inconsistente interrompe a inicialização.

## Evidências executadas

- seleção da chave atual imediatamente antes da ativação;
- troca para a próxima chave exatamente no instante agendado;
- token anterior e token novo verificáveis durante a sobreposição;
- retirada da chave anterior ao encerrar a retenção, sem retirar a chave nova;
- rejeição de agendamento ausente, `kid` duplicado e retenção abaixo do mínimo;
- binding do Spring e contexto do Authorization Server preservados.

## Limites ainda abertos

- geração dos pares continua sendo uma ação operacional externa; a aplicação
  nunca grava chave privada em disco;
- o operador precisa publicar a configuração antes do instante agendado para que
  resource servers tenham tempo de atualizar o cache do JWK Set;
- depois da retirada, o par novo deve ser promovido a atual em uma atualização
  posterior da configuração;
- não houve rotação em um cluster real nem exercício com arquivos montados por um
  gerenciador de secrets; backup, recuperação e runbook de comprometimento seguem
  nas próximas fatias.
