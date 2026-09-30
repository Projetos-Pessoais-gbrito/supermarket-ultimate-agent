-- Monthly spending limits set by the user: one overall limit (category NULL) and/or one per category.
CREATE TABLE budgets (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    category      VARCHAR(50),
    monthly_limit NUMERIC(12, 2) NOT NULL,
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_budgets_limit_positive CHECK (monthly_limit > 0)
);

-- At most one overall limit and one limit per category for each user
CREATE UNIQUE INDEX ux_budgets_user_category ON budgets (user_id, COALESCE(category, ''));
