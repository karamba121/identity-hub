# Runbook — rotação de chaves de assinatura

Este procedimento cobre rotação RSA planejada e comprometimento. A
[referência de configuração](../operations/signing-keys.md) contém formatos,
limites e todas as variáveis aceitas.

## Dados da mudança

Registre identificador, responsável, aprovador, `kid` atual e próximo, horário
UTC de ativação, retenção da chave anterior, versão da aplicação, réplicas e
resource servers afetados. Registre fingerprints públicos; a chave privada
nunca entra no registro.

Pare antes do deploy se relógios não estiverem sincronizados, se o maior TTL ou
cache de JWK dos consumidores for desconhecido, se não houver como voltar ao
par atual ou se algum nó não puder receber a mesma configuração.

## Rotação planejada

1. Gere fora da imagem um novo par RSA PKCS#8/X.509 com pelo menos 2048 bits;
   3072 bits é o exemplo operacional atual.
2. Armazene o par no cofre, monte-o somente para leitura e escolha um `kid`
   inédito. Não reutilize `kid` com material diferente.
3. Escolha `IDENTITY_HUB_SIGNING_ACTIVATION_AT` no futuro, depois do tempo
   necessário para atualizar todas as réplicas e caches de JWK.
4. Defina `IDENTITY_HUB_SIGNING_PREVIOUS_KEY_RETENTION` cobrindo o access token
   de 5 minutos, clock skew e cache dos consumidores. O backend aceita 5
   minutos a 7 dias e usa 10 minutos por padrão.
5. Configure todos os emissores com `rotating-pem`:

```properties
IDENTITY_HUB_SIGNING_KEY_SOURCE=rotating-pem
IDENTITY_HUB_SIGNING_KEY_ID=<kid-atual>
IDENTITY_HUB_SIGNING_PRIVATE_KEY_LOCATION=file:<privada-atual>
IDENTITY_HUB_SIGNING_PUBLIC_KEY_LOCATION=file:<publica-atual>
IDENTITY_HUB_SIGNING_NEXT_KEY_ID=<kid-novo>
IDENTITY_HUB_SIGNING_NEXT_PRIVATE_KEY_LOCATION=file:<privada-nova>
IDENTITY_HUB_SIGNING_NEXT_PUBLIC_KEY_LOCATION=file:<publica-nova>
IDENTITY_HUB_SIGNING_ACTIVATION_AT=<instante-UTC>
IDENTITY_HUB_SIGNING_PREVIOUS_KEY_RETENTION=<janela>
```

6. Recrie as réplicas de forma controlada. A inicialização deve falhar para
   arquivo ausente, formato inválido, par divergente, chave fraca, `kid`
   repetido ou retenção inválida; não substitua o erro por `generated`.
7. Antes da ativação, confirme dois `kid` no JWK Set e emissão com o atual:

```powershell
$baseUrl = 'https://<identity-hub>'
(Invoke-RestMethod "$baseUrl/oauth2/jwks").keys | Select-Object kid, kty, use, alg
```

8. No instante de ativação, obtenha um token por fluxo controlado e confirme o
   novo `kid`. Um token emitido imediatamente antes deve continuar válido no
   resource server durante a retenção.
9. Depois da retenção, confirme que o `kid` anterior saiu do JWK Set e que token
   novo continua válido. Só então promova o par novo aos campos atuais, prepare
   a próxima rotação e autorize a destruição do material privado antigo segundo
   a política do cofre.

## Abortagem e rollback planejado

Antes da ativação, volte todas as réplicas ao modo `pem` com o par atual e
reimplante. Depois que o novo `kid` começar a assinar, não remova sua chave
pública como rollback automático: tokens já emitidos com ela deixariam de ser
validados. Pause a mudança, mantenha os dois públicos publicados e corrija para
frente, salvo decisão explícita de incidente.

Aborte se as réplicas publicarem conjuntos diferentes, se um resource server
não reconhecer a próxima chave, se a emissão usar o `kid` errado ou se tokens
anteriores falharem antes do fim da retenção.

## Comprometimento

1. Declare incidente crítico, restrinja o material suspeito e preserve apenas
   fingerprint, `kid`, horários e acessos ao cofre como evidência.
2. Gere um terceiro par limpo em ambiente confiável. Não promova a chave
   suspeita nem a mantenha como próxima.
3. Coloque o serviço em manutenção e implante o par limpo como `pem` em todas as
   réplicas. A prioridade é parar assinatura e confiança no material
   comprometido, mesmo que tokens legítimos sejam invalidados.
4. Force atualização ou reinício dos caches JWK dos resource servers e confirme
   que somente o `kid` limpo é aceito. JWTs são autônomos; remover a chave dos
   emissores não basta enquanto consumidores mantiverem o JWK em cache.
5. Valide fluxo novo, issuer, audience e escopo. Exija nova autenticação e
   avalie invalidação do estado OAuth conforme o alcance do comprometimento.
6. Se banco, refresh tokens ou outras credenciais também puderem ter sido
   expostos, use o [runbook de recuperação](recovery.md) e rotacione cada classe
   de segredo separadamente.

Não há rollback para uma chave privada confirmadamente comprometida. O retorno
é sempre para material novo e confiável.
