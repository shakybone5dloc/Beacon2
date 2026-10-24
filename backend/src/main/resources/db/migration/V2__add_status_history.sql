-- When the application entered its current status. Denormalized from the
-- history below so the board can show time-in-stage without a join.
ALTER TABLE job_applications ADD COLUMN status_changed_at timestamptz;

-- Existing rows have no recorded history; updated_at is the best guess for
-- when anything past SAVED was reached (it may also reflect a later edit).
UPDATE job_applications
SET status_changed_at = CASE WHEN status = 'SAVED' THEN created_at ELSE updated_at END;

ALTER TABLE job_applications ALTER COLUMN status_changed_at SET NOT NULL;

CREATE TABLE application_status_changes (
    id              bigint          GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id  uuid            NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    from_status     varchar(32),
    to_status       varchar(32)     NOT NULL,
    changed_at      timestamptz     NOT NULL
);

CREATE INDEX ix_application_status_changes_application
    ON application_status_changes (application_id, changed_at);

-- Backfill: every application was created as SAVED, and anything further
-- along gets a single inferred jump to its current status.
INSERT INTO application_status_changes (application_id, from_status, to_status, changed_at)
SELECT id, NULL, 'SAVED', created_at FROM job_applications;

INSERT INTO application_status_changes (application_id, from_status, to_status, changed_at)
SELECT id, 'SAVED', status, status_changed_at FROM job_applications WHERE status <> 'SAVED';
