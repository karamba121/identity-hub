# ADR-013: Invalidação após eventos críticos de credencial

- **Status:** aceito
- **Data:** 2026-08-03

## Contexto

Alterar a senha sem encerrar acessos anteriores mantém utilizável uma sessão
possivelmente comprometida. Remover somente refresh tokens também é
insuficiente: access tokens JWT continuam válidos até expirar e o cookie SSO
pode emitir uma nova autorização sem solicitar credenciais.

## Decisão

- cada identidade possui uma `credential_version` monotônica;
- access tokens e ID tokens carregam essa versão como claim textual;
- resource servers internos validam issuer, audience e a versão corrente da
  identidade, além de exigir conta ativa e e-mail confirmado;
- a conclusão válida da recuperação incrementa a versão na mesma transação da
  troca de senha;
- todas as autorizações OAuth do principal são removidas por
  `OAuth2AuthorizationService`, preservando a revogação transacional das
  famílias de refresh token e seu histórico;
- sessões servlet autenticadas são registradas por principal e marcadas como
  expiradas somente depois do commit da transação;
- as cadeias com estado rejeitam a sessão expirada na requisição seguinte;
- novos eventos críticos de credencial deverão reutilizar o mesmo serviço.

## Consequências

- access tokens antigos falham imediatamente nos resource servers internos;
- refresh tokens, grants persistidos e sessões SSO do usuário deixam de
  conceder novo acesso;
- sessões e tokens de outras identidades não são alterados;
- validar JWT passa a consultar a identidade persistida, trocando validação
  totalmente local por revogação imediata;
- resource servers externos que validem JWT sem consultar o Identity Hub só
  observarão a revogação no vencimento do token; introspecção ou distribuição
  de eventos será necessária quando esse cenário for entregue;
- o registro de sessões é local à instância e precisará de armazenamento ou
  propagação coordenada antes de escalar horizontalmente.

## Alternativas consideradas

### Aguardar a expiração do access token

Rejeitado porque deixa uma janela de uso justamente após um evento de alto
risco.

### Remover somente autorizações OAuth

Rejeitado porque não invalida JWT já emitido nem o cookie SSO.

### Persistir todos os access tokens como opacos

Rejeitado nesta etapa porque muda o contrato atual e o perfil dos resource
servers; a versão de credencial mantém JWT e fornece revogação imediata aos
recursos internos.

## Evidências exigidas

- migração da versão de credencial e índice de autorizações por principal;
- teste completo emitindo access e refresh tokens antes da recuperação;
- grant removido, família revogada e refresh token rejeitado após o evento;
- access token anterior rejeitado por versão divergente;
- sessão SSO do principal marcada como expirada somente após commit;
- nova mensagem da interface informando o encerramento dos acessos anteriores.
