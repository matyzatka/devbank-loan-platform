ALTER TABLE loan_application
    ADD COLUMN rejection_reason VARCHAR(500);

ALTER TABLE loan_application_status_history
    ADD COLUMN reason VARCHAR(500);

UPDATE loan_application
SET rejection_reason = 'Důvod nebyl ve starší verzi evidován.'
WHERE status = 'REJECTED';

ALTER TABLE loan_application
    ADD CONSTRAINT chk_rejection_reason
        CHECK ((status = 'REJECTED' AND rejection_reason IS NOT NULL AND length(trim(rejection_reason)) > 0)
            OR (status <> 'REJECTED' AND rejection_reason IS NULL));
