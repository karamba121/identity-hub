# Resource server independente

Este exemplo consome access tokens JWT emitidos pelo Identity Hub sem acessar o
banco de dados do Authorization Server. Ele valida quatro fronteiras antes de
entregar `GET /api/v1/messages`:

1. a assinatura RSA usando as chaves públicas de `/oauth2/jwks`;
2. o `iss` esperado;
3. a audience `identity-hub-api`;
4. a autoridade derivada do escopo `demo.read`.

## Executar

O caminho recomendado é subir o Compose da raiz. O serviço ficará disponível
em `http://localhost:8081` e buscará as chaves pela rede interna em
`http://backend:8080/oauth2/jwks`.

Para executar fora do Compose:

```powershell
$env:IDENTITY_HUB_ISSUER = 'http://localhost:4200'
$env:IDENTITY_HUB_JWK_SET_URI = 'http://localhost:8080/oauth2/jwks'
mvn.cmd spring-boot:run
```

Depois de cadastrar um cliente confidencial com `demo.read`, troque o segredo
por um token e acesse o recurso:

```powershell
$clientId = 'meu-cliente-confidencial'
$clientSecret = '<segredo exibido na criação>'
$basic = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${clientId}:${clientSecret}"))
$token = Invoke-RestMethod -Method Post -Uri 'http://localhost:4200/oauth2/token' `
  -Headers @{ Authorization = "Basic $basic" } `
  -ContentType 'application/x-www-form-urlencoded' `
  -Body @{ grant_type = 'client_credentials'; scope = 'demo.read' }
Invoke-RestMethod -Uri 'http://localhost:8081/api/v1/messages' `
  -Headers @{ Authorization = "Bearer $($token.access_token)" }
```

O segredo não deve ser salvo no repositório, em arquivos de configuração ou em
logs. Em ambientes reais, forneça-o ao consumidor por um cofre de segredos.

## Configuração

| Variável | Padrão | Função |
| --- | --- | --- |
| `IDENTITY_HUB_ISSUER` | `http://localhost:4200` | valor exato esperado em `iss` |
| `IDENTITY_HUB_JWK_SET_URI` | `http://localhost:8080/oauth2/jwks` | origem das chaves públicas usadas na assinatura |
| `IDENTITY_HUB_RESOURCE_AUDIENCE` | `identity-hub-api` | audience aceita pelo serviço |
| `IDENTITY_HUB_RESOURCE_SCOPE` | `demo.read` | escopo necessário no endpoint |
| `RESOURCE_SERVER_PORT` | `8081` | porta HTTP do exemplo |

Apontar somente para o JWK Set não substitui a validação de issuer e audience;
as três verificações são configuradas explicitamente em
`ResourceServerSecurityConfig`.
