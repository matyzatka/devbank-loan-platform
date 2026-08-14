param([string]$ExpectedAccountId)
. (Join-Path $PSScriptRoot 'aws-demo-common.ps1')

$context = Assert-DevBankAwsContext -ExpectedAccountId $ExpectedAccountId
Write-Host "Target: account=$($context.Account), region=$($context.Region)"
Write-Host "Only these stacks may be deleted: $script:DevBankApplicationStack, $script:DevBankImagesStack"
foreach ($stack in @($script:DevBankApplicationStack, $script:DevBankImagesStack)) {
    Write-Host "`nResources in ${stack}:"
    & aws cloudformation list-stack-resources --stack-name $stack --region $script:DevBankRegion --query 'StackResourceSummaries[].{Type:ResourceType,Id:PhysicalResourceId}' --output table 2>$null
    if ($LASTEXITCODE -ne 0) { Write-Host '  stack not found' }
}
Write-Host "`n$script:DevBankToolkitStack is support infrastructure and will remain untouched."
Confirm-ExactPhrase 'DESTROY DEVBANK DEMO'

if ((Get-StackStatus $script:DevBankApplicationStack) -ne 'NOT_FOUND') {
    Invoke-Cdk destroy $script:DevBankApplicationStackId --exclusively --force
}
if ((Get-StackStatus $script:DevBankImagesStack) -ne 'NOT_FOUND') {
    Invoke-Cdk destroy $script:DevBankImagesStackId --exclusively --force
}
& (Join-Path $PSScriptRoot 'aws-demo-audit.ps1') -ExpectedAccountId $context.Account
