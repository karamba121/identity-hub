# ADR-009: Política de senha e evolução de hash

- **Status:** aceito
- **Data:** 2026-08-03

## Contexto

Senhas precisam resistir tanto à escolha previsível quanto à quebra offline da
base. Regras de composição fixas incentivam variações curtas e difíceis de
memorizar, enquanto trocar o algoritmo de hash sem compatibilidade força reset
coletivo ou interrompe usuários existentes.

O Identity Hub já possuía hashes BCrypt sem identificador de algoritmo. A nova
política precisa valer para todas as criações de credencial sem invalidar esse
legado e precisa permitir nova evolução de parâmetros.

## Decisão

- a política central aceita entre 15 e 128 caracteres Unicode normalizados em
  NFC e não remove espaços;
- não há obrigação artificial de maiúscula, minúscula, número ou símbolo;
- caracteres de controle, senhas comuns, repetições triviais, nome do serviço,
  identificador de e-mail e termos relevantes do nome são rejeitados;
- novas senhas usam Argon2id com salt de 16 bytes, hash de 32 bytes,
  19.456 KiB de memória, duas iterações e paralelismo um;
- hashes persistidos carregam o identificador `{argon2id}`;
- hashes BCrypt antigos, inclusive sem prefixo, continuam verificáveis;
- depois de um login legado válido, o hash é recalculado em Argon2id e salvo
  sem alterar a senha em claro;
- a mesma normalização é usada ao criar e verificar a credencial;
- senhas e hashes nunca entram em logs, respostas, auditoria ou documentação.

## Consequências

- novas credenciais usam uma função deliberadamente intensiva em memória;
- usuários existentes migram gradualmente sem reset forçado;
- o prefixo permite alterar algoritmo e parâmetros novamente;
- autenticação passa a realizar uma escrita única quando encontra hash legado;
- o custo precisa ser medido no hardware de produção antes de aumentar
  parâmetros ou capacidade;
- a lista local de senhas bloqueadas precisa evoluir com fontes operacionais
  apropriadas sem enviar a senha para terceiros.

## Alternativas consideradas

### Permanecer apenas em BCrypt

Rejeitada para novas credenciais porque não oferece custo de memória comparável
ao Argon2id, embora permaneça suportada durante a migração.

### Exigir classes de caracteres

Rejeitada porque restringe frases válidas e incentiva padrões previsíveis sem
tratar senhas comuns ou contexto pessoal.

### Invalidar todos os hashes existentes

Rejeitada porque força recuperação em massa e cria risco operacional evitável.

## Evidências exigidas

- testes unitários de comprimento, Unicode, bloqueio e dados contextuais;
- hashes novos com identificador e parâmetros verificáveis;
- salts distintos para a mesma senha;
- autenticação de BCrypt legado seguida de upgrade persistido;
- aplicação da política em todo ponto que cria ou redefine senha;
- busca automatizada por exposição de senha e hash.
