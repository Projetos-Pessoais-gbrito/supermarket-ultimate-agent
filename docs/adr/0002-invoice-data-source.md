# ADR 0002 — Invoice data source: NFC-e QR code public consultation pages

- Status: accepted
- Date: 2026-09-29

## Context
Supermarket receipts in Brazil are **NFC-e** (model 65). We need the full purchase
data: store, items, quantities, prices, taxes, payment and date.

Options considered:

1. **SEFAZ SOAP web services** (`NfeConsultaProtocolo`, `NFeDistribuicaoDFe`).
   They require an ICP-Brasil digital certificate (A1/A3). `NfeConsultaProtocolo` only
   returns the authorization status, not the items. `NFeDistribuicaoDFe` only returns
   documents issued *to* the certificate owner and does not cover consumer NFC-e.
   **Not viable.**
2. **Paid aggregator APIs** (third-party NF-e/NFC-e query services). Viable, but they
   charge per query. Kept as a future fallback.
3. **Public consultation page from the NFC-e QR code.** Every NFC-e has a QR code
   with a URL to the issuing state's SEFAZ portal, e.g.
   `https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx?p=...`.
   The page is public, free, and lists every item.

## Decision
Use option 3. The mobile app scans the QR code and sends the URL (or the 44-digit
access key when typed manually) to the backend. The backend fetches the page and
parses it with a **state-specific parser** behind a common `NfceProvider` interface,
selected by the `cUF` code at the start of the access key.

First supported state: **São Paulo (SP, cUF 35)**.

## Consequences
- Each state needs its own parser plus HTML fixtures for tests. Layouts can change
  without notice, so parsers are covered by fixture tests and fail loudly.
- Some states protect the page with a captcha; those need option 2 or manual entry.
- We are polite to SEFAZ servers: rate limiting, caching by access key, a descriptive
  User-Agent, and never re-fetching a receipt that was already imported.
- The raw HTML is stored so receipts can be re-parsed after a parser bug is fixed.
