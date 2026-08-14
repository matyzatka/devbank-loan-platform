param(
    [switch]$Build,
    [switch]$Cleanup,
    [int]$StartupTimeoutSeconds = 180,
    [int]$FlowTimeoutSeconds = 45
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$apiPort = if ($env:API_PORT) { $env:API_PORT } else { '8080' }
$frontendPort = if ($env:FRONTEND_PORT) { $env:FRONTEND_PORT } else { '3000' }
$apiBaseUrl = "http://localhost:$apiPort"
$frontendUrl = "http://localhost:$frontendPort/applications"

function Invoke-Compose {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)
    & docker compose --project-directory $projectRoot @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Wait-Until {
    param(
        [scriptblock]$Condition,
        [int]$TimeoutSeconds,
        [string]$FailureMessage
    )
    $deadline = [DateTimeOffset]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        try {
            if (& $Condition) { return }
        } catch {
            # Services are expected to reject connections while Compose is converging.
        }
        Start-Sleep -Seconds 2
    } while ([DateTimeOffset]::UtcNow -lt $deadline)
    throw $FailureMessage
}

Push-Location $projectRoot
try {
    $upArguments = @('up', '-d')
    if ($Build) { $upArguments += '--build' }
    Invoke-Compose @upArguments

    Wait-Until -TimeoutSeconds $StartupTimeoutSeconds `
        -FailureMessage "Backend did not become ready within $StartupTimeoutSeconds seconds." `
        -Condition {
            $health = Invoke-RestMethod -Uri "$apiBaseUrl/actuator/health/readiness" -TimeoutSec 5
            $health.status -eq 'UP'
        }

    $frontendResponse = Invoke-WebRequest -UseBasicParsing -Uri $frontendUrl -TimeoutSec 10
    if ($frontendResponse.StatusCode -ne 200) {
        throw "Frontend returned HTTP $($frontendResponse.StatusCode)."
    }

    $idempotencyKey = "smoke-$([guid]::NewGuid())"
    $requestId = "smoke-request-$([guid]::NewGuid())"
    $body = @{
        customerId = 'Orlice Industrial Systems s.r.o.'
        amount = 12500000
        currency = 'CZK'
    } | ConvertTo-Json
    $created = Invoke-RestMethod `
        -Method Post `
        -Uri "$apiBaseUrl/api/v1/applications" `
        -Headers @{ 'Idempotency-Key' = $idempotencyKey; 'X-Correlation-ID' = $requestId } `
        -ContentType 'application/json' `
        -Body $body `
        -TimeoutSec 10

    if ($created.status -ne 'SUBMITTED') {
        throw "New application started in unexpected state '$($created.status)'."
    }

    $applicationId = $created.id
    Wait-Until -TimeoutSeconds $FlowTimeoutSeconds `
        -FailureMessage "Application $applicationId did not reach UNDER_REVIEW within $FlowTimeoutSeconds seconds." `
        -Condition {
            $script:processed = Invoke-RestMethod `
                -Uri "$apiBaseUrl/api/v1/applications/$applicationId/processing" `
                -TimeoutSec 5
            $detail = Invoke-RestMethod `
                -Uri "$apiBaseUrl/api/v1/applications/$applicationId" `
                -TimeoutSec 5
            $detail.status -eq 'UNDER_REVIEW' -and
                $null -ne $script:processed.preprocessing -and
                $script:processed.statusHistory.Count -ge 2
        }

    if (-not $processed.preprocessing.eventId) {
        throw 'Processing evidence does not contain an eventId.'
    }
    if (-not ($processed.statusHistory | Where-Object { $_.requestId -eq $requestId })) {
        throw 'Audit history does not contain the smoke-test requestId.'
    }

    Write-Host "SMOKE TEST PASSED: applicationId=$applicationId eventId=$($processed.preprocessing.eventId) status=UNDER_REVIEW"
} catch {
    Write-Error $_
    Invoke-Compose logs --tail=150 loan-api processing-worker kafka postgres frontend
    exit 1
} finally {
    if ($Cleanup) {
        Invoke-Compose down --volumes --remove-orphans
    }
    Pop-Location
}
