CREATE TABLE receipt_items (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_id       BIGINT         NOT NULL REFERENCES receipts (id) ON DELETE CASCADE,
    store_product_id BIGINT         NOT NULL REFERENCES store_products (id),
    line_number      INT            NOT NULL,
    quantity         NUMERIC(12, 4) NOT NULL,
    unit             VARCHAR(10)    NOT NULL,
    unit_price       NUMERIC(12, 4) NOT NULL,
    total_price      NUMERIC(12, 2) NOT NULL,
    CONSTRAINT ux_receipt_items_line UNIQUE (receipt_id, line_number),
    CONSTRAINT ck_receipt_items_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX ix_receipt_items_store_product ON receipt_items (store_product_id);
