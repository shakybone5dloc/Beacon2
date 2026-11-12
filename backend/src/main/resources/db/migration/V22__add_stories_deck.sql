-- Drilling stories as study cards (A6/A7): each user's story bank gets one deck of its own,
-- apart from the per-application decks and the ones made by hand.
ALTER TABLE study_decks ADD COLUMN kind varchar(20) NOT NULL DEFAULT 'STANDARD';
CREATE UNIQUE INDEX uq_study_decks_stories ON study_decks (owner_id) WHERE kind = 'STORIES';
-- Cards made from a story follow it when it's edited.
CREATE INDEX ix_study_cards_story ON study_cards (story_id) WHERE story_id IS NOT NULL;
