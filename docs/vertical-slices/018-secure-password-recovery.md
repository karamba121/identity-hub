# Fatia vertical 018 — recuperação segura de senha

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 008, 009 e 010

## Capacidade entregue

A tela pública `/reset-password` solicita o e-mail e apresenta a mesma mensagem
de aceite independentemente da existência da conta. Contas habilitadas e já
verificadas recebem por SMTP um link para `/recover-password`, onde a pessoa
define e confirma uma nova senha pela política central.

A API expõe `POST /api/v1/password-recovery` e
`POST /api/v1/password-recovery/complete`. Ambas exigem CSRF. A conclusão troca
o hash da credencial por Argon2id e torna o link inutilizável.

## Persistência e consistência

A migração `V11__password_recovery.sql` cria `password_recovery_token`. O token
bruto possui 256 bits aleatórios, 43 caracteres Base64 URL e nunca é persistido;
o banco recebe somente seu SHA-256. Validade, consumo, revogação e criação são
registrados separadamente.

Uma nova emissão bloqueia a identidade, revoga credenciais temporárias ativas e
cria o sucessor com validade padrão de 15 minutos. A conclusão usa a mesma ordem
de bloqueio e trava o token para impedir consumo duplicado. Token desconhecido,
expirado, revogado ou já consumido gera a mesma resposta segura.

## Interface e configuração

O formulário antes estático foi ligado à API e traduzido. O login oferece
acesso à recuperação e a tela de conclusão trata ausência de token, erro seguro
e sucesso. `IDENTITY_HUB_PASSWORD_RECOVERY_TTL` configura a validade; remetente
e conexão reutilizam a configuração SMTP do cadastro.

## Evidências executadas

- cinco testes de integração cobrem resposta não enumerável, armazenamento por
  hash, troca Argon2id, uso único, expiração, revogação do predecessor, política
  de senha e CSRF;
- a migração V11 foi aplicada pela suíte em H2;
- o build de produção Angular foi concluído;
- a suíte completa do backend foi executada após a integração da fatia.

## Limites ainda abertos

- SMTP real, PostgreSQL real e o fluxo em navegador não foram exercitados;
- sessões existentes ainda não são invalidadas após a troca de senha, conforme
  item separado do roadmap;
- o teste concorrente dedicado permanece no item posterior de enumeração,
  brute force, replay e recuperação concorrente.
