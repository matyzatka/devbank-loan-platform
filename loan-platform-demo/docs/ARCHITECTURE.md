# Architecture and Event Flow

## Runtime responsibilities

### Loan API

- accepts REST commands and queries;
- owns the primary `LoanApplication` state;
- enforces HTTP idempotency;
- stores state changes, audit records, and outbox events atomically;
- publishes pending outbox events;
- does not consume its own Kafka events.

### Loan Processing Worker

- runs without an HTTP server;
- consumes only `LoanApplicationSubmitted`;
- claims `eventId` for idempotent processing;
- compares event data with the persisted application;
- records the preliminary validation and process-check result;
- moves the application from `SUBMITTED` to `UNDER_REVIEW`;
- creates the audit entry and follow-up outbox event in the same database transaction.

## Successful flow

1. The client sends `POST /api/v1/applications` with an `Idempotency-Key`.
2. Loan API atomically stores the application, idempotency record, `SUBMITTED` audit entry, and outbox event.
3. The publisher sends `LoanApplicationSubmitted` to Kafka.
4. The worker atomically claims its `eventId` in `processed_event`.
5. The worker verifies `applicationId`, `customerId`, `amount`, and `currency` against the database.
6. It stores `PASSED` in `loan_preprocessing_result`.
7. It changes the state to `UNDER_REVIEW`, increments `version`, and writes the audit entry.
8. The same transaction creates `LoanApplicationStatusChanged` in the outbox.
9. Loan API publishes that follow-up event. The worker ignores it because it handles submitted events only.
10. A user can approve or reject the application through Loan API, supplying the aggregate version displayed by the UI.

The detail screen retrieves operational evidence from `GET /api/v1/applications/{id}/processing`. The response combines the latest preliminary-processing result with the append-only state history. It is a read model only and cannot advance workflow state.

## Operator command concurrency

Approval and rejection commands carry `expectedVersion`. Loan API compares it with the persisted aggregate before applying the transition, and the repository also includes that version in its SQL update predicate. A stale UI therefore receives `409 Conflict` instead of deciding state that changed after the operator loaded it. The two checks serve different purposes: the service provides an explicit command contract, while the database predicate closes the race between read and write.

## Duplicate HTTP request

`idempotency_record` binds the key to a canonical payload hash and application ID. Repeating the same key and payload returns the original application. Reusing the key with a different payload returns a conflict. Neither case creates a second submitted event.

## Duplicate Kafka event

The `processed_event` claim, processing result, state mutation, audit entry, and follow-up outbox event share one transaction. A duplicate `eventId` therefore creates no additional result or transition.

## Failure before publication acknowledgement

Application state and outbox rows commit before asynchronous publication begins. When Kafka is unavailable, `published_at` stays empty and `publish_attempts` increments. A later publisher cycle retries the event. The automated test uses a deterministic failing sender; it does not claim to simulate every possible process crash.

## Worker failure

If processing fails before the database commit, the `processed_event` claim rolls back with all other worker writes. Kafka retries the record; after the configured attempts are exhausted, it is routed to `.DLT`.

## Logging policy

Application logs are signal-oriented. A committed state transition is logged once with `applicationId`, previous and new status, version, source, and related event ID. Worker completion and outbox publication failures remain separate operational signals. Expected reads, idempotent replays, duplicate events, and invalid request details use `DEBUG`; rejected business commands use `WARN`. MDC propagates `requestId`, `applicationId`, and `eventId`, while Kafka framework and client internals remain at `WARN` by default.

## Demonstrator boundary

The preliminary check is not credit scoring. A shared database keeps the local demonstration compact and allows the worker to update the aggregate atomically. With separate database ownership, the worker would send a command or result event back to the aggregate owner instead of updating its tables directly. That alternative is a future proposal only.
