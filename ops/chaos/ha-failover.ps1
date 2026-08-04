param(
    [int]$HttpPort = 4280,
    [switch]$KeepEnvironment
)

$ErrorActionPreference = 'Stop'
$projectName = 'identity-hub-ha-chaos'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$secretDirectory = Join-Path $root 'secrets\ha-chaos'
$privateKey = Join-Path $secretDirectory 'private.pem'
$publicKey = Join-Path $secretDirectory 'public.pem'
$compose = @('-p', $projectName, '-f', 'compose.yaml', '-f', 'compose.ha.yaml')

function ConvertTo-Pem([string]$Label, [byte[]]$Bytes) {
    $base64 = [Convert]::ToBase64String($Bytes, [Base64FormattingOptions]::InsertLineBreaks)
    return "-----BEGIN $Label-----`n$base64`n-----END $Label-----`n"
}

function Invoke-Healthy(
    [string]$Uri,
    [Microsoft.PowerShell.Commands.WebRequestSession]$WebSession,
    [int]$Attempts = 60
) {
    for ($attempt = 1; $attempt -le $Attempts; $attempt++) {
        try {
            $request = @{ Uri = $Uri; TimeoutSec = 3; SkipHttpErrorCheck = $true }
            if ($null -ne $WebSession) { $request.WebSession = $WebSession }
            $response = Invoke-WebRequest @request
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) { return $response }
        } catch { }
        Start-Sleep -Seconds 2
    }
    throw "Endpoint não ficou saudável: $Uri"
}

function Invoke-Compose([string[]]$Arguments) {
    & docker compose @compose @Arguments
    if ($LASTEXITCODE -ne 0) { throw "docker compose falhou: $($Arguments -join ' ')" }
}

New-Item -ItemType Directory -Force -Path $secretDirectory | Out-Null
$rsa = [Security.Cryptography.RSA]::Create(2048)
[IO.File]::WriteAllText($privateKey, (ConvertTo-Pem 'PRIVATE KEY' $rsa.ExportPkcs8PrivateKey()))
[IO.File]::WriteAllText($publicKey, (ConvertTo-Pem 'PUBLIC KEY' $rsa.ExportSubjectPublicKeyInfo()))
$rsa.Dispose()

$env:IDENTITY_HUB_DB_PASSWORD = 'chaos-database-password'
$env:IDENTITY_HUB_REDIS_PASSWORD = 'chaos-redis-password'
$env:IDENTITY_HUB_MFA_ENCRYPTION_KEY = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
$env:IDENTITY_HUB_DEMO_PASSWORD = 'ChaosSecureAccessPhrase123!'
$env:IDENTITY_HUB_PUBLIC_URL = "http://localhost:$HttpPort"
$env:IDENTITY_HUB_HTTP_PORT = "$HttpPort"
$env:IDENTITY_HUB_HA_PRIVATE_KEY_FILE = $privateKey
$env:IDENTITY_HUB_HA_PUBLIC_KEY_FILE = $publicKey
$baseUrl = "http://localhost:$HttpPort"

