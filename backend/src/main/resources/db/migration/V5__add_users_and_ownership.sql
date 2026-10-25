CREATE TABLE users (
    id              uuid            PRIMARY KEY,
    -- Stored lower-cased by the application, so the unique index is case-insensitive in practice.
    email           varchar(320)    NOT NULL,
    password_hash   varchar(200)    NOT NULL,
    display_name    varchar(100),
    created_at      timestamptz     NOT NULL
);

CREATE UNIQUE INDEX ux_users_email ON users (email);

-- Nullable so rows from before accounts existed survive; they stay hidden until assigned an owner.
ALTER TABLE job_applications ADD COLUMN owner_id uuid REFERENCES users (id) ON DELETE CASCADE;
CREATE INDEX ix_job_applications_owner ON job_applications (owner_id, created_at);

-- Documents carry their owner too, so library documents (no application) can be scoped.
ALTER TABLE documents ADD COLUMN owner_id uuid REFERENCES users (id) ON DELETE CASCADE;
CREATE INDEX ix_documents_owner ON documents (owner_id, application_id);
