-- Interviews on an application. Owned per user like everything else.
CREATE TABLE interviews (
    id               uuid           PRIMARY KEY,
    owner_id         uuid           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    application_id   uuid           NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    starts_at        timestamptz    NOT NULL,
    -- The IANA zone it was scheduled in (e.g. America/Chicago), to show it the way it was agreed.
    time_zone        varchar(64)    NOT NULL,
    duration_minutes integer        NOT NULL,
    -- PHONE_SCREEN, TECHNICAL, PANEL, ONSITE, OTHER
    type             varchar(20)    NOT NULL,
    -- An address or a meeting link.
    location         varchar(2048),
    prep_notes       varchar(10000),
    debrief_notes    varchar(10000),
    -- PASSED, DID_NOT_PASS, CANCELLED; null until known.
    outcome          varchar(20),
    version          bigint         NOT NULL,
    created_at       timestamptz    NOT NULL,
    updated_at       timestamptz    NOT NULL
);
CREATE INDEX ix_interviews_application ON interviews (application_id, starts_at);

-- Who interviews: people from the user's list. A plain many-to-many; the link has no data of its own.
CREATE TABLE interview_people (
    interview_id uuid NOT NULL REFERENCES interviews (id) ON DELETE CASCADE,
    person_id    uuid NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    PRIMARY KEY (interview_id, person_id)
);
CREATE INDEX ix_interview_people_person ON interview_people (person_id);
