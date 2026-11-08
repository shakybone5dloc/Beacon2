-- The weekly digest (A5): Home's sections emailed on the day and hour the user picks, in their time zone.
CREATE TABLE digest_settings (
    user_id       uuid          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    enabled       boolean       NOT NULL DEFAULT false,
    -- MONDAY ... SUNDAY
    day_of_week   varchar(10)   NOT NULL DEFAULT 'MONDAY',
    -- Local hour, 0-23
    hour          integer       NOT NULL DEFAULT 8 CHECK (hour BETWEEN 0 AND 23),
    -- IANA zone id, e.g. America/Chicago
    zone          varchar(64)   NOT NULL DEFAULT 'UTC',
    -- When the last scheduled digest went out; at most one per week.
    last_sent_at  timestamptz,
    version       bigint        NOT NULL DEFAULT 0,
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL
);

CREATE INDEX ix_digest_settings_enabled ON digest_settings (day_of_week) WHERE enabled;
