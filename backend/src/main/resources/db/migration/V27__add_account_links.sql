-- Password reset and email confirmation (L5).

-- When the account's email was confirmed by clicking a link sent to it. Accounts made before
-- this existed count as confirmed, so nobody is asked again.
ALTER TABLE users ADD COLUMN email_confirmed_at timestamptz;
UPDATE users SET email_confirmed_at = created_at;

-- One-time links emailed to a user. Only a SHA-256 hash of the link's token is kept, so the
-- database alone can't be used to reset a password.
CREATE TABLE account_links (
    id          uuid          PRIMARY KEY,
    user_id     uuid          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- RESET_PASSWORD or CONFIRM_EMAIL.
    purpose     varchar(20)   NOT NULL,
    token_hash  varchar(64)   NOT NULL UNIQUE,
    -- The address a confirmation link was sent to; confirming only counts while it's still the
    -- account's email.
    email       varchar(320)  NOT NULL,
    created_at  timestamptz   NOT NULL,
    expires_at  timestamptz   NOT NULL,
    used_at     timestamptz
);
CREATE INDEX ix_account_links_user ON account_links (user_id, purpose);
