-- The outreach log: each message, text or call with a person, optionally about
-- one application. Owned per user like people and applications.
CREATE TABLE contacts (
    id             uuid           PRIMARY KEY,
    owner_id       uuid           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    person_id      uuid           NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    -- The log outlives the application it was about.
    application_id uuid           REFERENCES job_applications (id) ON DELETE SET NULL,
    -- EMAIL, SMS, LINKEDIN, PHONE, OTHER
    channel        varchar(20)    NOT NULL,
    -- INBOUND (they reached out) or OUTBOUND (the user did)
    direction      varchar(10)    NOT NULL,
    occurred_at    timestamptz    NOT NULL,
    -- The pasted message, or notes from a call.
    body           varchar(20000),
    version        bigint         NOT NULL,
    created_at     timestamptz    NOT NULL,
    updated_at     timestamptz    NOT NULL
);
CREATE INDEX ix_contacts_person ON contacts (person_id, occurred_at DESC);
CREATE INDEX ix_contacts_application ON contacts (application_id, occurred_at DESC);
