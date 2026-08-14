# Loan Platform Demo — Project Brief

## Purpose

A small, realistic, and interview-ready Java/React portfolio demonstrator for a banking environment. It must not use proprietary data, code, assets, or terminology from a real employer.

## Business scenario

The system processes simplified corporate loan applications. It supports application creation and retrieval, idempotent command retries, a preliminary validation and process check performed by a worker, manual approval or rejection, state-change auditing, and reliable business-event publication.

This is not real credit scoring.

## Technology baseline

- Java 21, Spring Boot, and Maven;
- PostgreSQL, Flyway, and jOOQ;
- a Kafka-compatible broker;
- JUnit 5 and Testcontainers;
- React, TypeScript, and Vite;
- Docker and Docker Compose.

New technology is added only when it solves a concrete, implemented requirement.

## Domain model

`LoanApplication` contains `id`, `customerId`, `amount`, `currency`, `status`, `version`, `createdAt`, and `updatedAt`.

Allowed workflow:

```text
SUBMITTED -> UNDER_REVIEW -> APPROVED | REJECTED
```

The processing worker owns the transition to `UNDER_REVIEW`. Invalid transitions must be rejected explicitly.

## Runtime responsibilities

### Loan API

- REST commands and queries;
- primary application state;
- HTTP idempotency;
- one transaction for state, audit, and outbox;
- outbox publication.

### Loan Processing Worker

- consumes `LoanApplicationSubmitted`;
- performs an idempotent preliminary validation and process check;
- stores the result;
- transitions the application to `UNDER_REVIEW`;
- writes audit data and a follow-up business event.

The roles may share one repository, artifact, and database, but run as separate processes.

## Reliability

The project must demonstrate transaction boundaries, optimistic locking, HTTP idempotency, a transactional outbox, at-least-once delivery, event deduplication, retry/DLT behaviour, `requestId`/`applicationId`/`eventId` traceability, health checks, graceful shutdown, and failure before publication acknowledgement. It must not claim exactly-once processing.

## Frontend

The Czech-language UI allows an operator to create, filter, browse, approve, and reject applications; observe automatic preliminary processing; and see loading, empty, validation, and API-error states.

## Language policy

Technical documentation, README files, code-level messages, logs, OpenAPI descriptions, and identifiers are English. Only user-facing UI copy is Czech.

Unimplemented capabilities must be labelled **Proposed**. Simplifications must be labelled **Demo**.

## Cloud boundary

AWS is a later, separately approved milestone. Nothing is deployed or created without explicit approval. The following local flow must be functional and tested first:

```text
API -> PostgreSQL -> outbox -> Kafka -> worker -> state transition
```

## Definition of done

- Loan API and worker run as separate processes;
- the primary flow works end to end;
- duplicate, invalid-transition, optimistic-locking, and publication-failure tests pass;
- audit history is queryable;
- frontend behaviour reflects the actual workflow;
- Docker Compose starts the complete local system;
- English technical documentation explains the implementation and its limitations;
- Czech remains limited to user-facing UI copy.
