-- Each user's credentials for paid or keyed providers. The values are encrypted
-- with AES-GCM under the server's BEACON_SECRET_KEY; hint is the last few
-- characters of the main secret, so the settings page can say which key is saved.
CREATE TABLE user_api_keys (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider   varchar(50) NOT NULL,
    ciphertext bytea       NOT NULL,
    nonce      bytea       NOT NULL,
    hint       varchar(8),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (user_id, provider)
);
