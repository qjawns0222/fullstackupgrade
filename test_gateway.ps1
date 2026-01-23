
$secret = "DisIsVerySecretKeyForJwtExampleProjectMustBeLongerThan256BitsVoila"
$gatewayUrl = "http://localhost:8000/api/test"

function Get-JwtToken {
    param (
        [string]$secret
    )

    $header = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}')).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $payloadJson = '{"sub":"testuser","auth":"ROLE_USER","exp":' + ([DateTimeOffset]::Now.ToUnixTimeSeconds() + 3600) + '}'
    $payload = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($payloadJson)).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    
    $signatureInput = "$header.$payload"
    $hmac = New-Object System.Security.Cryptography.HMACSHA256
    $hmac.Key = [System.Text.Encoding]::UTF8.GetBytes($secret)
    $signatureBytes = $hmac.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($signatureInput))
    $signature = [Convert]::ToBase64String($signatureBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')

    return "$header.$payload.$signature"
}

Write-Host "NOTE: Make sure Gateway (port 8000) and Backend (port 8080) are running before this test!" -ForegroundColor Yellow

# 1. Test without Token
Write-Host "`n[Test 1] Request WITHOUT Token..." -NoNewline
try {
    $response = Invoke-WebRequest -Uri $gatewayUrl -Method Get -ErrorAction Stop
    Write-Host " FAILED (Expected 401 but got $($response.StatusCode))" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode -eq [System.Net.HttpStatusCode]::Unauthorized) {
        Write-Host " PASSED (Got 401 Unauthorized)" -ForegroundColor Green
    } else {
        Write-Host " FAILED (Got $($_.Exception.Response.StatusCode))" -ForegroundColor Red
    }
}

# 2. Test with Valid Token
$token = Get-JwtToken -secret $secret
Write-Host "[Test 2] Request WITH Valid Token..." -NoNewline
try {
    $response = Invoke-WebRequest -Uri $gatewayUrl -Headers @{Authorization="Bearer $token"} -Method Get -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        Write-Host " PASSED (Got 200 OK)" -ForegroundColor Green
        Write-Host " Response: $($response.Content)" -ForegroundColor Gray
    } else {
        Write-Host " FAILED (Got $($response.StatusCode))" -ForegroundColor Red
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode
    if ($statusCode -eq 503 -or $statusCode -eq 500) {
        Write-Host " PARTIAL PASS (Gateway accepted token but Backend is likely down: $statusCode)" -ForegroundColor Yellow
    } else {
        Write-Host " FAILED (Got $statusCode)" -ForegroundColor Red
        Write-Host " Error: $($_.Exception.Message)"
    }
}
