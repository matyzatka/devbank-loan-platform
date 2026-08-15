param([string]$ExpectedAccountId)
. (Join-Path $PSScriptRoot 'aws-demo-common.ps1')

$context = Assert-DevBankAwsContext -ExpectedAccountId $ExpectedAccountId
Write-Host "DevBank AWS context: account=$($context.Account), region=$($context.Region)"
foreach ($name in @($script:DevBankImagesStack, $script:DevBankApplicationStack, $script:DevBankToolkitStack)) {
    Write-Host ("{0}: {1}" -f $name, (Get-StackStatus $name))
}
$frontendUrl = & aws cloudformation describe-stacks --stack-name $script:DevBankApplicationStack --region $script:DevBankRegion --query "Stacks[0].Outputs[?OutputKey=='FrontendUrl'].OutputValue | [0]" --output text 2>$null
if ($LASTEXITCODE -eq 0 -and $frontendUrl -and $frontendUrl -ne 'None') { Write-Host "Frontend: $frontendUrl" }

$services = & aws ecs list-services --cluster $script:DevBankCluster --region $script:DevBankRegion --query 'serviceArns' --output text 2>$null
if ($LASTEXITCODE -eq 0 -and $services) { Write-Host "ECS services: $services" } else { Write-Host 'ECS services: none' }
