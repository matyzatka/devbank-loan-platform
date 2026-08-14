# Loan Platform Demo

A small but realistic Java/React portfolio project for a banking environment. It demonstrates transaction consistency, HTTP idempotency, a transactional outbox, at-least-once event delivery, idempotent processing, optimistic locking, and auditable state transitions.

This is a demonstrator, not a production banking system or a real credit-scoring engine. The worker performs only a **preliminary validation and process check**.

## Architecture

One Java artifact runs in two independently configured processes:

```text
Browser -> React / Nginx -> Loan API
                              |
                              +-> PostgreSQL: application + idempotency + audit + outbox
                              +-> outbox publisher -> Kafka: LoanApplicationSubmitted

Kafka -> Loan Processing Worker
           +-> event deduplication
           +-> preliminary validation and process check
           +-> result + audit + UNDER_REVIEW
           +-> LoanApplicationStatusChanged in the outbox
```

- `loan-api`: owns the REST API and primary application state, and publishes outbox events. It does not consume its own events.
- `processing-worker`: has no HTTP server and consumes only `LoanApplicationSubmitted` events.
- `frontend`: React SPA served by Nginx.
- `postgres`: shared local database.
- `kafka`: local single-node broker.

The runtime workflow is:

```text
SUBMITTED -> preliminary validation and process check -> UNDER_REVIEW
UNDER_REVIEW -> APPROVED | REJECTED
```

The transition to `UNDER_REVIEW` is owned by the worker and is not exposed as a public REST command. The API permits a human operator to make the final decision only after preliminary processing completes.

See [Architecture and event flow](docs/ARCHITECTURE.md) for transaction boundaries and failure behaviour.

## Run locally

Prerequisite: Docker Desktop.

```powershell
cd "C:\Users\matou\Documents\ChatGPT\New project\loan-platform-demo"
docker compose up -d --build --remove-orphans
docker compose ps
```

- Czech operations UI: `http://localhost:3000`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Loan API readiness: `http://localhost:8080/actuator/health/readiness`

Follow signal-oriented application logs:

```powershell
docker compose logs -f loan-api processing-worker
```

Stop the stack with `docker compose down`.

## Local development

Java 21 and Maven 3.9+ are required for a local backend build:

```powershell
cd backend
mvn clean verify
```

Node.js 22 is required for standalone frontend development:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

The Vite development server proxies `/api` and `/actuator` to the Loan API on port `8080`.

## Consistency and delivery semantics

Application creation stores `loan_application`, `idempotency_record`, the initial `loan_application_status_history`, and a `LoanApplicationSubmitted` outbox event in one PostgreSQL transaction.

The publisher marks an event as published only after Kafka acknowledges it. If publication fails, the application remains committed and the event remains pending for a later retry. Delivery is **at least once**, not exactly once. The worker claims each `event_id` in `processed_event` in the same transaction as its processing result, state transition, audit entry, and follow-up outbox event, so duplicate deliveries do not repeat business side effects.

Optimistic locking protects the aggregate through its `version` column. Approval and rejection commands carry the version observed by the UI, and SQL updates use the same value as a compare-and-set predicate. The domain model separately rejects invalid state transitions.

## Audit and traceability

`loan_application_status_history` records the previous and new state, application version, timestamp, change source (`API` or `WORKER`), `requestId`, and related `eventId`.

Logs carry `requestId`, `applicationId`, and `eventId` in MDC. The HTTP header remains `X-Correlation-ID` for compatibility; internally its value is treated as `requestId` and propagated in Kafka headers.

The application detail screen exposes the persisted preliminary-processing result and complete status history through the read-only `/api/v1/applications/{id}/processing` endpoint. This makes the same identifiers visible in the UI, API, database, and logs.

## Verified scenarios

- duplicate HTTP requests with the same `Idempotency-Key`;
- conflicting payloads under the same idempotency key;
- duplicate Kafka delivery without repeated side effects;
- invalid state transitions;
- optimistic-locking conflicts;
- rollback of invalid creation;
- publisher failure before acknowledgement, pending-event retention, and successful retry;
- consumer retry and dead-letter routing;
- HTTP-to-outbox-to-Kafka-to-worker correlation.

## Demo limitations

- The preliminary check verifies only consistency between the event and persisted application; it is not scoring.
- API and worker share one database.
- Kafka runs as one local node and application code creates topics.
- Retry/DLT handling has no operator reprocessing UI.
- Authentication and authorization are not implemented.
- The UI displays a static demonstration user.
- Metrics are not connected to a production monitoring system.

## Proposed only

AWS/ECS/Fargate deployment, CloudWatch dashboards and alarms, production secrets/IAM, separate database ownership, and real banking validation or scoring rules are proposals only. No AWS resource is created without separate approval.
