-- Companies become their own rows, owned per user, and applications link to one
-- instead of carrying the name as free text.

-- Rows from before accounts existed have no owner, so they can't belong to anyone's
-- company. They have been hidden since V5; remove them.
DELETE FROM documents WHERE owner_id IS NULL;
DELETE FROM job_applications WHERE owner_id IS NULL;
ALTER TABLE documents ALTER COLUMN owner_id SET NOT NULL;
ALTER TABLE job_applications ALTER COLUMN owner_id SET NOT NULL;

CREATE TABLE companies (
    id              uuid          PRIMARY KEY,
    owner_id        uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            varchar(200)  NOT NULL,
    -- CompanyNames.normalize(name): lower-cased, no "." or ",", single spaces, no trailing Inc/LLC/Corp...
    normalized_name varchar(200)  NOT NULL,
    type            varchar(16)   NOT NULL,
    website         varchar(2048),
    email_domains   text[]        NOT NULL DEFAULT '{}',
    notes           varchar(4000),
    version         bigint        NOT NULL,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL
);
CREATE UNIQUE INDEX ux_companies_owner_name ON companies (owner_id, normalized_name);

ALTER TABLE job_applications ADD COLUMN company_id uuid REFERENCES companies (id);
CREATE INDEX ix_job_applications_company ON job_applications (company_id);

-- Same steps as CompanyNames.normalize.
CREATE TEMPORARY TABLE application_company_names AS
SELECT id,
       owner_id,
       btrim(company) AS name,
       created_at,
       btrim(regexp_replace(
               regexp_replace(regexp_replace(lower(btrim(company)), '[.,]', '', 'g'), '\s+', ' ', 'g'),
               ' (inc|incorporated|llc|ltd|limited|corp|corporation|co|plc|gmbh)$', '')) AS normalized_name
FROM job_applications
-- Blank names and the placeholder job search used to save mean the employer isn't known.
WHERE NULLIF(btrim(company), '') IS NOT NULL
  AND lower(btrim(company)) <> 'unknown company';

-- One company per owner and normalized name, named as on that owner's earliest application.
INSERT INTO companies (id, owner_id, name, normalized_name, type, version, created_at, updated_at)
SELECT gen_random_uuid(), owner_id, name, normalized_name, 'EMPLOYER', 0, created_at, created_at
FROM (
    SELECT DISTINCT ON (owner_id, normalized_name) owner_id, name, normalized_name, created_at
    FROM application_company_names
    ORDER BY owner_id, normalized_name, created_at, id
) AS firsts;

UPDATE job_applications a
SET company_id = c.id
FROM application_company_names n
JOIN companies c ON c.owner_id = n.owner_id AND c.normalized_name = n.normalized_name
WHERE a.id = n.id;

DROP TABLE application_company_names;
ALTER TABLE job_applications DROP COLUMN company;
