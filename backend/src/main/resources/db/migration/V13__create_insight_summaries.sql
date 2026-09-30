-- AI tips per user, kept until the facts they were written from change (and across restarts).
-- facts_hash = SHA-256 of the prompt version + the computed facts.
CREATE TABLE insight_summaries (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    facts_hash CHAR(64)    NOT NULL,
    tips       JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ux_insight_summaries_user_facts UNIQUE (user_id, facts_hash)
);
