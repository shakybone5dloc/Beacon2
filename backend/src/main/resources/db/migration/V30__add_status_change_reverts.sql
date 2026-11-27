-- A revert is a new history entry that points at the change it undoes.
ALTER TABLE application_status_changes
    ADD COLUMN reverts_change_id bigint NULL REFERENCES application_status_changes (id) ON DELETE SET NULL;
