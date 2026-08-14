param([string]$ExpectedAccountId)
. (Join-Path $PSScriptRoot 'aws-demo-common.ps1')

$context = Assert-DevBankAwsContext -ExpectedAccountId $ExpectedAccountId
Write-Host "DevBank AWS context: account=$($context.Account), region=$($context.Region)"
foreach ($name in @($script:DevBankImagesStack, $script:DevBankApplicationStack, $script:DevBankToolkitStack)) {
    Write-Host ("{0}: {1}" -f $name, (Get-StackStatus $name))
}

$services = & aws ecs list-services --cluster $script:DevBankCluster --region $script:DevBankRegion --query 'serviceArns' --output text 2>$null
if ($LASTEXITCODE -eq 0 -and $services) { Write-Host "ECS services: $services" } else { Write-Host 'ECS services: none' }
