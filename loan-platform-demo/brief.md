# Loan Platform Demo

## 1. Purpose

This repository is a small, realistic portfolio project for a Java/React contractor role in a banking environment.

The project demonstrates:

* Java 21 backend development
* Spring Boot
* REST API design
* PostgreSQL persistence
* business state transitions
* idempotency
* transactional outbox
* Kafka-compatible event processing
* automated testing
* Docker
* React and TypeScript
* AWS deployment concepts
* ECS/Fargate, ECR, CloudWatch, S3, Lambda and CDK

This is a demonstrator, not a production banking platform. It must be understandable, honest and explainable in an interview.

Do not use proprietary data, code or terminology from any real employer.

## 2. Business scenario

The system processes simplified corporate loan applications.

A customer submits an application. The application is persisted, moves through a controlled workflow and produces business events for downstream processing.

The system must support:

* creating a loan application,
* retrieving an application,
* reviewing an application,
* approving an application,
* rejecting an application,
* safely handling duplicate requests,
* publishing an event after a successful database transaction,
* exposing useful operational information.

## 3. Target architecture

Use one repository with separated logical and runtime boundaries.

```text
loan-platform-demo/
├── backend/
├── frontend/
├── infra/
├── docker-compose.yml
├── PROJECT_BRIEF.md
├── README.md
└── docs/
```

The first implementation is a modular monolith.

Do not create multiple microservices unless explicitly approved.

Target runtime architecture:

```text
Browser
  ↓
S3 / CloudFront static frontend
  ↓ HTTP API
ECS / Fargate Spring Boot backend
  ↓
PostgreSQL
  ↓
Transactional outbox
  ↓
Kafka-compatible event broker
  ↓
Consumers and downstream processing
  ↓
CloudWatch logs and metrics
```

For the inexpensive demo:

* PostgreSQL runs locally through Docker Compose.
* Kafka runs locally through Docker Compose.
* The backend may be temporarily deployed to ECS/Fargate.
* The frontend may be temporarily uploaded to S3.
* AWS services must be used only when they provide meaningful learning value.

## 4. Technology choices

Backend:

* Java 21
* Spring Boot
* Maven
* PostgreSQL
* Flyway
* jOOQ or another explicit SQL-oriented persistence approach
* Kafka-compatible broker
* JUnit 5
* Testcontainers where useful
* Spring Boot Actuator
* OpenAPI documentation

Frontend:

* React
* TypeScript
* Vite
* simple CSS or Tailwind
* no Redux unless justified
* no unnecessary UI framework

Infrastructure:

* Docker
* Docker Compose
* AWS CDK in TypeScript
* ECR
* ECS/Fargate
* IAM
* CloudWatch
* S3
* Lambda
* optionally API Gateway

## 5. Domain model

### LoanApplication

Fields:

* `id`
* `customerId`
* `amount`
* `currency`
* `status`
* `version`
* `createdAt`
* `updatedAt`

Statuses:

```text
SUBMITTED
UNDER_REVIEW
APPROVED
REJECTED
```

Allowed transitions:

```text
SUBMITTED -> UNDER_REVIEW
UNDER_REVIEW -> APPROVED
UNDER_REVIEW -> REJECTED
```

Invalid transitions must be rejected explicitly.

The domain must not permit:

```text
APPROVED -> REJECTED
REJECTED -> APPROVED
APPROVED -> APPROVED
REJECTED -> REJECTED
```

## 6. API

Initial API:

```text
POST /api/v1/applications
GET /api/v1/applications/{id}
POST /api/v1/applications/{id}/review
POST /api/v1/applications/{id}/approve
POST /api/v1/applications/{id}/reject
```

Creation requires:

```text
Idempotency-Key
```

Expected behavior:

* first request creates one application and returns `201`,
* repeated request with the same key returns the original result,
* duplicate request never creates another application,
* invalid input returns a clear validation response,
* invalid transition returns a clear business error,
* missing application returns `404`.

## 7. Reliability requirements

The project must demonstrate practical distributed-system thinking.

Implement or document:

* database transaction boundaries,
* optimistic locking,
* idempotent commands,
* transactional outbox,
* at-least-once delivery,
* duplicate event handling,
* retry and error handling,
* correlation/request IDs,
* health checks,
* graceful shutdown,
* basic observability.

Do not claim exactly-once processing unless the implementation truly provides it.

