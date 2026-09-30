# ADR 0004 — Database: PostgreSQL

- Status: accepted
- Date: 2026-09-29

## Context
The data is strongly relational: users → receipts → items → products → stores.
The insights are aggregations over time (price per product per day, spend per
category per month, cheapest store per product).

MariaDB was considered; note that MariaDB is also a **relational** database, not NoSQL.
A document store (e.g. MongoDB) was rejected because almost every insight is a join
plus an aggregation across receipts, which is exactly what SQL is good at.

## Decision
PostgreSQL 16, because on top of a standard relational model it offers:
- **Window functions, `date_trunc`, `generate_series`** for time-series insights.
- **JSONB** to keep the raw parsed payload next to normalized columns.
- **`pg_trgm`** trigram similarity to match the same product across stores
  ("ARROZ TIO JOAO 5KG" vs "ARROZ T.JOAO TP1 5KG").

## Consequences
The schema is managed only through Flyway migrations. Tests run against a real
PostgreSQL via Testcontainers.
