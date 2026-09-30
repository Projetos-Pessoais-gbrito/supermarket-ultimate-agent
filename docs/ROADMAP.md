# Roadmap

Each task below maps to **one `feature/*` branch and one PR into `develop`**.
Epics end with a `release/*` and a tag on `main`.

Legend: ☐ todo · ◐ in progress · ☑ done

---

## Epic 0 — Foundation → `v0.1.0`

| # | Task | Branch |
|---|------|--------|
| 0.1 | ◐ Repo conventions, ADRs, roadmap, README | `feature/repo-foundation` |
| 0.2 | ☐ Docker Compose with PostgreSQL 16 + `.env.example` | `feature/local-infra` |
| 0.3 | ☐ Spring Boot 4 / Java 21 skeleton, health endpoint, Flyway, Testcontainers | `feature/backend-skeleton` |
| 0.4 | ☐ Expo + TypeScript skeleton, Expo Router, ESLint/Prettier | `feature/mobile-skeleton` |
| 0.5 | ☐ GitHub Actions CI (backend build/test, mobile lint/typecheck) | `feature/ci` |

## Epic 1 — Receipt ingestion (SP) → `v0.2.0`

| # | Task | Branch |
|---|------|--------|
| 1.1 | ☐ `AccessKey` value object: parse 44 digits (cUF, AAMM, CNPJ, model, series, number), mod-11 check digit | `feature/access-key` |
| 1.2 | ☐ Extract access key from QR code URL (v2 `p=` format) | `feature/qrcode-url-parser` |
| 1.3 | ☐ `NfceProvider` interface + registry by cUF | `feature/nfce-provider-registry` |
| 1.4 | ☐ SP provider: HTTP fetch (timeouts, retry, rate limit, User-Agent) | `feature/sp-nfce-fetch` |
| 1.5 | ☐ SP provider: jsoup HTML parser + real HTML fixtures in tests | `feature/sp-nfce-parser` |
| 1.6 | ☐ Flyway schema: stores, receipts, receipt_items, store_products, products | `feature/receipt-schema` |
| 1.7 | ☐ `POST /api/receipts` import use case (idempotent by access key + user) | `feature/receipt-import` |
| 1.8 | ☐ `GET /api/receipts`, `GET /api/receipts/{id}` | `feature/receipt-query` |
| 1.9 | ☐ Strip/hash consumer CPF before persisting (LGPD) | `feature/lgpd-cpf-masking` |

## Epic 2 — Users & auth → `v0.3.0`

| # | Task | Branch |
|---|------|--------|
| 2.1 | ☐ Users table, sign-up/login, BCrypt | `feature/user-accounts` |
| 2.2 | ☐ JWT access + refresh tokens, Spring Security config | `feature/jwt-auth` |
| 2.3 | ☐ Scope all receipt queries to the logged-in user | `feature/receipt-ownership` |
| 2.4 | ☐ Account deletion + data export (LGPD rights) | `feature/lgpd-account-rights` |

## Epic 3 — Mobile MVP → `v0.4.0`

| # | Task | Branch |
|---|------|--------|
| 3.1 | ☐ Auth screens + secure token storage (expo-secure-store) | `feature/mobile-auth` |
| 3.2 | ☐ QR scanner screen (expo-camera) + manual access key input | `feature/mobile-scanner` |
| 3.3 | ☐ Receipt list + receipt detail screens | `feature/mobile-receipts` |
| 3.4 | ☐ API client (TanStack Query) + error/offline states | `feature/mobile-api-client` |

## Epic 4 — Product catalog & normalization → `v0.5.0`

| # | Task | Branch |
|---|------|--------|
| 4.1 | ☐ Normalize descriptions (upper-case, accents, units: KG/G/L/ML/UN) | `feature/product-normalizer` |
| 4.2 | ☐ Match same product across stores (GTIN first, then `pg_trgm` similarity) | `feature/product-matching` |
| 4.3 | ☐ `AiClient` interface + Gemini implementation (rate limit, cache) | `feature/ai-client-gemini` |
| 4.4 | ☐ AI categorization (Hortifruti, Carnes, Laticínios, Limpeza, Bebidas, ...) as async job | `feature/ai-categorization` |
| 4.5 | ☐ Unit price calculation (R$/kg, R$/L) | `feature/unit-price` |

## Epic 5 — Insights & dashboards → `v1.0.0`

Computed in SQL; AI only writes the human-friendly text.

| # | Insight | Branch |
|---|---------|--------|
| 5.1 | ☐ Monthly spending + spending by category | `feature/insight-spending` |
| 5.2 | ☐ Price history per product (per store) | `feature/insight-price-history` |
| 5.3 | ☐ **Best day of the month / weekday to buy** (avg unit price by day bucket) | `feature/insight-best-day` |
| 5.4 | ☐ **Potential savings**: what you paid vs the lowest price you've seen (per store/date) | `feature/insight-savings` |
| 5.5 | ☐ Cheapest store per product / per basket | `feature/insight-cheapest-store` |
| 5.6 | ☐ Personal inflation index (your basket vs last month / IPCA) | `feature/insight-personal-inflation` |
| 5.7 | ☐ Weekly AI summary ("You spent 12% more on meat; Assaí was 8% cheaper on rice") | `feature/insight-ai-summary` |
| 5.8 | ☐ Mobile dashboards (charts, cards) | `feature/mobile-dashboards` |

## Backlog — Later ideas

- **Atacarejo break-even**: is the bulk price at Assaí/Atacadão worth it vs retail for your consumption?
- **Smart shopping list**: predict what you'll need based on purchase frequency.
- **Price alerts**: notify when a frequent product is above your average.
- **Budget goals** per month/category with push notifications.
- **Unusual purchase detection** (price spikes, duplicate items).
- **Promotion hunting**: products whose price dropped across stores recently.
- **Nutritional / health view** by category (sugary drinks, ultra-processed share).
- More states (RJ, MG, PR, ...) as new `NfceProvider`s.
- Web app (React Native Web or Next.js sharing TS types).
- Household sharing (family members sharing one dashboard).
