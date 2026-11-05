-- The story bank (A7): STAR stories kept once and reused across applications and briefs.
CREATE TABLE stories (
    id          uuid          PRIMARY KEY,
    owner_id    uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       varchar(200)  NOT NULL,
    situation   text          NOT NULL DEFAULT '',
    task        text          NOT NULL DEFAULT '',
    action      text          NOT NULL DEFAULT '',
    result      text          NOT NULL DEFAULT '',
    -- The numbers that back the story up: users, latency, money saved, team size.
    metrics     text          NOT NULL DEFAULT '',
    -- What it answers, like "leadership" or "hardest problem"; lower case, so they match.
    tags        text[]        NOT NULL DEFAULT '{}',
    version     bigint        NOT NULL DEFAULT 0,
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL
);
CREATE INDEX ix_stories_owner ON stories (owner_id, updated_at DESC);
