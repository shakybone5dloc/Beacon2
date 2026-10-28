-- People the user deals with: recruiters, hiring managers, interviewers, referrals.
-- Owned per user like applications and companies.
CREATE TABLE people (
    id             uuid          PRIMARY KEY,
    owner_id       uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name           varchar(200)  NOT NULL,
    -- Where they work; a recruiter's agency is a company with type AGENCY.
    company_id     uuid          REFERENCES companies (id),
    title          varchar(200),
    -- Their usual role; each application link has its own.
    role           varchar(20)   NOT NULL,
    -- Only for recruiters: IN_HOUSE, AGENCY, RPO, INDEPENDENT.
    recruiter_type varchar(20),
    email          varchar(320),
    phone          varchar(50),
    linkedin_url   varchar(2048),
    notes          varchar(4000),
    version        bigint        NOT NULL,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL
);
CREATE INDEX ix_people_owner ON people (owner_id, name);
CREATE INDEX ix_people_company ON people (company_id);

-- Who is involved in which application, and as what: the same person can be the
-- recruiter on one application and a referral on another.
CREATE TABLE application_people (
    application_id uuid        NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    person_id      uuid        NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    role           varchar(20) NOT NULL,
    added_at       timestamptz NOT NULL,
    PRIMARY KEY (application_id, person_id)
);
CREATE INDEX ix_application_people_person ON application_people (person_id);

-- FULL_TIME, CONTRACT, CONTRACT_TO_HIRE, C2C, PART_TIME; null when not known.
ALTER TABLE job_applications ADD COLUMN employment_type varchar(20);