try {
    Invoke-Compose @('up', '-d', '--build', '--scale', 'backend=2')
    Invoke-Healthy "$baseUrl/.well-known/openid-configuration" | Out-Null

    $browser = [Microsoft.PowerShell.Commands.WebRequestSession]::new()
    $verifier = 'ha-chaos-verifier-with-more-than-forty-three-characters-123456789'
    $sha = [Security.Cryptography.SHA256]::HashData([Text.Encoding]::ASCII.GetBytes($verifier))
    $challenge = [Convert]::ToBase64String($sha).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $authorize = "$baseUrl/oauth2/authorize?response_type=code&client_id=identity-hub-demo" +
        "&redirect_uri=$([Uri]::EscapeDataString("$baseUrl/demo/callback"))" +
        "&scope=openid%20profile&state=ha-chaos&nonce=ha-chaos" +
        "&code_challenge=$challenge&code_challenge_method=S256"
    $start = $null
    try {
        Invoke-WebRequest -Uri $authorize -WebSession $browser -MaximumRedirection 0 -ErrorAction Stop | Out-Null
    } catch {
        $start = $_.Exception.Response
    }
    if ($start.StatusCode -ne 302) { throw "Autorização não criou interação: $($start.StatusCode)" }
    $location = $start.Headers.Location.ToString()
    $interactionId = [Uri]::UnescapeDataString(
        (($location -split 'interaction_id=', 2)[1] -split '&', 2)[0])
    if ([string]::IsNullOrWhiteSpace($interactionId)) { throw 'interaction_id ausente' }
    Invoke-Healthy "$baseUrl/api/v1/interactions/$interactionId" $browser | Out-Null
    $csrf = [Uri]::UnescapeDataString($browser.Cookies.GetCookies($baseUrl)['XSRF-TOKEN'].Value)
    $loginBody = '{"email":"missing-ha-chaos@example.test","password":"invalid-password"}'
    for ($attempt = 1; $attempt -le 9; $attempt++) {
        $login = Invoke-WebRequest -Uri "$baseUrl/api/v1/interactions/$interactionId/login" `
            -Method Post -WebSession $browser -ContentType 'application/json' `
            -Headers @{ 'X-XSRF-TOKEN' = $csrf } -Body $loginBody -SkipHttpErrorCheck
        $expected = if ($attempt -eq 9) { 429 } else { 401 }
        if ($login.StatusCode -ne $expected) {
            throw "Rate limit distribuído retornou $($login.StatusCode) na tentativa $attempt; " +
                "esperado $expected; resposta: $($login.Content)"
        }
    }
    $jwkBefore = (Invoke-WebRequest -Uri "$baseUrl/oauth2/jwks" -WebSession $browser).Content

    $backendIds = @(& docker compose @compose ps -q backend)
    if ($LASTEXITCODE -ne 0 -or $backendIds.Count -ne 2) { throw 'Duas réplicas backend não foram encontradas' }
    & docker stop $backendIds[0] | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível interromper a réplica backend' }

    Invoke-Healthy "$baseUrl/.well-known/openid-configuration" | Out-Null
    Invoke-Healthy "$baseUrl/api/v1/interactions/$interactionId" $browser | Out-Null
    $jwkAfter = (Invoke-Healthy "$baseUrl/oauth2/jwks" $browser).Content
    if ($jwkBefore -ne $jwkAfter) { throw 'O JWK Set variou entre réplicas' }

    $redisId = & docker compose @compose ps -q redis
    & docker stop $redisId | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível interromper o Redis' }
    Start-Sleep -Seconds 3
    $failedClosed = Invoke-WebRequest -Uri "$baseUrl/api/v1/interactions/$interactionId" -WebSession $browser -SkipHttpErrorCheck
    if ($failedClosed.StatusCode -lt 500) { throw 'A perda do estado compartilhado não falhou de forma fechada' }
    & docker start $redisId | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível reiniciar o Redis' }
    Invoke-Healthy "$baseUrl/api/v1/interactions/$interactionId" $browser | Out-Null

    & docker start $backendIds[0] | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível reiniciar a réplica backend' }
    Write-Host 'HA chaos PASS: sessão e rate limit foram compartilhados, failover preservou JWK e Redis falhou fechado.'
} finally {
    if (-not $KeepEnvironment) {
        & docker compose @compose down --volumes --remove-orphans | Out-Null
        foreach ($generatedKey in @($privateKey, $publicKey)) {
            if (Test-Path -LiteralPath $generatedKey) {
                Remove-Item -LiteralPath $generatedKey -Force
            }
        }
        if ((Test-Path -LiteralPath $secretDirectory) -and
            -not (Get-ChildItem -LiteralPath $secretDirectory -Force)) {
            Remove-Item -LiteralPath $secretDirectory
        }
    }
}
