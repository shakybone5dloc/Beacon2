-- One living prep document per application (A1): sections the AI drafts and the user edits.
CREATE TABLE interview_briefs (
    id                  uuid         PRIMARY KEY,
    owner_id            uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    application_id      uuid         NOT NULL UNIQUE REFERENCES job_applications (id) ON DELETE CASCADE,
    -- The resume the sections are written from; null falls back to the fit's resume, then the default.
    resume_document_id  uuid         REFERENCES documents (id) ON DELETE SET NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL
);

-- A section's text, in Markdown. Only sections that have been written or edited are stored.
CREATE TABLE brief_sections (
    id            uuid         PRIMARY KEY,
    brief_id      uuid         NOT NULL REFERENCES interview_briefs (id) ON DELETE CASCADE,
    kind          varchar(40)  NOT NULL,
    -- Set only for the update written after one interview round.
    interview_id  uuid         REFERENCES interviews (id) ON DELETE CASCADE,
    content       text         NOT NULL,
    -- When the AI last wrote it, and with which model; null for text the user wrote from scratch.
    generated_at  timestamptz,
    model         varchar(100),
    -- When the user last changed it by hand; a section with edits is never rewritten without asking.
    edited_at     timestamptz,
    version       bigint       NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    CONSTRAINT uq_brief_sections UNIQUE NULLS NOT DISTINCT (brief_id, kind, interview_id)
);
