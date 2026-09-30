-- The user's own shopping list: products from their history or free-text items.
CREATE TABLE shopping_list_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    product_id BIGINT REFERENCES products (id) ON DELETE SET NULL,
    name       VARCHAR(200)   NOT NULL,
    quantity   NUMERIC(12, 4),
    checked    BOOLEAN        NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX ix_shopping_list_items_user ON shopping_list_items (user_id, checked, created_at);

-- A product appears at most once among the items still to buy
CREATE UNIQUE INDEX ux_shopping_list_items_open_product
    ON shopping_list_items (user_id, product_id)
    WHERE product_id IS NOT NULL AND NOT checked;
