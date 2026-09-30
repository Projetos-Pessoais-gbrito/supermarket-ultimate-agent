CREATE TABLE receipt_payments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_id BIGINT         NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    method     VARCHAR(60)    NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL
);

CREATE INDEX ix_receipt_payments_receipt ON receipt_payments (receipt_id);
