# ADR-014: MFA TOTP e códigos de recuperação

- **Status:** aceito
- **Data:** 2026-08-03

## Contexto

Senha isolada não oferece uma segunda prova contra reutilização de credenciais.
O segundo fator precisa integrar o fluxo oficial de autorização sem transformar
o Angular em autoridade de autenticação nem persistir segredos recuperáveis sem
proteção.

## Decisão

- MFA é opcional por identidade e usa TOTP de seis dígitos, SHA-1, período de
  30 segundos e janela de tolerância de um período em cada direção;
- o segredo possui 160 bits aleatórios e é cifrado com AES-256-GCM por uma chave
  externa ao banco e ao repositório;
- a ativação só ocorre depois da confirmação de um TOTP válido;
- a senha válida cria apenas um desafio pendente, vinculado à interação e à
  sessão por cinco minutos; a sessão autenticada nasce depois do segundo fator;
- um passo TOTP aceito é persistido e não pode ser reutilizado;
- oito códigos de recuperação de alta entropia são exibidos somente na criação
  ou regeneração, persistidos apenas como SHA-256 e consumidos atomicamente;
- ativar ou desabilitar MFA incrementa `credential_version`, revoga grants e
  refresh tokens e expira sessões SSO anteriores;
- regenerar códigos invalida imediatamente todo o conjunto anterior.

## Consequências

- aplicativos autenticadores padronizados funcionam sem integração proprietária;
- comprometer somente o banco não revela o segredo TOTP nem códigos de
  recuperação utilizáveis;
- a chave `IDENTITY_HUB_MFA_ENCRYPTION_KEY` passa a ser configuração operacional
  obrigatória e precisa de backup e rotação planejada;
- o registro da última janela impede replay, mas um código já usado na ativação
  não pode ser reutilizado para um login no mesmo período;
- a implantação única continua sendo a fronteira atual do desafio pendente e do
  registro de sessões.

## Alternativas consideradas

### Enviar OTP por e-mail

Rejeitado como segundo fator principal porque depende do mesmo canal usado para
cadastro e recuperação e oferece proteção inferior contra comprometimento do
e-mail.

### Persistir o segredo TOTP em texto claro

Rejeitado porque uma leitura indevida do banco permitiria gerar o segundo fator.

### Criar a sessão logo após validar a senha

Rejeitado porque endpoints autenticados poderiam ser acessados antes da
conclusão do MFA.

## Evidências exigidas

- migração para configuração MFA e códigos de recuperação;
- segredo cifrado e códigos persistidos somente como hash;
- ativação confirmada por TOTP e desafio pós-senha;
- rejeição de TOTP repetido e consumo único de código de recuperação;
- interface de configuração e desafio no Angular;
- build e testes de integração verdes.
