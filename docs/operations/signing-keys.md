# Origem das chaves de assinatura

O Identity Hub seleciona a origem da chave RSA no início do processo. A
aplicação falha durante a inicialização se a fonte configurada não puder fornecer
um par válido; ela não gera silenciosamente outra chave como fallback.

## Modos

| `IDENTITY_HUB_SIGNING_KEY_SOURCE` | Uso | Comportamento |
| --- | --- | --- |
| `generated` | desenvolvimento e testes | gera RSA de 2048 bits e `kid` aleatório em cada inicialização |
| `pem` | ambientes estáveis | carrega chave privada PKCS#8 e pública X.509 de recursos externos `file:` |
| `rotating-pem` | rotação planejada | carrega os pares atual e próximo, ativa o próximo no instante agendado e retém a chave pública anterior pela janela segura |

O modo `generated` invalida access tokens e ID tokens ainda vigentes quando o
processo reinicia. Ele não deve ser usado em produção nem em um conjunto de
réplicas.

## Gerar um par PEM

Exemplo com RSA de 3072 bits:

```shell
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out identity-hub-private.pem
openssl pkey -in identity-hub-private.pem -pubout -out identity-hub-public.pem
```

A chave privada gerada está em PKCS#8 (`BEGIN PRIVATE KEY`) e a pública em X.509
SubjectPublicKeyInfo (`BEGIN PUBLIC KEY`). O carregador rejeita PKCS#1, chaves
menores que 2048 bits, arquivos acima de 32 KiB e pares divergentes.

## Configurar

Monte os arquivos fora da imagem, com leitura somente para o usuário do
processo, e configure:

```properties
IDENTITY_HUB_SIGNING_KEY_SOURCE=pem
IDENTITY_HUB_SIGNING_PRIVATE_KEY_LOCATION=file:/run/secrets/identity-hub-private.pem
IDENTITY_HUB_SIGNING_PUBLIC_KEY_LOCATION=file:/run/secrets/identity-hub-public.pem
IDENTITY_HUB_SIGNING_KEY_ID=production-2026-08
```

`IDENTITY_HUB_SIGNING_KEY_ID` é opcional. Sem ele, o servidor deriva um `kid`
estável do thumbprint SHA-256 da chave pública. Não reutilize o mesmo `kid` para
materiais criptográficos diferentes.

## Planejar uma rotação sem interrupção

Gere um novo par fora da aplicação, armazene-o no mesmo mecanismo de secrets do
par atual e escolha um instante UTC que permita publicar a próxima chave antes da
troca. Em seguida, use o modo `rotating-pem`:

```properties
IDENTITY_HUB_SIGNING_KEY_SOURCE=rotating-pem
IDENTITY_HUB_SIGNING_KEY_ID=production-2026-08
IDENTITY_HUB_SIGNING_PRIVATE_KEY_LOCATION=file:/run/secrets/identity-hub-current-private.pem
IDENTITY_HUB_SIGNING_PUBLIC_KEY_LOCATION=file:/run/secrets/identity-hub-current-public.pem
IDENTITY_HUB_SIGNING_NEXT_KEY_ID=production-2026-09
IDENTITY_HUB_SIGNING_NEXT_PRIVATE_KEY_LOCATION=file:/run/secrets/identity-hub-next-private.pem
IDENTITY_HUB_SIGNING_NEXT_PUBLIC_KEY_LOCATION=file:/run/secrets/identity-hub-next-public.pem
IDENTITY_HUB_SIGNING_ACTIVATION_AT=2026-09-01T03:00:00Z
IDENTITY_HUB_SIGNING_PREVIOUS_KEY_RETENTION=10m
```

O ciclo observado pelo servidor é:

1. **publicação:** antes de `activation-at`, a chave atual assina e as chaves
   públicas atual e próxima aparecem no JWK Set;
2. **ativação:** no instante configurado, a próxima chave passa a assinar sem
   reinício, enquanto a anterior permanece publicável;
3. **retirada:** após `previous-key-retention`, a anterior deixa o JWK Set;
4. **promoção:** depois de confirmar a retirada, promova o par novo aos campos
   atuais em uma nova configuração e prepare a rotação seguinte.

A retenção deve cobrir a vida máxima de qualquer JWT assinado pela chave
anterior e a margem operacional para relógios e caches. O backend aceita entre
5 minutos e 7 dias e usa 10 minutos quando o valor é omitido. O access token
atual vive 5 minutos; reduzir a retenção abaixo disso é rejeitado na
inicialização.

Todos os nós emissores devem receber exatamente os mesmos pares, `kid`, instante
de ativação e retenção, com relógios sincronizados. Não avance a configuração de
apenas uma réplica.

O Compose principal expõe as variáveis, mas não monta arquivos automaticamente:
o caminho e o mecanismo de secrets dependem do ambiente. Uma extensão de
deploy deve montar ambos os arquivos em `/run/secrets` e passar as localizações
`file:` correspondentes.

## Controles de inicialização

Antes de construir o JWK Set, o servidor comprova:

- presença e legibilidade dos dois arquivos;
- cabeçalhos PEM e formatos esperados;
- algoritmo RSA e tamanho mínimo;
- igualdade dos módulos e assinatura/verificação de um desafio em memória;
- `key-id` dentro do limite contratual.

No modo de rotação, os mesmos controles são aplicados aos dois pares. O servidor
também rejeita agendamento ausente, `kid` repetido, material repetido e janela de
retenção fora dos limites.

Conteúdo PEM e chaves privadas nunca são escritos em logs ou respostas. A
rotação exercitada nesta etapa cobre a seleção e publicação local; execução em
cluster, backup, recuperação e resposta a comprometimento permanecem trabalho
operacional separado.
