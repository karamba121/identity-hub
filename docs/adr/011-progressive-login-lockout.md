# ADR-011: Bloqueio progressivo de autenticação

- **Status:** aceito
- **Data:** 2026-08-03

## Contexto

Uma senha resistente não impede tentativas online repetidas. Bloqueios
permanentes, por outro lado, permitem negação de serviço contra uma conta, e
respostas distintas para conta bloqueada criam um oráculo de enumeração.

O mecanismo precisa abranger qualquer autenticação de usuário executada pelo
`AuthenticationManager`, preservar a resposta pública atual e manter estado
consistente quando falhas chegam simultaneamente.

## Decisão

- o estado fica na identidade, por meio de contador de falhas, instante da
  última falha e prazo de bloqueio;
- as quatro primeiras falhas não bloqueiam; a quinta bloqueia por um minuto;
- depois de o prazo expirar, cada nova falha dobra o período para dois, quatro,
  oito e no máximo quinze minutos;
- tentativas durante bloqueio ativo são rejeitadas sem incrementar o contador
  nem renovar o prazo;
- um login válido limpa contador, prazo e instante da última falha;
- uma recuperação de senha concluída também limpa o bloqueio;
- identidades desabilitadas, ainda não verificadas ou inexistentes não recebem
  estado de bloqueio;
- a atualização usa bloqueio pessimista da identidade para não perder
  incrementos concorrentes;
- um provedor decorador envolve o provedor DAO central, evitando que novos
  controllers precisem repetir a regra;
- credencial inválida, conta bloqueada e conta inelegível continuam produzindo
  `401 Credenciais inválidas` no contrato público;
- limiar e durações são configuráveis, com validação fechada na inicialização.

## Consequências

- o atacante precisa aguardar intervalos crescentes para continuar tentando a
  mesma conta;
- requisições durante o bloqueio não podem mantê-lo indefinidamente ativo;
- a persistência recebe uma escrita por falha válida antes do bloqueio;
- bloqueio por identidade não substitui rate limiting por sinais combinados,
  que permanece como o próximo item do roadmap;
- não revelar o bloqueio impede a UI de exibir um contador ou prazo específico.

## Alternativas consideradas

### Bloqueio permanente após um número fixo de falhas

Rejeitado porque transformaria qualquer endereço conhecido em alvo simples de
negação de serviço e exigiria intervenção administrativa.

### Controlar somente por IP

Rejeitado porque endereços são compartilhados e rotacionáveis. IP poderá ser
um dos sinais do rate limiting, mas não é a identidade protegida nesta decisão.

### Implementar a contagem no controller

Rejeitado porque outros pontos de autenticação poderiam ignorar a proteção.

## Evidências exigidas

- bloqueio somente a partir do limiar;
- progressão após expiração e teto de duração;
- tentativa durante bloqueio sem extensão;
- login válido e recuperação limpando o estado;
- contas inexistentes e inelegíveis sem novo estado persistido;
- mesma resposta HTTP para senha inválida e conta temporariamente bloqueada;
- teste de concorrência dedicado permanece no item de abuso do roadmap.
