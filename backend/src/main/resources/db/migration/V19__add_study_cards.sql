-- Study cards (A6): flashcards to drill before an interview, in decks, scheduled by spaced repetition.
CREATE TABLE study_decks (
    id              uuid          PRIMARY KEY,
    owner_id        uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name            varchar(200)  NOT NULL,
    -- The application the deck prepares for, when it has one; a deck outlives a deleted application.
    application_id  uuid          REFERENCES job_applications (id) ON DELETE SET NULL,
    version         bigint        NOT NULL DEFAULT 0,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL
);
CREATE INDEX ix_study_decks_owner ON study_decks (owner_id, updated_at DESC);
-- One deck per application, so making cards from a brief again adds to the same deck.
CREATE UNIQUE INDEX uq_study_decks_application ON study_decks (application_id) WHERE application_id IS NOT NULL;

CREATE TABLE study_cards (
    id             uuid          PRIMARY KEY,
    owner_id       uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    deck_id        uuid          NOT NULL REFERENCES study_decks (id) ON DELETE CASCADE,
    question       text          NOT NULL,
    answer         text          NOT NULL DEFAULT '',
    -- MANUAL, BRIEF (made by the AI from an interview brief) or STORY (from the story bank).
    source         varchar(20)   NOT NULL,
    story_id       uuid          REFERENCES stories (id) ON DELETE SET NULL,
    -- DRAFT until the user keeps it; only ACTIVE cards are studied.
    status         varchar(20)   NOT NULL,
    -- SM-2 style scheduling: ease factor, days until the next review, and when it's due.
    ease           numeric(4,2)  NOT NULL DEFAULT 2.50,
    interval_days  integer       NOT NULL DEFAULT 0 CHECK (interval_days >= 0),
    repetitions    integer       NOT NULL DEFAULT 0 CHECK (repetitions >= 0),
    lapses         integer       NOT NULL DEFAULT 0 CHECK (lapses >= 0),
    due_at         timestamptz   NOT NULL,
    last_reviewed_at timestamptz,
    version        bigint        NOT NULL DEFAULT 0,
    created_at     timestamptz   NOT NULL,
    updated_at     timestamptz   NOT NULL
);
CREATE INDEX ix_study_cards_deck ON study_cards (deck_id, created_at);
-- "What's due today" across all of a user's decks.
CREATE INDEX ix_study_cards_due ON study_cards (owner_id, due_at) WHERE status = 'ACTIVE';

-- Every grade given, for how a card has gone over time.
CREATE TABLE study_reviews (
    id           uuid          PRIMARY KEY,
    card_id      uuid          NOT NULL REFERENCES study_cards (id) ON DELETE CASCADE,
    grade        varchar(10)   NOT NULL,
    reviewed_at  timestamptz   NOT NULL,
    interval_days integer      NOT NULL
);
CREATE INDEX ix_study_reviews_card ON study_reviews (card_id, reviewed_at);
