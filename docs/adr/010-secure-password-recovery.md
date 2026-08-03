# ADR-010: Recuperação segura de senha

- **Status:** aceito
- **Data:** 2026-08-03

## Contexto

A recuperação de senha é uma emissão temporária de autoridade sobre uma conta.
Um link reutilizável, duradouro ou armazenado em claro transforma vazamento de
banco, histórico ou e-mail em tomada de conta. Respostas diferentes para contas
existentes também permitem enumerar usuários antes de uma tentativa de abuso.

## Decisão

- a solicitação pública sempre responde `202` com a mesma mensagem para conta
  existente, inexistente ou ainda não verificada;
- somente contas habilitadas e com e-mail verificado recebem a mensagem;
- o token contém 256 bits aleatórios e é representado em Base64 URL sem padding,
  totalizando 43 caracteres;
- somente o SHA-256 do token é persistido; o valor bruto existe apenas no link
  enviado ao endereço previamente verificado;
- a validade padrão é de 15 minutos e pode ser configurada;
- uma nova solicitação revoga todos os tokens ativos anteriores da mesma conta;
- consumo e emissão são serializados por bloqueio pessimista da identidade, e o
  token também é bloqueado durante o consumo;
- expiração, revogação, reutilização e token desconhecido produzem o mesmo erro;
- a nova credencial passa pela política central e é codificada em Argon2id antes
  de o token ser consumido;
- endpoints mutáveis continuam protegidos por CSRF e a UI usa o fluxo público do
  TailAdmin definido na ADR-008.

## Consequências

- uma captura do banco não contém credenciais temporárias utilizáveis;
- solicitar novamente invalida links anteriores, inclusive sob solicitações
  serializadas para a mesma conta;
- a troca de senha não invalida sessões existentes nesta fatia; essa proteção
  permanece explícita como o próximo item independente do roadmap;
- indisponibilidade SMTP ainda faz a operação falhar e reverter, sem deixar um
  token silenciosamente ativo que não foi entregue.

## Alternativas consideradas

### Código curto digitável

Rejeitado nesta etapa porque teria entropia menor e exigiria rate limiting e
contagem de tentativas antes de ser seguro. O link Base64 URL continua curto o
suficiente para transporte e preserva 256 bits de entropia.

### Persistir o token bruto

Rejeitado porque permitiria usar diretamente um vazamento da tabela de
recuperação.

### Informar que a conta não existe

Rejeitado por criar um oráculo público de enumeração.

## Evidências exigidas

- respostas idênticas para e-mails existentes e inexistentes;
- token bruto ausente do banco e hash de tamanho esperado;
- troca para hash Argon2id somente após política válida;
- expiração, reutilização e revogação do token anterior rejeitadas;
- CSRF obrigatório nas duas operações mutáveis;
- teste concorrente específico permanece associado ao item de testes de abuso
  e recuperação concorrente do roadmap.
