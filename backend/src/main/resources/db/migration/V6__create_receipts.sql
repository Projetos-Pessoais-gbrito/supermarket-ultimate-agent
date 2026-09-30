CREATE TABLE receipts (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    store_id          BIGINT         NOT NULL REFERENCES stores (id),
    access_key        CHAR(44)       NOT NULL,
    number            BIGINT         NOT NULL,
    series            INT            NOT NULL,
    issued_at         TIMESTAMPTZ    NOT NULL,
    total_amount      NUMERIC(12, 2) NOT NULL,
    discount_amount   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    approximate_taxes NUMERIC(12, 2) NOT NULL DEFAULT 0,
    source_url        VARCHAR(1000)  NOT NULL,
    -- Kept to re-parse after parser fixes; personal data is removed before saving
    raw_html          TEXT           NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ux_receipts_user_access_key UNIQUE (user_id, access_key),
    CONSTRAINT ck_receipts_access_key_digits CHECK (access_key ~ '^[0-9]{44}$')
);

CREATE INDEX ix_receipts_user_issued_at ON receipts (user_id, issued_at DESC);
CREATE INDEX ix_receipts_store ON receipts (store_id);
