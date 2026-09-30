# Changelog

All notable changes to this project. Versions follow [SemVer](https://semver.org/);
the format follows [Keep a Changelog](https://keepachangelog.com/).

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
