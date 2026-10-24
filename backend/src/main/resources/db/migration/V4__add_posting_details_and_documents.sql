-- Details copied from the job ad, so they survive the posting being taken down.
ALTER TABLE job_applications ADD COLUMN location         varchar(500);
ALTER TABLE job_applications ADD COLUMN remote           boolean;
ALTER TABLE job_applications ADD COLUMN salary_min       integer;
ALTER TABLE job_applications ADD COLUMN salary_max       integer;
ALTER TABLE job_applications ADD COLUMN salary_currency  varchar(3);
ALTER TABLE job_applications ADD COLUMN salary_period    varchar(10);
ALTER TABLE job_applications ADD COLUMN salary_text      varchar(100);
-- Where the posting was found: a job board name, "Referral", "Company site"...
ALTER TABLE job_applications ADD COLUMN source           varchar(100);
ALTER TABLE job_applications ADD COLUMN posted_on        date;
ALTER TABLE job_applications ADD COLUMN job_description  text;

-- Files kept with an application (posting PDF, resume, cover letter...).
-- application_id is null for library documents reused across applications.
CREATE TABLE documents (
    id              uuid            PRIMARY KEY,
    application_id  uuid            REFERENCES job_applications (id) ON DELETE CASCADE,
    kind            varchar(32)     NOT NULL,
    filename        varchar(255)    NOT NULL,
    content_type    varchar(100)    NOT NULL,
    size_bytes      integer         NOT NULL,
    content         bytea           NOT NULL,
    created_at      timestamptz     NOT NULL
);

CREATE INDEX ix_documents_application ON documents (application_id, created_at);
