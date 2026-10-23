CREATE TABLE job_applications (
    id          uuid            PRIMARY KEY,
    company     varchar(200)    NOT NULL,
    role        varchar(200)    NOT NULL,
    job_url     varchar(2048),
    notes       varchar(4000),
    status      varchar(32)     NOT NULL,
    version     bigint          NOT NULL,
    created_at  timestamptz     NOT NULL,
    updated_at  timestamptz     NOT NULL
);

CREATE INDEX ix_job_applications_created_at ON job_applications (created_at);