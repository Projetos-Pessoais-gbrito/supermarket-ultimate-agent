-- Trigram similarity to match the same product across stores (see ADR 0004)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Accent-insensitive normalization of product descriptions
CREATE EXTENSION IF NOT EXISTS unaccent;
