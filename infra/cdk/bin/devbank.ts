#!/usr/bin/env node
import * as cdk from 'aws-cdk-lib'
import { DevBankDemoStack } from '../lib/devbank-demo-stack'
import { DevBankImagesStack } from '../lib/devbank-images-stack'

const app = new cdk.App()

const environment = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: 'eu-central-1',
}

const images = new DevBankImagesStack(app, 'DevBankDemoImages', {
  stackName: 'DevBankDemo-Images-eu-central-1',
  env: environment,
  description: 'DevBank immutable container repositories',
})

const application = new DevBankDemoStack(app, 'DevBankDemoApplication', {
  stackName: 'DevBankDemo-eu-central-1',
  repositories: {
    backend: images.backendRepository,
    frontend: images.frontendRepository,
    kafka: images.kafkaRepository,
  },
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: 'eu-central-1',
  },
  description: 'DevBank cost-controlled demonstration environment',
})

application.addStackDependency(images)
