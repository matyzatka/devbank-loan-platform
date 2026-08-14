# Loan Platform Demo

A small, realistic corporate loan application processing demonstrator for a Java/React developer portfolio. It is intentionally a modular monolith so business rules, delivery reliability, and operational concerns remain understandable and testable.

## Current milestone

This initial scaffold contains:

- a Java 21 and Spring Boot 3.5 backend;
- packages for API, application, domain, persistence, messaging, and configuration concerns;
- Spring Boot Actuator health at `GET /actuator/health`;
- one integration test for the health endpoint;
- Docker and Compose placeholders;
- reserved frontend, infrastructure, and documentation directories.

The domain increment adds the loan application aggregate and its explicit workflow. PostgreSQL persistence uses Flyway migrations and jOOQ with optimistic locking. The application layer provides transactional create/get/workflow use cases, request idempotency, and a transactional outbox. A versioned REST API exposes these use cases with validation, RFC 9457 problem responses, and generated OpenAPI documentation. The broker, frontend, and AWS infrastructure are deliberately not implemented yet.

## Proposed structure

```text
loan-platform-demo/
|-- backend/
|   |-- src/main/java/com/example/loanplatform/
|   |   |-- api/
|   |   |-- application/
|   |   |-- configuration/
|   |   |-- domain/
|   |   |-- messaging/
|   |   `-- persistence/
|   `-- pom.xml
|-- frontend/
|-- infra/
|-- docs/
|-- docker-compose.yml
`-- README.md
```

## Dependency choices

The backend currently uses Spring Web, Actuator, Spring Boot Test, and Lombok. Lombok is deliberately restricted: `@Getter` removes mechanical aggregate accessors, while `@Data`, experimental features, and `@SneakyThrows` are forbidden in `lombok.config`. Later milestones are expected to add dependencies only when their capability is implemented:

- Spring Kafka plus a Kafka-compatible Compose broker for event integration;
- springdoc-openapi for API documentation;
- Testcontainers PostgreSQL and Kafka modules for integration testing.

This keeps the first milestone small and avoids implying that unimplemented capabilities already exist.

## Local verification

Prerequisites: Java 21 and Maven 3.9 or newer.

```bash
cd backend
mvn clean verify
mvn spring-boot:run
```

Then request `http://localhost:8080/actuator/health`.

OpenAPI JSON is available at `http://localhost:8080/v3/api-docs` and Swagger UI at `http://localhost:8080/swagger-ui.html`.

To build and run the placeholder container after creating the backend JAR:

```bash
cd backend
mvn clean package
cd ..
docker compose up --build
```

## Delivery boundaries

- **Implemented and locally verified:** recorded here only after the corresponding build or test succeeds.
- **Tested in AWS:** nothing yet.
- **AWS infrastructure design:** reserved for a later CDK milestone; nothing has been deployed and no credentials are used.

## Planned architecture

The intended flow is REST API to application service to one PostgreSQL transaction that stores both domain state and an outbox record. A publisher will deliver outbox events to Kafka, and consumers will process those events idempotently under at-least-once delivery. This is a future design, not a claim about the current scaffold.
