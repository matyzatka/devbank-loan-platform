param([string]$ExpectedAccountId)
. (Join-Path $PSScriptRoot 'aws-demo-common.ps1')

$context = Assert-DevBankAwsContext -ExpectedAccountId $ExpectedAccountId -RequireBootstrap
foreach ($tool in @('docker', 'git')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Required command is unavailable: $tool" }
}
$sha = (& git -C $script:DevBankRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sha -notmatch '^[0-9a-f]{40}$') { throw 'Unable to resolve the immutable Git SHA.' }
if (& git -C $script:DevBankRoot status --porcelain) { throw 'The working tree must be clean before deployment.' }

Write-Host "Target: account=$($context.Account), region=$($context.Region)"
Write-Host "Stacks: $script:DevBankImagesStack -> $script:DevBankApplicationStack"
Write-Host "Application image tag: $sha"

Push-Location $script:DevBankCdkDirectory
try {
    Invoke-CheckedNative npm ci
    Invoke-CheckedNative npm run build
    Invoke-CheckedNative npm test
    Invoke-CheckedNative npm run synth -- --quiet
} finally { Pop-Location }

Invoke-Cdk diff $script:DevBankImagesStackId --exclusively --no-change-set
Invoke-Cdk diff $script:DevBankApplicationStackId --exclusively --no-change-set
Confirm-ExactPhrase 'DEPLOY DEVBANK DEMO'

Invoke-Cdk deploy $script:DevBankImagesStackId --exclusively --require-approval never

$registry = "$($context.Account).dkr.ecr.$($script:DevBankRegion).amazonaws.com"
$password = & aws ecr get-login-password --region $script:DevBankRegion
if ($LASTEXITCODE -ne 0) { throw 'Unable to obtain the short-lived ECR login token.' }
$password | docker login --username AWS --password-stdin $registry
$password = $null
if ($LASTEXITCODE -ne 0) { throw 'Docker could not authenticate to ECR.' }

function Test-EcrTag([string]$Repository, [string]$Tag) {
    # A missing image is the expected cache-miss path. Windows PowerShell turns
    # the AWS CLI stderr for that case into a terminating NativeCommandError
    # when the script-wide ErrorActionPreference is Stop, so scope the relaxed
    # preference to this probe and restore it immediately afterwards.
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        & aws ecr describe-images --repository-name $Repository --image-ids "imageTag=$Tag" --region $script:DevBankRegion *> $null
        $imageExists = $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return $imageExists
}
function Publish-ApplicationImage([string]$Repository, [string]$Context) {
    if (Test-EcrTag $Repository $sha) { Write-Host "$Repository`:$sha already exists; skipping immutable image push."; return }
    $image = "$registry/$Repository`:$sha"
    Invoke-CheckedNative docker build --tag $image $Context
    Invoke-CheckedNative docker push $image
}

Publish-ApplicationImage 'devbank/backend' (Join-Path $script:DevBankRoot 'backend')
Publish-ApplicationImage 'devbank/frontend' (Join-Path $script:DevBankRoot 'frontend')
if (-not (Test-EcrTag 'devbank/kafka' '3.8.0')) {
    Invoke-CheckedNative docker pull 'apache/kafka-native:3.8.0'
    Invoke-CheckedNative docker tag 'apache/kafka-native:3.8.0' "$registry/devbank/kafka:3.8.0"
    Invoke-CheckedNative docker push "$registry/devbank/kafka:3.8.0"
}

Invoke-Cdk deploy $script:DevBankApplicationStackId --exclusively --require-approval never --parameters "$($script:DevBankApplicationStackId):ApplicationImageTag=$sha" --parameters "$($script:DevBankApplicationStackId):KafkaImageTag=3.8.0"
& (Join-Path $PSScriptRoot 'aws-demo-status.ps1') -ExpectedAccountId $context.Account
