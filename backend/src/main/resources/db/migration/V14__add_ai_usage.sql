-- One row per AI call, with the token counts the provider reported. The cost is worked out
-- from the price in effect on the day of the call and kept, so a later price change doesn't
-- rewrite past months. Null when the model had no price on file.
--
-- Rows are written in their own transaction, so a call is counted even when the work that
-- asked for it then fails (the provider bills it either way). That's why owner_id has no
-- foreign key: the check couldn't see a user created in a transaction not yet committed.
-- Deleting an account (X5) must delete its rows here too.
CREATE TABLE ai_usage (
    id                  uuid           PRIMARY KEY,
    owner_id            uuid           NOT NULL,
    feature             varchar(40)    NOT NULL,
    provider            varchar(20)    NOT NULL,
    model               varchar(100)   NOT NULL,
    -- All input tokens, cached ones included; cached_input_tokens says how many of them were cached.
    input_tokens        bigint         NOT NULL CHECK (input_tokens >= 0),
    cached_input_tokens bigint         NOT NULL DEFAULT 0 CHECK (cached_input_tokens >= 0),
    -- Reasoning tokens are billed as output and are counted here.
    output_tokens       bigint         NOT NULL CHECK (output_tokens >= 0),
    cost_usd            numeric(12, 6),
    created_at          timestamptz    NOT NULL
);
CREATE INDEX ix_ai_usage_owner_created ON ai_usage (owner_id, created_at);

-- An optional monthly AI budget, in US dollars; null means no budget.
ALTER TABLE users ADD COLUMN ai_monthly_budget_usd numeric(10, 2) CHECK (ai_monthly_budget_usd > 0);
