# Fatia vertical 016 — cadastro e verificação de e-mail

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADRs exercitadas:** 002, 006, 007 e 008

## Capacidade entregue

A rota pública `/signup` deixa de ser um formulário demonstrativo e passa a
criar identidades globais no Identity Hub. A API responde `202 Accepted` com a
mesma mensagem para um e-mail novo ou já cadastrado, evitando confirmar pela
resposta pública se a conta existe.

Uma conta recém-criada permanece com `email_verified = false`. O serviço de
autenticação a trata como desabilitada até a confirmação, e o customizador OIDC
passa a emitir o claim `email_verified` a partir do estado persistido em vez de
fixá-lo como verdadeiro.

## Verificação e entrega

O cadastro gera 256 bits aleatórios e persiste somente o hash SHA-256 do token,
com validade configurável, trinta minutos por padrão. O valor original existe
apenas durante a chamada ao provedor SMTP e segue em um link cujo token fica no
fragmento da URL, evitando envio ao servidor em requisições de navegação.

A tela `/verify-email` extrai o fragmento e submete o token no corpo de um POST
protegido por CSRF. A confirmação usa lock pessimista, marca o token como
consumido e habilita o e-mail na mesma transação. Reuso, expiração e valores
desconhecidos recebem a mesma resposta pública.

O remetente, host, porta, autenticação, STARTTLS e prazo do token são
configuráveis por ambiente. O Compose encaminha essas opções ao backend; nenhum
segredo SMTP é versionado.

## Interface

O formulário TailAdmin foi reduzido aos campos reais de nome, e-mail e senha,
removeu botões sociais fictícios e não registra dados no console. Ele obtém o
cookie CSRF antes da mutação, apresenta uma confirmação genérica e direciona o
usuário ao cliente demonstrativo após verificar a conta. A tela de login agora
oferece acesso explícito ao cadastro.

## Evidências executadas

- cadastro persiste senha codificada e conta ainda não verificável;
- usuário pendente não pode autenticar;
- token bruto não aparece na tabela de verificação;
- confirmação ativa a conta e registra o consumo;
- reuso e expiração do token são rejeitados;
- e-mail duplicado mantém resposta `202`, não duplica usuário nem nova entrega;
- POST sem CSRF é rejeitado e o contrato usado pelo Angular emite o cookie
  `XSRF-TOKEN`;
- migrações V1 a V10 são reconstruídas pelo perfil automatizado;
- build Angular inclui as rotas `/signup` e `/verify-email`.

## Limites ainda abertos

- a política completa de senha é o próximo item do roadmap; esta fatia exige
  apenas entre 8 e 200 caracteres e usa o BCrypt já configurado;
- reenvio, troca de e-mail e recuperação de conta ainda não foram entregues;
- a entrega SMTP depende de um servidor configurado e não foi exercitada contra
  um provedor real nesta execução;
- a migração V10 não foi executada em PostgreSQL real e o fluxo visual não foi
  automatizado em navegador.
