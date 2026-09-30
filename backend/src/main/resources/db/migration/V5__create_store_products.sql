-- A product as a specific store describes it on its receipts
CREATE TABLE store_products (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    store_id    BIGINT       NOT NULL REFERENCES stores (id),
    store_code  VARCHAR(60)  NOT NULL,
    description VARCHAR(300) NOT NULL,
    unit        VARCHAR(10)  NOT NULL,
    product_id  BIGINT REFERENCES products (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_store_products_store_code UNIQUE (store_id, store_code)
);

CREATE INDEX ix_store_products_product ON store_products (product_id);
CREATE INDEX ix_store_products_description_trgm ON store_products USING gin (description gin_trgm_ops);
