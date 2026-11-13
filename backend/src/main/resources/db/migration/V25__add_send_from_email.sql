-- The address the user sends Beacon-written emails (like a check-in) from, when it isn't
-- the one they sign in with. A Gmail address opens Gmail as that account; any other opens
-- the device's mail app. It replaces choosing Gmail or the mail app by hand (V23).
ALTER TABLE users ADD COLUMN send_from_email varchar(320);
ALTER TABLE users DROP COLUMN mail_client;
