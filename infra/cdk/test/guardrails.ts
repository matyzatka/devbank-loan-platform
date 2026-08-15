import { strict as assert } from 'node:assert'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import * as cdk from 'aws-cdk-lib'
import { Template } from 'aws-cdk-lib/assertions'
import { DevBankDemoStack } from '../lib/devbank-demo-stack'
import { DevBankImagesStack } from '../lib/devbank-images-stack'

const app = new cdk.App()
const images = new DevBankImagesStack(app, 'GuardrailImages', {
  stackName: 'DevBankDemo-Images-eu-central-1',
  env: { region: 'eu-central-1' },
})
const stack = new DevBankDemoStack(app, 'GuardrailTest', {
  stackName: 'DevBankDemo-eu-central-1',
  env: { region: 'eu-central-1' },
  repositories: {
    backend: images.backendRepository,
    frontend: images.frontendRepository,
    kafka: images.kafkaRepository,
  },
})
const template = Template.fromStack(stack)
const imagesTemplate = Template.fromStack(images)

imagesTemplate.resourceCountIs('AWS::ECR::Repository', 3)
imagesTemplate.allResourcesProperties('AWS::ECR::Repository', {
  EmptyOnDelete: true,
  ImageScanningConfiguration: { ScanOnPush: true },
  ImageTagMutability: 'IMMUTABLE',
})

for (const forbiddenType of [
  'AWS::EC2::NatGateway',
  'AWS::EFS::FileSystem',
  'AWS::MSK::Cluster',
  'AWS::ApplicationAutoScaling::ScalableTarget',
  'AWS::ApplicationAutoScaling::ScalingPolicy',
]) {
  template.resourceCountIs(forbiddenType, 0)
}

template.resourceCountIs('AWS::RDS::DBInstance', 1)
template.hasResourceProperties('AWS::RDS::DBInstance', {
  Engine: 'postgres',
  Port: '5432',
  DeletionProtection: false,
  DeleteAutomatedBackups: true,
  MultiAZ: false,
  PubliclyAccessible: false,
})
template.resourceCountIs('AWS::RDS::DBSubnetGroup', 1)
template.resourceCountIs('AWS::ECS::Service', 4)
template.resourceCountIs('AWS::ECS::TaskDefinition', 4)
template.resourceCountIs('AWS::EC2::VPCEndpoint', 5)
template.resourceCountIs('AWS::Logs::LogGroup', 5)
template.allResourcesProperties('AWS::Logs::LogGroup', {
  RetentionInDays: 7,
})
template.hasResourceProperties('AWS::ElasticLoadBalancingV2::Listener', {
  Port: 80,
  Protocol: 'HTTP',
})
template.resourceCountIs('AWS::CloudFront::Distribution', 1)
template.hasResourceProperties('AWS::CloudFront::Distribution', {
  DistributionConfig: {
    DefaultCacheBehavior: {
      ViewerProtocolPolicy: 'redirect-to-https',
      AllowedMethods: ['GET', 'HEAD', 'OPTIONS', 'PUT', 'PATCH', 'POST', 'DELETE'],
      CachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad',
    },
    HttpVersion: 'http2and3',
    PriceClass: 'PriceClass_100',
  },
})

const resources = template.toJSON().Resources as Record<string, {
  Type: string
  Properties?: Record<string, unknown>
  DeletionPolicy?: string
  UpdateReplacePolicy?: string
}>
const databaseIngress = Object.entries(resources).filter(([, resource]) =>
  resource.Type === 'AWS::EC2::SecurityGroupIngress'
  && resource.Properties?.FromPort === 5432
  && resource.Properties?.ToPort === 5432)
const databaseSubnetGroups = Object.values(resources).filter(resource =>
  resource.Type === 'AWS::RDS::DBSubnetGroup')

assert.equal(databaseIngress.length, 1, 'PostgreSQL must have exactly one ingress rule')
assert.equal(
  (databaseSubnetGroups[0]?.Properties?.SubnetIds as unknown[] | undefined)?.length,
  2,
  'The RDS subnet group must span exactly two subnets',
)
assert.match(
  JSON.stringify(databaseIngress[0]?.[1].Properties?.SourceSecurityGroupId),
  /ApplicationSecurityGroup/,
  'PostgreSQL ingress must originate from the application task security group',
)

const deployScript = readFileSync(resolve(__dirname, '../../../scripts/aws-demo-deploy.ps1'), 'utf8')
assert.match(
  deployScript,
  /\$script:DevBankApplicationStack\):ApplicationImageTag=/,
  'CDK parameters must be qualified by the physical CloudFormation stack name',
)
assert.doesNotMatch(
  deployScript,
  /\$script:DevBankApplicationStackId\):(?:ApplicationImageTag|KafkaImageTag)=/,
  'CDK construct IDs must not qualify CloudFormation parameters',
)

for (const [logicalId, resource] of Object.entries({
  ...(imagesTemplate.toJSON().Resources as Record<string, typeof resources[string]>),
  ...resources,
})) {
  if (['AWS::ECR::Repository', 'AWS::Logs::LogGroup', 'AWS::RDS::DBInstance', 'AWS::SecretsManager::Secret'].includes(resource.Type)) {
    assert.equal(resource.DeletionPolicy, 'Delete', `${logicalId} must be deleted with the demo stack`)
    assert.equal(resource.UpdateReplacePolicy, 'Delete', `${logicalId} must not retain replaced demo data`)
  }
}

console.log('CDK guardrails verified.')
