# ADR-005: Ciclo de vida de tokens e chaves

- **Status:** aceito
- **Data:** 2026-07-30

## Contexto

Emitir um token é apenas o começo do problema. Expiração, audience, revogação,
rotação, replay e ciclo de vida das chaves determinam o impacto de vazamentos e
a continuidade operacional.

Chaves fixas versionadas ou geradas novamente a cada reinício tornam a solução
insegura ou instável. Refresh tokens sem rotação ampliam a janela de abuso.

## Decisão

- access tokens serão JWTs assinados assimetricamente no perfil inicial;
- ID tokens existirão somente em fluxos OIDC;
- access tokens terão issuer, audience, sujeito, tempos e escopos mínimos;
- dados pessoais e permissões excessivas não serão colocados em claims por
  conveniência;
- refresh tokens serão opacos, rotacionados a cada uso e associados a uma
  família;
- reutilização confirmada revogará a família correspondente;
- consumo de authorization code e rotação de refresh token serão atômicos;
- JWK Set publicará a chave ativa e chaves anteriores ainda necessárias;
- chaves terão estados de geração, publicação, ativação, retirada e destruição;
- material privado virá de keystore ou KMS apropriado ao ambiente e nunca do
  repositório.

Introspecção será usada somente para tokens ou resource servers cuja estratégia
exija validação online. Não será adicionada como chamada obrigatória para todo
JWT sem justificativa.

## Consequências

- resource servers podem validar access tokens JWT localmente;
- revogação de JWT já emitido não é instantânea sem mecanismo adicional;
- tempos de vida precisam limitar essa janela;
- rotação de chaves exige sobreposição e relógios confiáveis;
- persistência de famílias de refresh token precisa suportar concorrência e
  auditoria;
- indisponibilidade do provedor de chaves deve falhar de forma segura.

## Alternativas consideradas

### Chave simétrica compartilhada com todos os resource servers

Rejeitada porque consumidores capazes de validar também poderiam emitir tokens
e a rotação distribuiria segredo amplamente.

### Access token opaco por padrão

Não escolhido inicialmente; permanece opção para casos que requeiram controle
online de revogação.

### Refresh token reutilizável até expirar

Rejeitado pelo risco maior de replay silencioso.

## Evidências exigidas

- testes de issuer, audience, assinatura, expiração e clock skew;
- testes concorrentes de consumo de code e refresh token;
- detecção de replay e revogação da família;
- rotação de chave mantendo validação durante a janela definida;
- busca automatizada por chaves e tokens acidentalmente versionados;
- runbook de rotação e comprometimento.
