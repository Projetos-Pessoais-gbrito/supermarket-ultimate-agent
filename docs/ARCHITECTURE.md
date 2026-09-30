# Architecture

## Overview

```
┌──────────────┐   QR URL / access key   ┌──────────────────────────────┐   HTTPS (HTML)  ┌─────────────────┐
│  Mobile app  │ ──────────────────────▶ │  Backend (Spring Boot)       │ ──────────────▶ │ SEFAZ-SP public │
│  Expo / TS   │ ◀────────────────────── │                              │                 │ NFC-e page      │
└──────────────┘   receipts, insights    │  receipt ─ catalog ─ insight │                 └─────────────────┘
                                         │        │                │   │   prompts (no PII) ┌──────────────┐
                                         │        ▼                └───┼──────────────────▶ │ Gemini (free)│
                                         │   PostgreSQL 16             │                    └──────────────┘
                                         └──────────────────────────────┘
```

## Import flow

1. User scans the NFC-e QR code (or types the 44-digit access key).
2. App sends `POST /api/receipts { qrCodeUrl | accessKey }`.
3. Backend validates the access key (mod-11 check digit, model 65).
4. If the user already imported it → return existing receipt (idempotent).
5. `NfceProviderRegistry` picks the provider by `cUF` (35 = SP).
6. Provider fetches the public page and parses store, items, totals, payment, date.
7. Consumer CPF is removed; raw HTML is stored for re-parsing.
8. Items are linked to `store_products`; an async job normalizes/categorizes them.

## Backend packages (modular monolith)

```
com.supermarketagent
├── receipt     # access key, providers (sp/...), import use case, REST
├── catalog     # stores, store products, canonical products, normalization
├── insight     # SQL-based analytics + AI text generation
├── ai          # AiClient abstraction + Gemini implementation
├── user        # accounts, auth, JWT
└── shared      # errors, config, utilities
```

## Data model

Managed by Flyway (`backend/src/main/resources/db/migration`). All ids are `BIGINT` identity.

```
users            (id, email UNIQUE case-insensitive, password_hash, created_at)
stores           (id, cnpj UNIQUE, name, address, state_code, created_at, updated_at)
products         (id, normalized_name [trigram index], brand, category,
                  measure_value, measure_unit, created_at)
store_products   (id, store_id → stores, store_code, description [trigram index], unit,
                  product_id → products NULL, UNIQUE(store_id, store_code))
receipts         (id, user_id → users, store_id → stores, access_key CHAR(44),
                  number, series, issued_at, total_amount, discount_amount,
                  approximate_taxes, source_url, raw_html, created_at,
                  UNIQUE(user_id, access_key))
receipt_items    (id, receipt_id → receipts, store_product_id → store_products,
                  line_number, quantity, unit, unit_price, total_price)
receipt_payments (id, receipt_id → receipts, method, amount)
```

- Money is `NUMERIC(12,2)`; quantities and unit prices are `NUMERIC(12,4)` (some items are
  priced with 3 decimals).
- `issued_at` is `TIMESTAMPTZ`. SEFAZ pages show São Paulo local time, which is converted on
  import; day-of-month analytics use the `America/Sao_Paulo` zone.
- Deleting a user cascades to their receipts, items and payments (LGPD right to erasure).
  Stores and products are shared reference data.

## NFC-e access key layout (44 digits)

| Pos | Len | Field                          |
|-----|-----|--------------------------------|
| 1   | 2   | cUF (state code, SP = 35)      |
| 3   | 4   | AAMM (year/month of issue)     |
| 7   | 14  | Issuer CNPJ                    |
| 21  | 2   | Model (65 = NFC-e, 55 = NF-e)  |
| 23  | 3   | Series                         |
| 26  | 9   | Number                         |
| 35  | 1   | Emission type                  |
| 36  | 8   | Numeric code                   |
| 44  | 1   | Check digit (mod 11)           |
