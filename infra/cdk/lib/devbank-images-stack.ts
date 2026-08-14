import * as cdk from 'aws-cdk-lib'
import * as ecr from 'aws-cdk-lib/aws-ecr'
import { Construct } from 'constructs'

export class DevBankImagesStack extends cdk.Stack {
  readonly backendRepository: ecr.Repository
  readonly frontendRepository: ecr.Repository
  readonly kafkaRepository: ecr.Repository

  constructor(scope: Construct, id: string, props: cdk.StackProps) {
    super(scope, id, props)

    this.backendRepository = this.repository('BackendRepository', 'devbank/backend')
    this.frontendRepository = this.repository('FrontendRepository', 'devbank/frontend')
    this.kafkaRepository = this.repository('KafkaRepository', 'devbank/kafka')

    cdk.Tags.of(this).add('Project', 'DevBank')
    cdk.Tags.of(this).add('Environment', 'demo')
    cdk.Tags.of(this).add('ManagedBy', 'AWS CDK')

    new cdk.CfnOutput(this, 'BackendRepositoryUri', { value: this.backendRepository.repositoryUri })
    new cdk.CfnOutput(this, 'FrontendRepositoryUri', { value: this.frontendRepository.repositoryUri })
    new cdk.CfnOutput(this, 'KafkaRepositoryUri', { value: this.kafkaRepository.repositoryUri })
  }

  private repository(id: string, repositoryName: string): ecr.Repository {
    return new ecr.Repository(this, id, {
      repositoryName,
      imageScanOnPush: true,
      imageTagMutability: ecr.TagMutability.IMMUTABLE,
      lifecycleRules: [{ maxImageCount: 10, description: 'Retain the ten most recent images' }],
      emptyOnDelete: true,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    })
  }
}
