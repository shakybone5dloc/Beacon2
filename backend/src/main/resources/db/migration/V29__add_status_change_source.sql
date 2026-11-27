ALTER TABLE application_status_changes
    ADD COLUMN source varchar(16) NOT NULL DEFAULT 'USER',
    ADD COLUMN source_email_id varchar(255) NULL;
