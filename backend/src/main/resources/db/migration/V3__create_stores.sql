CREATE TABLE stores (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cnpj       CHAR(14)     NOT NULL,
    name       VARCHAR(200) NOT NULL,
    address    VARCHAR(500),
    state_code CHAR(2)      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ux_stores_cnpj UNIQUE (cnpj),
    CONSTRAINT ck_stores_cnpj_digits CHECK (cnpj ~ '^[0-9]{14}$')
);
