import * as cdk from 'aws-cdk-lib'
import * as ec2 from 'aws-cdk-lib/aws-ec2'
import * as ecr from 'aws-cdk-lib/aws-ecr'
import * as ecs from 'aws-cdk-lib/aws-ecs'
import * as elbv2 from 'aws-cdk-lib/aws-elasticloadbalancingv2'
import * as iam from 'aws-cdk-lib/aws-iam'
import * as logs from 'aws-cdk-lib/aws-logs'
import * as rds from 'aws-cdk-lib/aws-rds'
import * as servicediscovery from 'aws-cdk-lib/aws-servicediscovery'
import { Construct } from 'constructs'

const APP_PORT = 8080
const FRONTEND_PORT = 80
const KAFKA_BROKER_PORT = 19092
const KAFKA_CONTROLLER_PORT = 9093
const DATABASE_NAME = 'loan_platform'
const NAMESPACE = 'devbank.local'

export interface DevBankDemoStackProps extends cdk.StackProps {
  readonly repositories: {
    readonly backend: ecr.IRepository
    readonly frontend: ecr.IRepository
    readonly kafka: ecr.IRepository
  }
}

export class DevBankDemoStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: DevBankDemoStackProps) {
    super(scope, id, props)

    const applicationImageTag = new cdk.CfnParameter(this, 'ApplicationImageTag', {
      type: 'String',
      description: 'Immutable Git SHA tag shared by the frontend and backend images.',
      allowedPattern: '[0-9a-f]{7,40}',
    })
    const kafkaImageTag = new cdk.CfnParameter(this, 'KafkaImageTag', {
      type: 'String',
      description: 'Tag of the reviewed apache/kafka-native image mirrored into the Kafka ECR repository.',
    })
    const vpc = new ec2.Vpc(this, 'Vpc', {
      ipAddresses: ec2.IpAddresses.cidr('10.42.0.0/16'),
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        { name: 'public', subnetType: ec2.SubnetType.PUBLIC, cidrMask: 24 },
        { name: 'application', subnetType: ec2.SubnetType.PRIVATE_ISOLATED, cidrMask: 24 },
      ],
    })

    const albSecurityGroup = this.securityGroup(vpc, 'AlbSecurityGroup', 'Public HTTP entry point for the short-lived demo')
    const frontendSecurityGroup = this.securityGroup(vpc, 'FrontendSecurityGroup', 'Frontend tasks')
    const applicationSecurityGroup = this.securityGroup(vpc, 'ApplicationSecurityGroup', 'Loan API and worker tasks')
    const kafkaSecurityGroup = this.securityGroup(vpc, 'KafkaSecurityGroup', 'Single demo Kafka broker')
    const databaseSecurityGroup = this.securityGroup(vpc, 'DatabaseSecurityGroup', 'Private PostgreSQL instance')
    const endpointSecurityGroup = this.securityGroup(vpc, 'EndpointSecurityGroup', 'Private AWS service endpoints')

    albSecurityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'Public demo HTTP')
    frontendSecurityGroup.addIngressRule(albSecurityGroup, ec2.Port.tcp(FRONTEND_PORT), 'ALB to frontend')
    applicationSecurityGroup.addIngressRule(frontendSecurityGroup, ec2.Port.tcp(APP_PORT), 'Frontend to API')
    databaseSecurityGroup.addIngressRule(applicationSecurityGroup, ec2.Port.tcp(5432), 'API and worker to PostgreSQL')
    kafkaSecurityGroup.addIngressRule(applicationSecurityGroup, ec2.Port.tcp(KAFKA_BROKER_PORT), 'API and worker to Kafka')

    albSecurityGroup.addEgressRule(frontendSecurityGroup, ec2.Port.tcp(FRONTEND_PORT), 'ALB to frontend')
    frontendSecurityGroup.addEgressRule(applicationSecurityGroup, ec2.Port.tcp(APP_PORT), 'Frontend to API')
    applicationSecurityGroup.addEgressRule(databaseSecurityGroup, ec2.Port.tcp(5432), 'Application to PostgreSQL')
    applicationSecurityGroup.addEgressRule(kafkaSecurityGroup, ec2.Port.tcp(KAFKA_BROKER_PORT), 'Application to Kafka')

    vpc.addGatewayEndpoint('S3Endpoint', {
      service: ec2.GatewayVpcEndpointAwsService.S3,
      subnets: [{ subnetType: ec2.SubnetType.PRIVATE_ISOLATED }],
    })
    for (const [name, service] of [
      ['EcrApiEndpoint', ec2.InterfaceVpcEndpointAwsService.ECR],
      ['EcrDockerEndpoint', ec2.InterfaceVpcEndpointAwsService.ECR_DOCKER],
      ['LogsEndpoint', ec2.InterfaceVpcEndpointAwsService.CLOUDWATCH_LOGS],
      ['SecretsEndpoint', ec2.InterfaceVpcEndpointAwsService.SECRETS_MANAGER],
    ] as const) {
      vpc.addInterfaceEndpoint(name, {
        service,
        privateDnsEnabled: true,
        securityGroups: [endpointSecurityGroup],
        subnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      })
    }

    for (const securityGroup of [frontendSecurityGroup, applicationSecurityGroup, kafkaSecurityGroup]) {
      this.allowDns(securityGroup, vpc)
      // Isolated subnets have no default internet route; HTTPS can reach only routed VPC/gateway endpoints.
      securityGroup.addEgressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443), 'Private AWS endpoints and ECR layers in S3')
      endpointSecurityGroup.addIngressRule(securityGroup, ec2.Port.tcp(443), 'Private AWS API access')
    }

    const databaseSecret = new rds.DatabaseSecret(this, 'DatabaseSecret', {
      secretName: 'devbank/demo/database',
      username: 'loan_platform',
      excludeCharacters: ' %+~`#$&*()|[]{}:;<>?!\'"\\/@',
    })
    databaseSecret.applyRemovalPolicy(cdk.RemovalPolicy.DESTROY)
    const subnetGroup = new rds.SubnetGroup(this, 'DatabaseSubnetGroup', {
      description: 'Two-subnet placement group for the private Single-AZ demo database',
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    })
    const databaseLog = this.logGroup(
      'DatabaseLogGroup',
      '/aws/rds/instance/devbank-demo-postgres/postgresql',
    )
    const database = new rds.DatabaseInstance(this, 'Database', {
      instanceIdentifier: 'devbank-demo-postgres',
      engine: rds.DatabaseInstanceEngine.postgres({ version: rds.PostgresEngineVersion.VER_17_6 }),
      credentials: rds.Credentials.fromSecret(databaseSecret),
      databaseName: DATABASE_NAME,
      vpc,
      subnetGroup,
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      securityGroups: [databaseSecurityGroup],
      instanceType: ec2.InstanceType.of(ec2.InstanceClass.T4G, ec2.InstanceSize.MICRO),
      allocatedStorage: 20,
      storageType: rds.StorageType.GP3,
      storageEncrypted: true,
      port: 5432,
      multiAz: false,
      publiclyAccessible: false,
      backupRetention: cdk.Duration.days(1),
      deleteAutomatedBackups: true,
      deletionProtection: false,
      cloudwatchLogsExports: ['postgresql'],
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    })
    database.node.addDependency(databaseLog)

    const cluster = new ecs.Cluster(this, 'Cluster', {
      clusterName: 'devbank-demo',
      vpc,
      containerInsightsV2: ecs.ContainerInsights.DISABLED,
    })
    cluster.addDefaultCloudMapNamespace({ name: NAMESPACE })

    const frontendLog = this.logGroup('FrontendLogGroup', '/devbank/demo/frontend')
    const apiLog = this.logGroup('ApiLogGroup', '/devbank/demo/loan-api')
    const workerLog = this.logGroup('WorkerLogGroup', '/devbank/demo/processing-worker')
    const kafkaLog = this.logGroup('KafkaLogGroup', '/devbank/demo/kafka')

    const taskRole = new iam.Role(this, 'TaskRole', {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
      description: 'Runtime role with no AWS API permissions by default',
    })
    const frontendExecutionRole = this.executionRole('FrontendExecutionRole', props.repositories.frontend, frontendLog)
    const backendExecutionRole = this.executionRole('BackendExecutionRole', props.repositories.backend, apiLog, workerLog)
    databaseSecret.grantRead(backendExecutionRole)
    const kafkaExecutionRole = this.executionRole('KafkaExecutionRole', props.repositories.kafka, kafkaLog)

    const kafkaTask = this.taskDefinition('KafkaTask', 1024, 2048, taskRole, kafkaExecutionRole)
    const kafkaContainer = kafkaTask.addContainer('kafka', {
      image: ecs.ContainerImage.fromEcrRepository(props.repositories.kafka, kafkaImageTag.valueAsString),
      logging: ecs.LogDrivers.awsLogs({ logGroup: kafkaLog, streamPrefix: 'kafka' }),
      environment: {
        KAFKA_NODE_ID: '1',
        KAFKA_PROCESS_ROLES: 'broker,controller',
        KAFKA_LISTENERS: 'CONTROLLER://:9093,PLAINTEXT://:19092',
        KAFKA_ADVERTISED_LISTENERS: `PLAINTEXT://kafka.${NAMESPACE}:${KAFKA_BROKER_PORT}`,
        KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT',
        KAFKA_INTER_BROKER_LISTENER_NAME: 'PLAINTEXT',
        KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER',
        KAFKA_CONTROLLER_QUORUM_VOTERS: `1@localhost:${KAFKA_CONTROLLER_PORT}`,
        KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: '1',
        KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: '1',
        KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: '1',
        KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: '0',
        KAFKA_LOG4J_ROOT_LOGLEVEL: 'WARN',
        CLUSTER_ID: 'MkU3OEVBNTcwNTJENDM2Qk',
      },
      healthCheck: {
        command: ['CMD-SHELL', "bash -c 'exec 3<>/dev/tcp/localhost/19092'"],
        interval: cdk.Duration.seconds(15),
        timeout: cdk.Duration.seconds(5),
        retries: 5,
        startPeriod: cdk.Duration.seconds(30),
      },
    })
    kafkaContainer.addPortMappings(
      { containerPort: KAFKA_BROKER_PORT, protocol: ecs.Protocol.TCP },
      { containerPort: KAFKA_CONTROLLER_PORT, protocol: ecs.Protocol.TCP },
    )
    const kafkaService = new ecs.FargateService(this, 'KafkaService', {
      serviceName: 'devbank-demo-kafka',
      cluster,
      taskDefinition: kafkaTask,
      desiredCount: 1,
      minHealthyPercent: 0,
      maxHealthyPercent: 100,
      assignPublicIp: false,
      securityGroups: [kafkaSecurityGroup],
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      cloudMapOptions: { name: 'kafka', dnsRecordType: servicediscovery.DnsRecordType.A },
      circuitBreaker: { rollback: true },
    })

    const commonBackendEnvironment = {
      SERVER_PORT: APP_PORT.toString(),
      SPRING_PROFILES_ACTIVE: 'prod',
      SPRING_DATASOURCE_URL: `jdbc:postgresql://${database.instanceEndpoint.hostname}:5432/${DATABASE_NAME}?sslmode=require`,
      SPRING_KAFKA_BOOTSTRAP_SERVERS: `kafka.${NAMESPACE}:${KAFKA_BROKER_PORT}`,
      SPRING_KAFKA_CONSUMER_GROUP_ID: 'loan-platform-processing',
      KAFKA_TOPIC: 'loan-application-events',
      LOAN_PLATFORM_DEMO_DATA_ENABLED: 'false',
    }
    const databaseSecrets = {
      SPRING_DATASOURCE_USERNAME: ecs.Secret.fromSecretsManager(databaseSecret, 'username'),
      SPRING_DATASOURCE_PASSWORD: ecs.Secret.fromSecretsManager(databaseSecret, 'password'),
    }

    const apiTask = this.taskDefinition('ApiTask', 512, 1024, taskRole, backendExecutionRole)
    const apiContainer = apiTask.addContainer('loan-api', {
      image: ecs.ContainerImage.fromEcrRepository(props.repositories.backend, applicationImageTag.valueAsString),
      logging: ecs.LogDrivers.awsLogs({ logGroup: apiLog, streamPrefix: 'loan-api' }),
      environment: {
        ...commonBackendEnvironment,
        LOAN_PLATFORM_API_ENABLED: 'true',
        LOAN_PLATFORM_WORKER_ENABLED: 'false',
        LOAN_PLATFORM_OUTBOX_PUBLISHER_ENABLED: 'true',
      },
      secrets: databaseSecrets,
      healthCheck: {
        command: ['CMD-SHELL', `bash -c 'exec 3<>/dev/tcp/localhost/${APP_PORT} && printf "GET /actuator/health/readiness HTTP/1.0\\r\\n\\r\\n" >&3 && read -r status <&3 && [[ "$status" == *" 200 "* ]]'`],
        interval: cdk.Duration.seconds(15),
        timeout: cdk.Duration.seconds(5),
        retries: 5,
        startPeriod: cdk.Duration.seconds(60),
      },
    })
    apiContainer.addPortMappings({ containerPort: APP_PORT, protocol: ecs.Protocol.TCP })
    const apiService = new ecs.FargateService(this, 'ApiService', {
      serviceName: 'devbank-demo-loan-api',
      cluster,
      taskDefinition: apiTask,
      desiredCount: 1,
      minHealthyPercent: 0,
      maxHealthyPercent: 100,
      assignPublicIp: false,
      securityGroups: [applicationSecurityGroup],
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      cloudMapOptions: { name: 'loan-api', dnsRecordType: servicediscovery.DnsRecordType.A },
      circuitBreaker: { rollback: true },
    })
    apiService.node.addDependency(database, kafkaService)

    const workerTask = this.taskDefinition('WorkerTask', 512, 1024, taskRole, backendExecutionRole)
    workerTask.addContainer('processing-worker', {
      image: ecs.ContainerImage.fromEcrRepository(props.repositories.backend, applicationImageTag.valueAsString),
      logging: ecs.LogDrivers.awsLogs({ logGroup: workerLog, streamPrefix: 'processing-worker' }),
      environment: {
        ...commonBackendEnvironment,
        SPRING_MAIN_WEB_APPLICATION_TYPE: 'none',
        LOAN_PLATFORM_API_ENABLED: 'false',
        LOAN_PLATFORM_WORKER_ENABLED: 'true',
        LOAN_PLATFORM_OUTBOX_PUBLISHER_ENABLED: 'false',
      },
      secrets: databaseSecrets,
    })
    const workerService = new ecs.FargateService(this, 'WorkerService', {
      serviceName: 'devbank-demo-processing-worker',
      cluster,
      taskDefinition: workerTask,
      desiredCount: 1,
      minHealthyPercent: 0,
      maxHealthyPercent: 100,
      assignPublicIp: false,
      securityGroups: [applicationSecurityGroup],
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      circuitBreaker: { rollback: true },
    })
    workerService.node.addDependency(database, kafkaService)

    const frontendTask = this.taskDefinition('FrontendTask', 256, 512, taskRole, frontendExecutionRole)
    const frontendContainer = frontendTask.addContainer('frontend', {
      image: ecs.ContainerImage.fromEcrRepository(props.repositories.frontend, applicationImageTag.valueAsString),
      logging: ecs.LogDrivers.awsLogs({ logGroup: frontendLog, streamPrefix: 'frontend' }),
      environment: {
        BACKEND_URL: `http://loan-api.${NAMESPACE}:${APP_PORT}`,
      },
      healthCheck: {
        command: ['CMD-SHELL', 'wget -qO- http://localhost/ >/dev/null || exit 1'],
        interval: cdk.Duration.seconds(15),
        timeout: cdk.Duration.seconds(5),
        retries: 5,
      },
    })
    frontendContainer.addPortMappings({ containerPort: FRONTEND_PORT, protocol: ecs.Protocol.TCP })
    const frontendService = new ecs.FargateService(this, 'FrontendService', {
      serviceName: 'devbank-demo-frontend',
      cluster,
      taskDefinition: frontendTask,
      desiredCount: 1,
      minHealthyPercent: 0,
      maxHealthyPercent: 100,
      assignPublicIp: false,
      securityGroups: [frontendSecurityGroup],
      vpcSubnets: { subnetType: ec2.SubnetType.PRIVATE_ISOLATED },
      circuitBreaker: { rollback: true },
    })
    frontendService.node.addDependency(apiService)

    const loadBalancer = new elbv2.ApplicationLoadBalancer(this, 'LoadBalancer', {
      loadBalancerName: 'devbank-demo',
      vpc,
      internetFacing: true,
      securityGroup: albSecurityGroup,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
    })
    const listener = loadBalancer.addListener('HttpListener', {
      port: 80,
      protocol: elbv2.ApplicationProtocol.HTTP,
      open: false,
    })
    listener.addTargets('FrontendTarget', {
      port: FRONTEND_PORT,
      protocol: elbv2.ApplicationProtocol.HTTP,
      targets: [frontendService.loadBalancerTarget({ containerName: 'frontend', containerPort: FRONTEND_PORT })],
      healthCheck: { path: '/', healthyHttpCodes: '200' },
      deregistrationDelay: cdk.Duration.seconds(30),
    })

    cdk.Tags.of(this).add('Project', 'DevBank')
    cdk.Tags.of(this).add('Environment', 'demo')
    cdk.Tags.of(this).add('ManagedBy', 'AWS CDK')

    new cdk.CfnOutput(this, 'LoadBalancerDnsName', { value: loadBalancer.loadBalancerDnsName })
    new cdk.CfnOutput(this, 'DatabaseSecretArn', { value: databaseSecret.secretArn })
  }

  private securityGroup(vpc: ec2.IVpc, id: string, description: string): ec2.SecurityGroup {
    return new ec2.SecurityGroup(this, id, {
      vpc,
      description,
      allowAllOutbound: false,
    })
  }

  private allowDns(securityGroup: ec2.SecurityGroup, vpc: ec2.IVpc): void {
    securityGroup.addEgressRule(ec2.Peer.ipv4(vpc.vpcCidrBlock), ec2.Port.udp(53), 'VPC DNS')
    securityGroup.addEgressRule(ec2.Peer.ipv4(vpc.vpcCidrBlock), ec2.Port.tcp(53), 'VPC DNS fallback')
  }

  private logGroup(id: string, logGroupName: string): logs.LogGroup {
    return new logs.LogGroup(this, id, {
      logGroupName,
      retention: logs.RetentionDays.ONE_WEEK,
      removalPolicy: cdk.RemovalPolicy.DESTROY,
    })
  }

  private executionRole(
    id: string,
    repository: ecr.IRepository,
    ...logGroups: logs.ILogGroup[]
  ): iam.Role {
    const role = new iam.Role(this, id, {
      assumedBy: new iam.ServicePrincipal('ecs-tasks.amazonaws.com'),
    })
    repository.grantPull(role)
    for (const logGroup of logGroups) {
      logGroup.grantWrite(role)
    }
    return role
  }

  private taskDefinition(
    id: string,
    cpu: number,
    memoryLimitMiB: number,
    taskRole: iam.IRole,
    executionRole: iam.IRole,
  ): ecs.FargateTaskDefinition {
    return new ecs.FargateTaskDefinition(this, id, {
      cpu,
      memoryLimitMiB,
      taskRole,
      executionRole,
      runtimePlatform: {
        operatingSystemFamily: ecs.OperatingSystemFamily.LINUX,
        cpuArchitecture: ecs.CpuArchitecture.X86_64,
      },
    })
  }
}
