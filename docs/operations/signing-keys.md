# Origem das chaves de assinatura

O Identity Hub seleciona a origem da chave RSA no início do processo. A
aplicação falha durante a inicialização se a fonte configurada não puder fornecer
um par válido; ela não gera silenciosamente outra chave como fallback.

## Modos

| `IDENTITY_HUB_SIGNING_KEY_SOURCE` | Uso | Comportamento |
| --- | --- | --- |
| `generated` | desenvolvimento e testes | gera RSA de 2048 bits e `kid` aleatório em cada inicialização |
| `pem` | ambientes estáveis | carrega chave privada PKCS#8 e pública X.509 de recursos externos `file:` |

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

Conteúdo PEM e chaves nunca são escritos em logs ou respostas. O modo atual
publica uma única chave; geração planejada, janela com chaves anteriores e
procedimentos de rotação serão entregues separadamente.
