Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:DevBankRegion = 'eu-central-1'
$script:DevBankApplicationStack = 'DevBankDemo-eu-central-1'
$script:DevBankImagesStack = 'DevBankDemo-Images-eu-central-1'
$script:DevBankToolkitStack = 'CDKToolkit'
$script:DevBankCluster = 'devbank-demo'
$script:DevBankRepositories = @('devbank/backend', 'devbank/frontend', 'devbank/kafka')
$script:DevBankRoot = Split-Path -Parent $PSScriptRoot
$script:DevBankCdkDirectory = Join-Path $script:DevBankRoot 'infra\cdk'

function Invoke-CheckedNative {
    param([Parameter(Mandatory)][string]$Command, [Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Command failed ($LASTEXITCODE): $Command $($Arguments -join ' ')"
    }
}

function Get-ExpectedAccountId {
    param([string]$ExpectedAccountId)
    $value = if ($ExpectedAccountId) { $ExpectedAccountId } else { $env:DEVBANK_AWS_ACCOUNT_ID }
    if ($value -notmatch '^\d{12}$') {
        throw 'Provide the expected 12-digit account through -ExpectedAccountId or DEVBANK_AWS_ACCOUNT_ID.'
    }
    return $value
}

function Assert-DevBankAwsContext {
    param([string]$ExpectedAccountId, [switch]$RequireBootstrap)

    foreach ($tool in @('aws', 'npm')) {
        if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Required command is unavailable: $tool" }
    }

    $expected = Get-ExpectedAccountId $ExpectedAccountId
    $actual = (& aws sts get-caller-identity --query Account --output text --region $script:DevBankRegion).Trim()
    if ($LASTEXITCODE -ne 0 -or $actual -ne $expected) {
        throw "AWS account mismatch. Expected $expected; received $actual."
    }

    $configuredRegion = if ($env:AWS_REGION) { $env:AWS_REGION } elseif ($env:AWS_DEFAULT_REGION) { $env:AWS_DEFAULT_REGION } else { (& aws configure get region).Trim() }
    if ($configuredRegion -ne $script:DevBankRegion) {
        throw "AWS region mismatch. Configure $script:DevBankRegion explicitly; received '$configuredRegion'."
    }

    if ($RequireBootstrap) {
        $toolkitStatus = & aws cloudformation describe-stacks --stack-name $script:DevBankToolkitStack --region $script:DevBankRegion --query 'Stacks[0].StackStatus' --output text 2>$null
        if ($LASTEXITCODE -ne 0 -or $toolkitStatus -notmatch '^(CREATE|UPDATE)_COMPLETE$') {
            throw "CDKToolkit is not ready. Bootstrap it once outside these scripts for account $expected in $script:DevBankRegion."
        }
    }

    [pscustomobject]@{ Account = $actual; Region = $script:DevBankRegion }
}

function Get-StackStatus {
    param([Parameter(Mandatory)][string]$StackName)
    $status = & aws cloudformation describe-stacks --stack-name $StackName --region $script:DevBankRegion --query 'Stacks[0].StackStatus' --output text 2>$null
    if ($LASTEXITCODE -ne 0) { return 'NOT_FOUND' }
    return $status.Trim()
}

function Confirm-ExactPhrase {
    param([Parameter(Mandatory)][string]$Phrase)
    $answer = Read-Host "Type '$Phrase' to continue"
    if ($answer -cne $Phrase) { throw 'Confirmation did not match. No change was made.' }
}

function Invoke-Cdk {
    param([Parameter(ValueFromRemainingArguments)][string[]]$Arguments)
    Push-Location $script:DevBankCdkDirectory
    try { Invoke-CheckedNative npx cdk @Arguments } finally { Pop-Location }
}
