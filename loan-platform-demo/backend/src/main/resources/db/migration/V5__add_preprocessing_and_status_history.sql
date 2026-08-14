ALTER TABLE outbox_event RENAME COLUMN correlation_id TO request_id;
ALTER TABLE loan_application_event_log RENAME COLUMN correlation_id TO request_id;

CREATE TABLE loan_preprocessing_result (
    event_id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    result VARCHAR(30) NOT NULL CHECK (result IN ('PASSED')),
    details VARCHAR(500) NOT NULL,
    checked_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_preprocessing_application
        FOREIGN KEY (application_id) REFERENCES loan_application (id)
);

CREATE INDEX idx_preprocessing_application
    ON loan_preprocessing_result (application_id, checked_at);

CREATE TABLE loan_application_status_history (
    id UUID PRIMARY KEY,
    application_id UUID NOT NULL,
    previous_status VARCHAR(30),
    new_status VARCHAR(30) NOT NULL,
    application_version BIGINT NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    changed_by VARCHAR(30) NOT NULL CHECK (changed_by IN ('API', 'WORKER')),
    request_id VARCHAR(100) NOT NULL,
    event_id UUID,
    CONSTRAINT fk_status_history_application
        FOREIGN KEY (application_id) REFERENCES loan_application (id)
);

CREATE INDEX idx_status_history_application
    ON loan_application_status_history (application_id, changed_at, application_version);
