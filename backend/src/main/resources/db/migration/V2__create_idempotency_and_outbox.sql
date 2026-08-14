CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    request_hash CHAR(64) NOT NULL,
    application_id UUID NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_idempotency_application
        FOREIGN KEY (application_id) REFERENCES loan_application (id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INTEGER NOT NULL CHECK (schema_version > 0),
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    publish_attempts INTEGER NOT NULL DEFAULT 0 CHECK (publish_attempts >= 0),
    CONSTRAINT fk_outbox_application
        FOREIGN KEY (aggregate_id) REFERENCES loan_application (id)
);

CREATE INDEX idx_outbox_event_unpublished
    ON outbox_event (occurred_at)
    WHERE published_at IS NULL;