## 8. Event flow

After a successful application creation:

```text
HTTP request
  ↓
database transaction
  ├── loan application
  ├── idempotency record
  └── outbox event
        ↓
outbox publisher
        ↓
Kafka event
        ↓
consumer
        ↓
processed-event deduplication
```

Example event:

```text
LoanApplicationSubmitted
```

The event should contain:

* event ID,
* application ID,
* customer ID,
* event type,
* event version,
* timestamp,
* payload.

## 9. Frontend

The React frontend should be intentionally small but functional.

It should allow the user to:

* create a loan application,
* view a list of applications,
* open an application detail,
* review an application,
* approve or reject it,
* see loading states,
* see empty states,
* see validation and API errors,
* see the current status clearly.

The frontend is a demonstration of end-to-end delivery, not a visual design project.

## 10. AWS scope

The AWS learning target is:

```text
Docker image
  -> ECR
  -> ECS/Fargate task
  -> CloudWatch logs
```

Optional short demonstrations:

* Lambda invocation,
* private S3 bucket,
* IAM role,
* CDK synthesis,
* CloudWatch alarm.

Do not create or keep running expensive infrastructure without explicit approval.

Do not use for the first demo:

* MSK/Kafka cluster,
* NAT Gateway,
* permanent Application Load Balancer,
* permanent RDS instance,
* ElastiCache cluster,
* multi-service ECS architecture,
* unnecessary public IPv4 resources.

Before any AWS deployment:

* verify the active AWS account and region,
* enable MFA,
* create a billing budget,
* confirm expected costs,
* use least-privilege IAM,
* never commit credentials,
* never deploy without explicit approval.

After the demo:

* stop running tasks,
* remove temporary services,
* remove load balancers,
* remove databases,
* remove unused networking resources,
* inspect CloudFormation and billing resources.

## 11. What Codex is responsible for

Codex should:

* inspect the repository before editing,
* create and modify project files,
* implement code in small milestones,
* generate tests,
* run builds and tests,
* explain design decisions,
* review diffs,
* update documentation,
* create Docker and CDK configuration,
* identify risks and incomplete parts.

Codex must not:

* delete unrelated files,
* add large dependencies without approval,
* deploy AWS resources silently,
* invent production experience,
* hide failing tests,
* create unnecessary abstractions,
* implement the entire system in one uncontrolled change.

## 12. What Matouš is responsible for

Matouš should:

* approve the architecture and scope,
* read the important diffs,
* run and observe the application,
* verify the UI and API,
* understand every major design decision,
* ask failure-scenario questions,
* control AWS deployment and costs,
* create Git commits after verified milestones,
* prepare to explain the project without pretending Codex did not help.

The goal is not to hand-write every line. The goal is to understand, supervise and be able to defend the resulting system.

## 13. Working method

Work in milestones.

After each milestone:

1. run tests,
2. run the application,
3. inspect the diff,
4. explain important decisions,
5. update documentation,
6. create a Git commit,
7. stop before starting the next major area.

Suggested milestones:

1. project skeleton,
2. domain model,
3. PostgreSQL persistence,
4. REST API,
5. state machine,
6. idempotency,
7. transactional outbox,
8. Kafka consumer,
9. Docker,
10. React frontend,
11. CDK infrastructure,
12. short AWS deployment,
13. final README and demo script.

## 14. Definition of done

The project is complete when:

* the backend runs locally,
* the frontend runs locally,
* the main user flow works end to end,
* tests pass,
* duplicate requests are safe,
* invalid state transitions are rejected,
* the outbox flow is understandable,
* Docker Compose works,
* the container can be pushed to ECR,
* a short Fargate deployment has been tested or clearly documented,
* CloudWatch logs can be shown,
* AWS costs are controlled,
* README explains the architecture and trade-offs,
* Matouš can demonstrate the project in five minutes.

## 15. Interview demo

The final demonstration should show:

1. submit a loan application,
2. retrieve it in the UI,
3. move it through the workflow,
4. repeat the same request and show idempotency,
5. show the database state,
6. show the outbox/event,
7. show Docker or ECR,
8. show a Fargate task,
9. show CloudWatch logs,
10. explain one failure scenario and one trade-off.

The project should communicate:

> I can take a business requirement, design the flow, implement the backend, handle consistency and failure, connect a frontend, containerize the application and understand its cloud deployment boundary.
