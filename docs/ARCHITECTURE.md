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

## Data model (first version)

```
users            (id, email, password_hash, created_at)
stores           (id, cnpj UNIQUE, name, trade_name, address, city, uf)
receipts         (id, user_id → users, store_id → stores, access_key CHAR(44),
                  issued_at, total_amount, discount_amount, payment_method,
                  source_url, raw_html, created_at,
                  UNIQUE(user_id, access_key))
store_products   (id, store_id → stores, store_code, description, gtin, ncm, unit,
                  product_id → products NULL, UNIQUE(store_id, store_code))
products         (id, normalized_name, brand, category, measure_value, measure_unit)
receipt_items    (id, receipt_id → receipts, store_product_id → store_products,
                  line_number, quantity, unit, unit_price, total_price)
```

Money is stored as `NUMERIC(12,2)`, quantities as `NUMERIC(12,4)`; timestamps in
`TIMESTAMPTZ` using the `America/Sao_Paulo` zone for day-of-month analytics.

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
