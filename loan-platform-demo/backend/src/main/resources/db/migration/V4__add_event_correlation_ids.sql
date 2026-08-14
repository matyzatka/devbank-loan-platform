ALTER TABLE outbox_event ADD COLUMN correlation_id VARCHAR(100);
UPDATE outbox_event SET correlation_id = id::text WHERE correlation_id IS NULL;
ALTER TABLE outbox_event ALTER COLUMN correlation_id SET NOT NULL;

ALTER TABLE loan_application_event_log ADD COLUMN correlation_id VARCHAR(100);
UPDATE loan_application_event_log SET correlation_id = event_id::text WHERE correlation_id IS NULL;
ALTER TABLE loan_application_event_log ALTER COLUMN correlation_id SET NOT NULL;
