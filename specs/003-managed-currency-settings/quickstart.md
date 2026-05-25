# Quickstart — Managed currency settings

## Strict cold start

With an empty `managed_currency` table, Portfolio Management intake is rejected until traders onboard currencies.

## Local demo

1. Start backend and Postgres.
2. Run `./scripts/seed-demo-orders.sh` — onboards EUR and USD via settings API, then posts demo orders.
3. Open the SPA → **Currencies** in the header to view or edit rules.

## Trader API

See [contracts/api-v1.md](./contracts/api-v1.md). All calls require `X-Trader-Id`.
