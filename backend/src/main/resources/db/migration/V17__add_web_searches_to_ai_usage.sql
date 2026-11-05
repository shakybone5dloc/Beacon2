-- Web searches an AI call ran (company research in the interview brief), charged per search on top of tokens.
ALTER TABLE ai_usage ADD COLUMN web_searches bigint NOT NULL DEFAULT 0 CHECK (web_searches >= 0);
