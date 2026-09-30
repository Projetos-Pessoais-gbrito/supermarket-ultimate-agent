# ADR 0005 — AI provider: Google Gemini (free tier) behind an abstraction

- Status: accepted
- Date: 2026-09-29

## Context
We want natural-language insights and product categorization without paying for AI.

## Decision
- **Numbers come from SQL, not from the LLM.** Prices, savings, best day to buy, etc.
  are computed deterministically by the backend. The LLM only (a) categorizes and
  normalizes product names and (b) turns computed metrics into short, friendly
  recommendations in Portuguese.
- Use **Google Gemini (free tier, Flash model)** through an `AiClient` interface so we
  can switch to Groq or a local Ollama without touching business code.
- Never send personal data (CPF, name, e-mail) to the provider — only product
  descriptions and aggregated metrics.
- Cache results and respect free-tier rate limits with a queue and backoff.

## Consequences
The app keeps working (numbers and charts) if the AI provider is down or the quota runs
out; only the text recommendations degrade.
