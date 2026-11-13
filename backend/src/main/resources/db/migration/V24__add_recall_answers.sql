-- Recall practice (A8): how each card was answered, and what the AI made of a typed or
-- spoken answer. The grade the scheduler used is still `grade`, the one the user confirmed.
-- REVEAL (answered in your head, then revealed), TYPE (typed first) or SAY (spoken first).
ALTER TABLE study_reviews ADD COLUMN mode varchar(10) NOT NULL DEFAULT 'REVEAL';
-- What the user typed or said, when they did.
ALTER TABLE study_reviews ADD COLUMN answer_text text;
-- The AI's suggestion and its note, when it was asked; null otherwise.
ALTER TABLE study_reviews ADD COLUMN suggested_grade varchar(10);
ALTER TABLE study_reviews ADD COLUMN feedback text;
