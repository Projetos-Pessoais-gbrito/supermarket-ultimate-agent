-- Long-lived sessions. Only a SHA-256 hash of each token is stored, so a database leak
-- does not expose usable tokens.
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_refresh_tokens_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
