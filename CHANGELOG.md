# Changelog

All notable changes to this project. Versions follow [SemVer](https://semver.org/);
the format follows [Keep a Changelog](https://keepachangelog.com/).

## [1.1.0] - 2026-10-01

Plan your shopping, import receipts more ways, and install the app outside Expo Go.

### Added

- **Publishing**: Docker image for the backend, Render Blueprint (API + PostgreSQL 16), EAS
  profiles for an installable Android APK, and a guide to move local data
  ([docs/DEPLOY.md](docs/DEPLOY.md)).
- **Shopping list and price alerts**: products due to be bought again, from your own buying
  rhythm, with the cheapest store; an editable list; alerts when a price rose or dropped 10% or more.
- **Monthly budget**: an overall limit and per-category limits, with progress, a month-end
  projection and warnings on the dashboard.
- **Search and filters in Notas**: by store, month and more.
- **Import from gallery photos**: pick up to 20 receipt photos and see how many were imported.
- **Key-only SEFAZ links**: solve the SEFAZ captcha inside the app and the receipt is imported.
- **Dashboard periods**: 3, 6, 12 months or the whole history.
- **Products**: friendly names written by AI and a price history screen per product.
- **Delete a receipt**.
- **Dark mode** following the phone's setting.
- **Login protection**: rate limits on login and sign-up (ADR 0006).

### Changed

- Savings compare each purchase with the best price within 60 days of it.
- The monthly comparison uses the same days of the previous month.
- AI tips are more concrete and are kept until your data changes.
- "Por categoria" and "Por mercado" show every row behind "Ver todas".

### Fixed

- Endless loading during imports: requests and SEFAZ fetches now time out.
- Phones could not reach the server when Expo picked a WSL/Docker address.
- Notas list layout, "already imported" message and connection error wording.
- Passwords over 72 bytes (with accents) crashed sign-up.

## [1.0.0] - 2026-09-30

Insights and dashboards: see where your money goes and how to spend less.

### Added

- **Dashboard (Início tab)**: spending this month vs last month, monthly chart, spending by
  category and by store, potential savings, best time to buy, personal inflation and AI tips.
- **Insights API** (`/api/insights/...`), all computed in SQL from the user's own receipts:
  - spending by month, store and category (São Paulo months);
  - price history per product;
  - potential savings and the cheapest store per product;
  - best period of the month and weekday to buy (each purchase compared with the same product's
    average price);
  - personal inflation of the user's basket (weighted like the IPCA);
  - products of a category, opened by tapping a category bar.
- **AI tips**: Gemini (free tier) writes 1-3 tips in Portuguese from facts the backend computed;
  cached until the data changes; hidden when the AI is unavailable.
- **Product catalog**: normalized names and package sizes, price per kg/L, the same product matched
  across stores (package size + trigram similarity), automatic categories with Gemini.
- **Store brand names**: SENDAS DISTRIBUIDORA S/A is shown as ASSAI (also Atacadão, Carrefour,
  Makro, Sonda, Tenda, Roldão); branches of a chain are combined.
- **Stay logged in**: rotating refresh tokens (30 days) with reuse detection; the app renews
  sessions automatically; "Sair" revokes the session on the server.
- **Conta tab**: export all your data as JSON (LGPD portability), delete your account with
  password confirmation (LGPD erasure), and **unlock with fingerprint / Face ID**.

### Fixed

- Clear message for SEFAZ links that only have the access key (captcha pages).
- Cached data is cleared on sign-out, so another person logging in on the same phone never sees it.

## [0.4.0] - 2026-09-30

First usable version: scan a São Paulo supermarket receipt and see it in the app.

### Added

- **Receipt import (SP)**: NFC-e access key validation (mod-11), QR code URL parsing
  (v1, v2 online/offline, short `/qrcode` links), SEFAZ-SP page download and parsing.
- **Safe SEFAZ access**: host allow-list on every request and redirect (SSRF protection),
  timeouts, retries, one request per second across all users.
- **Privacy (LGPD)**: buyer CPF/name/address removed from stored pages and QR URLs.
- **Database**: PostgreSQL 16 schema for users, stores, products, receipts, items and payments;
  imports are idempotent per user.
- **Accounts**: register, login and `GET /api/me` with BCrypt passwords and 1-hour JWT access tokens.
- **Receipt API**: `POST /api/receipts`, `GET /api/receipts`, `GET /api/receipts/{id}`,
  scoped to the owner, with RFC 9457 error responses.
- **Mobile app (Expo SDK 57)**: login and sign-up, secure session storage, QR scanner with
  paste-link fallback, receipt list and details in pt-BR.
- **Tooling**: git-flow and Conventional Commits guide, ADRs, Docker Compose, GitHub Actions CI
  for backend and mobile, phone testing guide.

### Fixed

- Scanning SP receipts failed because the printed short link redirects to the consultation page.
