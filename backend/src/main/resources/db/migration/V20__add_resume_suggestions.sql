-- The resume actually sent with an application (A2), so Stats can say which resume gets interviews.
ALTER TABLE job_applications
    ADD COLUMN sent_resume_id uuid REFERENCES documents (id) ON DELETE SET NULL;

-- Edits the AI suggests to tailor a resume to one posting (A2). Suggestions only: the resume
-- file is never changed. Asking again replaces the open and dismissed ones and keeps those done.
CREATE TABLE resume_suggestions (
    id                  uuid         PRIMARY KEY,
    owner_id            uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    application_id      uuid         NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    resume_document_id  uuid         REFERENCES documents (id) ON DELETE SET NULL,
    position            integer      NOT NULL,
    -- ADD, EMPHASIZE, REWORD, MOVE or CUT
    kind                varchar(20)  NOT NULL,
    -- Where on the resume, such as "Summary" or "Experience: PropKnot"
    section             varchar(200) NOT NULL,
    suggestion          text         NOT NULL,
    reason              text         NOT NULL,
    -- Wording to paste in, when the change is a rewrite; null otherwise
    example             text,
    -- OPEN, DONE or DISMISSED
    state               varchar(20)  NOT NULL DEFAULT 'OPEN',
    model               varchar(100) NOT NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL
);

CREATE INDEX ix_resume_suggestions_application ON resume_suggestions (application_id, position);
