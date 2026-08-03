# Fatia vertical 017 — política de senha e evolução de hash

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADR exercitada:** 009

## Capacidade entregue

`PasswordPolicy` passa a ser a autoridade única para credenciais criadas pelo
cadastro público e pelo bootstrap do primeiro administrador. A política aceita
frases de 15 a 128 caracteres, preserva espaços, normaliza Unicode em NFC e não
impõe regras fixas de composição.

São rejeitados caracteres de controle, escolhas comuns da lista local,
repetições de um único caractere, o nome do Identity Hub e termos derivados do
identificador de e-mail ou do nome da pessoa. A API continua sem devolver a
senha ou qualquer representação do hash.

## Armazenamento e migração

Novas credenciais são codificadas com Argon2id usando salt aleatório de 16
bytes, hash de 32 bytes, 19.456 KiB de memória, duas iterações e paralelismo um.
O valor persistido começa com `{argon2id}`, deixando explícito o algoritmo para
evoluções futuras.

O `DelegatingPasswordEncoder` também reconhece BCrypt prefixado e o formato
legado sem prefixo já existente no banco. Quando um login BCrypt é válido, o
`IdentityUserDetailsService` recebe do provedor de autenticação o novo hash
Argon2id e o persiste na mesma autenticação. Uma senha inválida nunca dispara
upgrade.

`NormalizingPasswordEncoder` garante que cadastro e login usem a mesma forma
Unicode canônica. Nenhuma migração de banco foi necessária porque a coluna
existente comporta o formato versionado.

## Interface

O formulário de cadastro comunica o mínimo de 15 caracteres, recomenda uma
frase e deixa claro que espaços e Unicode são aceitos. A validação visual é
apenas uma conveniência; o backend permanece responsável por toda a política e
retorna o motivo seguro de rejeição.

## Evidências executadas

- frases longas sem composição artificial são aceitas;
- formas Unicode equivalentes são normalizadas;
- comprimento inválido, controles, senhas comuns, repetidas, específicas do
  serviço ou derivadas do usuário são rejeitadas;
- duas codificações da mesma senha geram salts e hashes diferentes;
- o formato Argon2id registra os parâmetros escolhidos;
- um hash BCrypt sem prefixo autentica e é substituído por Argon2id;
- cadastro e bootstrap usam a política central;
- a suíte de cadastro continua protegendo token, CSRF e verificação de e-mail.

## Limites ainda abertos

- a lista local de bloqueio é uma base inicial e ainda não consulta um corpus
  amplo de credenciais comprometidas;
- os parâmetros Argon2id não foram aferidos no hardware de produção;
- recuperação e troca de senha, quando implementadas, deverão reutilizar a
  mesma política;
- a checagem online da recomendação oficial não pôde ser concluída nesta
  execução por falha da ferramenta de consulta.
