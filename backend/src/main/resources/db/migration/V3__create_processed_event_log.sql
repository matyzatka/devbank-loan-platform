CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE loan_application_event_log (
    event_id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_event_log_processed_event
        FOREIGN KEY (event_id) REFERENCES processed_event (event_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_loan_application_event_log_application
    ON loan_application_event_log (application_id, received_at);

