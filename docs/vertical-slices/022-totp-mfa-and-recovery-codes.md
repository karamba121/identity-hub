# Fatia vertical 022 — MFA TOTP e códigos de recuperação

- **Estado:** entregue
- **Data:** 2026-08-03
- **ADR exercitada:** 014

## Capacidade entregue

A pessoa autenticada pode configurar um aplicativo TOTP na área de segurança,
confirmar a posse pelo primeiro código e receber oito códigos de recuperação de
uso único. O estado e a quantidade de códigos restantes são consultáveis; o
conjunto pode ser regenerado e o MFA pode ser desabilitado mediante um fator
válido.

No fluxo OAuth, uma senha correta não cria mais a sessão SSO quando MFA está
ativo. O backend mantém um desafio curto vinculado à interação e só conclui a
autenticação depois de aceitar um TOTP ainda não usado ou consumir um código de
recuperação.

## Proteção dos dados

- segredo TOTP de 160 bits cifrado com AES-256-GCM e nonce aleatório;
- chave de cifra fornecida por `IDENTITY_HUB_MFA_ENCRYPTION_KEY`;
- códigos de recuperação exibidos uma vez e persistidos somente como SHA-256;
- lock pessimista e `last_used_step` impedem consumo concorrente e replay;
- ativação e desativação invalidam tokens e sessões anteriores como eventos
  críticos de credencial.

## Evidências executadas

- teste integrado de configuração, confirmação TOTP, estado, regeneração e
  invalidação do conjunto anterior;
- teste integrado prova que a senha deixa a sessão anônima e que somente o
  segundo fator conclui a interação;
- migrações V1 a V14 aplicadas pelo Flyway em H2 e validadas pelo Hibernate;
- build Angular concluído com a tela de segurança e o desafio de login;
- suíte completa e verificações finais registradas ao concluir a fatia.

## Limites ainda abertos

- a chave de cifra ainda não possui rotação com múltiplas versões;
- desafio e registro de sessão atendem à implantação única;
- auditoria específica do ciclo de vida do MFA foi entregue pela fatia 023;
- PostgreSQL real, navegador real e concorrência em múltiplas instâncias não
  foram exercitados nesta entrega.
