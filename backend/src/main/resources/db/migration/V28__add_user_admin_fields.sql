-- Admin basics: switching an account off, and seeing when each was last used.

-- Set while an admin has switched the account off: it can't sign in, and its sessions end.
ALTER TABLE users ADD COLUMN disabled_at timestamptz;

-- The last time the account was used, kept to within an hour. Null until it's next used.
ALTER TABLE users ADD COLUMN last_active_at timestamptz;
