-- Canonical product shared across stores (e.g. "ARROZ TIO JOAO 5KG")
CREATE TABLE products (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    normalized_name VARCHAR(200) NOT NULL,
    brand           VARCHAR(100),
    category        VARCHAR(50),
    measure_value   NUMERIC(12, 4),
    measure_unit    VARCHAR(10),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_products_normalized_name_trgm ON products USING gin (normalized_name gin_trgm_ops);
