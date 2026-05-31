# Quickstart — Term rate settings (005)

## Prerequisites

- Phase 1 catalog: at least one **active institution** and **active managed currency** with enabled tenors.
- Backend running with Flyway `V12__term_rate.sql` applied.

## CSV columns

| Column | Format |
|--------|--------|
| `tradingDate` | `YYYY-MM-DD` (same on every row in one file) |
| `institutionCode` | Active institution code |
| `currency` | Active ISO 4217 code |
| `tenor` | `1W`, `2W`, `1M`, `3M`, `6M`, `1Y` (enabled for currency) |
| `rate` | Positive decimal, max 8 fractional digits |

## Replace whole day

A successful upload for a `tradingDate` **deletes** all existing rates for that date and inserts the new set. Re-upload is idempotent in outcome (last file wins).

## API (trader header `X-Trader-Id`)

| Method | Path |
|--------|------|
| GET | `/api/v1/settings/term-rates/sample` |
| POST | `/api/v1/settings/term-rates/upload` (`multipart/form-data`, field `file`) |
| GET | `/api/v1/settings/term-rates?tradingDate=YYYY-MM-DD` |
| GET | `/api/v1/settings/term-rates/days` |

## UI

Settings → **Term rates** (`/settings/term-rates`): download sample, upload CSV, view rates by trading day.

## Demo script

`scripts/seed-demo-orders.sh` seeds currencies and institutions, then optionally uploads a sample term-rate file when the backend is up.
