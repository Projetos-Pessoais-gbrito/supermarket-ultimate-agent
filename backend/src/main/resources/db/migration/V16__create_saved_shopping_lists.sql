-- Named copies of the shopping list ("Compra do mês") to reuse in later shopping trips.
CREATE TABLE saved_shopping_lists (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(80)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Saving under an existing name replaces that list
CREATE UNIQUE INDEX ux_saved_shopping_lists_user_name ON saved_shopping_lists (user_id, lower(name));

CREATE TABLE saved_shopping_list_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    list_id    BIGINT         NOT NULL REFERENCES saved_shopping_lists (id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products (id) ON DELETE SET NULL,
    name       VARCHAR(200)   NOT NULL,
    quantity   NUMERIC(12, 4),
    position   INT            NOT NULL
);

CREATE INDEX ix_saved_shopping_list_items_list ON saved_shopping_list_items (list_id, position);
