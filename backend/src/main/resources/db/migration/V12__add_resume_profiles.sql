-- What Beacon knows about each library resume: a label, which one is the default,
-- its text (read once from the file), and a short summary for fit scoring.
CREATE TABLE resume_profiles (
    document_id      uuid          PRIMARY KEY REFERENCES documents (id) ON DELETE CASCADE,
    owner_id         uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    label            varchar(100)  NOT NULL,
    is_default       boolean       NOT NULL,
    -- PENDING (not read yet), READ, or UNREADABLE (Word files, images, scanned PDFs).
    text_status      varchar(20)   NOT NULL,
    text             text,
    target_titles    text[]        NOT NULL DEFAULT '{}',
    skills           text[]        NOT NULL DEFAULT '{}',
    years_experience integer,
    version          bigint        NOT NULL,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL
);
-- At most one default per user.
CREATE UNIQUE INDEX ux_resume_profiles_default ON resume_profiles (owner_id) WHERE is_default;

-- Resumes already in the library get a profile named after the file; each user's newest
-- becomes the default. Their text is read the first time they're listed.
INSERT INTO resume_profiles (document_id, owner_id, label, is_default, text_status, version, created_at, updated_at)
SELECT d.id,
       d.owner_id,
       left(regexp_replace(d.filename, '\.[^.]+$', ''), 100),
       row_number() OVER (PARTITION BY d.owner_id ORDER BY d.created_at DESC) = 1,
       'PENDING',
       0,
       d.created_at,
       d.created_at
FROM documents d
WHERE d.application_id IS NULL AND d.kind = 'RESUME' AND d.owner_id IS NOT NULL;
