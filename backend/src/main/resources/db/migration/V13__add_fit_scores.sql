-- Fit scores: how well a resume fits a posting, as the AI judged it. Each row is keyed
-- by a hash of everything that went into it (posting text, resume and its text, prompt
-- version), so asking again with the same inputs reuses the row instead of paying again.
CREATE TABLE fit_scores (
    id                 uuid          PRIMARY KEY,
    owner_id           uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    resume_document_id uuid          NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    input_hash         varchar(64)   NOT NULL,
    score              integer       NOT NULL CHECK (score BETWEEN 0 AND 100),
    matched_skills     text[]        NOT NULL DEFAULT '{}',
    gaps               text[]        NOT NULL DEFAULT '{}',
    dealbreakers       text[]        NOT NULL DEFAULT '{}',
    rationale          text          NOT NULL,
    apply_by           date,
    provider           varchar(20)   NOT NULL,
    model              varchar(100)  NOT NULL,
    created_at         timestamptz   NOT NULL,
    CONSTRAINT ux_fit_scores_input UNIQUE (owner_id, input_hash)
);

-- The fit shown on an application: its most recent score.
CREATE TABLE application_fits (
    application_id uuid        PRIMARY KEY REFERENCES job_applications (id) ON DELETE CASCADE,
    fit_score_id   uuid        NOT NULL REFERENCES fit_scores (id) ON DELETE CASCADE,
    linked_at      timestamptz NOT NULL
);
CREATE INDEX ix_application_fits_fit_score ON application_fits (fit_score_id);
