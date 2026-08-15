param([string]$ExpectedAccountId)
. (Join-Path $PSScriptRoot 'aws-demo-common.ps1')

$context = Assert-DevBankAwsContext -ExpectedAccountId $ExpectedAccountId
$findings = [System.Collections.Generic.List[string]]::new()
foreach ($stack in @($script:DevBankApplicationStack, $script:DevBankImagesStack)) {
    $status = Get-StackStatus $stack
    if ($status -ne 'NOT_FOUND') { $findings.Add("CloudFormation stack $stack ($status)") }
}

$clusters = (& aws ecs list-clusters --region $script:DevBankRegion --query "clusterArns[?ends_with(@, '/$($script:DevBankCluster)')]" --output text 2>$null).Trim()
if ($clusters) {
    $services = (& aws ecs list-services --cluster $script:DevBankCluster --region $script:DevBankRegion --query 'serviceArns' --output text 2>$null).Trim()
    $tasks = (& aws ecs list-tasks --cluster $script:DevBankCluster --region $script:DevBankRegion --query 'taskArns' --output text 2>$null).Trim()
    $findings.Add("ECS cluster $script:DevBankCluster; services='$services'; tasks='$tasks'")
}

$checks = @(
    @{ Label='RDS'; Command=@('rds','describe-db-instances','--region',$script:DevBankRegion,'--query',"DBInstances[?DBInstanceIdentifier=='devbank-demo-postgres'].DBInstanceIdentifier",'--output','text') },
    @{ Label='ALB'; Command=@('elbv2','describe-load-balancers','--region',$script:DevBankRegion,'--query',"LoadBalancers[?LoadBalancerName=='devbank-demo'].LoadBalancerName",'--output','text') },
    @{ Label='CloudFront'; Command=@('cloudfront','list-distributions','--query',"DistributionList.Items[?Comment=='DevBank demo HTTPS edge'].Id",'--output','text') },
    @{ Label='Secrets Manager'; Command=@('secretsmanager','list-secrets','--include-planned-deletion','--region',$script:DevBankRegion,'--query',"SecretList[?Name=='devbank/demo/database'].Name",'--output','text') },
    @{ Label='CloudWatch log groups'; Command=@('logs','describe-log-groups','--region',$script:DevBankRegion,'--query',"logGroups[?starts_with(logGroupName, '/devbank/demo/') || starts_with(logGroupName, '/aws/rds/instance/devbank-demo-')].logGroupName",'--output','text') },
    @{ Label='demo VPC'; Command=@('ec2','describe-vpcs','--region',$script:DevBankRegion,'--filters','Name=tag:Project,Values=DevBank','Name=tag:Environment,Values=demo','--query','Vpcs[].VpcId','--output','text') },
    @{ Label='VPC endpoints'; Command=@('ec2','describe-vpc-endpoints','--region',$script:DevBankRegion,'--filters','Name=tag:Project,Values=DevBank','Name=tag:Environment,Values=demo','--query','VpcEndpoints[].VpcEndpointId','--output','text') }
)
foreach ($check in $checks) {
    [string[]]$arguments = $check.Command
    $value = (& aws @arguments 2>$null).Trim()
    if ($LASTEXITCODE -eq 0 -and $value) { $findings.Add("$($check.Label): $value") }
}
foreach ($repository in $script:DevBankRepositories) {
    & aws ecr describe-repositories --repository-names $repository --region $script:DevBankRegion *> $null
    if ($LASTEXITCODE -eq 0) { $findings.Add("ECR repository: $repository") }
}

Write-Host "$script:DevBankToolkitStack: $(Get-StackStatus $script:DevBankToolkitStack) (intentionally untouched)"
if ($findings.Count -gt 0) {
    Write-Warning 'DevBank demo resources remain:'
    $findings | ForEach-Object { Write-Host " - $_" }
    exit 1
}
Write-Host 'Audit passed: no DevBank demo resources remain.'
