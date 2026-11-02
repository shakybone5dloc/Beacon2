-- Saved searches that run on their own schedule and collect new postings as matches.
CREATE TABLE saved_searches (
    id                  uuid          PRIMARY KEY,
    owner_id            uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name                varchar(100)  NOT NULL,
    keywords            varchar(500)  NOT NULL,
    -- A posting whose title contains any of these is dropped.
    exclude_words       text[]        NOT NULL DEFAULT '{}',
    location            varchar(200),
    remote_only         boolean       NOT NULL DEFAULT false,
    -- Postings that state pay below this are dropped; ones that don't state it are kept.
    salary_min          integer       CHECK (salary_min >= 0),
    -- Source ids to search; empty means every source the user can search.
    sources             text[]        NOT NULL DEFAULT '{}',
    posted_within_days  integer       NOT NULL DEFAULT 7 CHECK (posted_within_days BETWEEN 1 AND 60),
    -- The resume new matches are fit-scored against; no resume, no scoring.
    resume_document_id  uuid          REFERENCES documents (id) ON DELETE SET NULL,
    min_score           integer       CHECK (min_score BETWEEN 0 AND 100),
    active              boolean       NOT NULL DEFAULT true,
    -- Days of the week, like MONDAY, and the hour, in time_zone.
    run_days            text[]        NOT NULL,
    run_hour            integer       NOT NULL CHECK (run_hour BETWEEN 0 AND 23),
    time_zone           varchar(64)   NOT NULL,
    next_run_at         timestamptz,
    last_run_at         timestamptz,
    -- Set while a run is under way, so two never overlap.
    running_since       timestamptz,
    version             bigint        NOT NULL DEFAULT 0,
    created_at          timestamptz   NOT NULL,
    updated_at          timestamptz   NOT NULL
);
CREATE INDEX ix_saved_searches_owner ON saved_searches (owner_id, created_at);
CREATE INDEX ix_saved_searches_due ON saved_searches (next_run_at) WHERE active;

-- What each run did, for the summary on the saved search.
CREATE TABLE search_runs (
    id               uuid         PRIMARY KEY,
    saved_search_id  uuid         NOT NULL REFERENCES saved_searches (id) ON DELETE CASCADE,
    started_at       timestamptz  NOT NULL,
    finished_at      timestamptz,
    -- Postings the sources returned, before filtering and dedupe.
    gathered         integer      NOT NULL DEFAULT 0,
    new_matches      integer      NOT NULL DEFAULT 0,
    scored           integer      NOT NULL DEFAULT 0,
    -- One line per source ("Remotive: 12") and anything worth telling the user, like a skipped scoring.
    notes            text[]       NOT NULL DEFAULT '{}'
);
CREATE INDEX ix_search_runs_search ON search_runs (saved_search_id, started_at DESC);

-- Postings a run found that weren't on the board or found before. Kept after a decision,
-- so a dismissed posting doesn't come back.
CREATE TABLE job_matches (
    id               uuid           PRIMARY KEY,
    owner_id         uuid           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    saved_search_id  uuid           REFERENCES saved_searches (id) ON DELETE SET NULL,
    -- Normalized company and title; the same job found twice is one match.
    match_key        varchar(64)    NOT NULL,
    source           varchar(100)   NOT NULL,
    external_id      varchar(300)   NOT NULL,
    title            varchar(300)   NOT NULL,
    company          varchar(300),
    location         varchar(500),
    remote           boolean        NOT NULL DEFAULT false,
    url              varchar(2048)  NOT NULL,
    posted_at        timestamptz,
    closes_on        date,
    salary_min       integer,
    salary_max       integer,
    salary_currency  varchar(10),
    salary_period    varchar(20),
    salary_text      varchar(200),
    snippet          text,
    description      text,
    -- NEW, SAVED, DISMISSED, or BELOW_MIN (scored under the search's minimum).
    status           varchar(20)    NOT NULL,
    score            integer        CHECK (score BETWEEN 0 AND 100),
    fit_score_id     uuid           REFERENCES fit_scores (id) ON DELETE SET NULL,
    application_id   uuid           REFERENCES job_applications (id) ON DELETE SET NULL,
    found_at         timestamptz    NOT NULL,
    decided_at       timestamptz,
    CONSTRAINT ux_job_matches_key UNIQUE (owner_id, match_key)
);
CREATE INDEX ix_job_matches_owner_status ON job_matches (owner_id, status, found_at DESC);
CREATE INDEX ix_job_matches_search ON job_matches (saved_search_id);
