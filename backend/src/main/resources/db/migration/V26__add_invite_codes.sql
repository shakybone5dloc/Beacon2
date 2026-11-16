-- Invite codes (L2): while Beacon is an invite-only beta, signing up takes a code an admin
-- handed out. Each code works once, until it expires or is revoked.
CREATE TABLE invite_codes (
    id          uuid          PRIMARY KEY,
    -- What the person types or the sign-up link carries, e.g. K7QX-M2PA-9DTR.
    code        varchar(32)   NOT NULL UNIQUE,
    -- Who it's for, so the admin remembers ("Pat, from the LinkedIn post").
    note        varchar(200),
    created_by  uuid          REFERENCES users (id) ON DELETE SET NULL,
    created_at  timestamptz   NOT NULL,
    expires_at  timestamptz   NOT NULL,
    -- The account it made; set in the same transaction as the account, so a code is used once.
    used_by     uuid          REFERENCES users (id) ON DELETE SET NULL,
    used_at     timestamptz,
    revoked_at  timestamptz
);
CREATE INDEX ix_invite_codes_created ON invite_codes (created_at DESC);
