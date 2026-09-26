# Term Rate Settings API v1

Canonical OpenAPI: [openapi.yaml](./openapi.yaml). All trader operations require header `X-User-Id`.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/settings/term-rates/upload` | Upload morning CSV (`multipart/form-data`, field `file`) |
| GET | `/api/v1/settings/term-rates` | List rates for `?tradingDate=YYYY-MM-DD` (required) |
| GET | `/api/v1/settings/term-rates/days` | Distinct trading dates with data, newest first |
| GET | `/api/v1/settings/term-rates/sample` | Download sample CSV (`term-rates-sample.csv`) |

## CSV format

UTF-8, comma-separated, header required:

| Column | Format |
|--------|--------|
| `tradingDate` | `YYYY-MM-DD` — same value on every data row in one file |
| `institutionCode` | Active institution catalog code |
| `currency` | Active managed currency ISO code |
| `tenor` | `1W` \| `2W` \| `1M` \| `3M` \| `6M` \| `1Y` — must be enabled for currency |
| `rate` | Positive decimal, max 8 fractional digits |

**Re-upload:** a successful upload for a `tradingDate` **replaces** all persisted rates for that date.

## TermRateUploadResponse (200)

- `tradingDate` — date from CSV
- `rowCount` — number of data rows persisted
- `uploadedAt` — ISO-8601 timestamp

## TermRateResponse

- `tradingDate`, `institutionCode`, `currency`, `tenor`, `rate`
- `uploadedAt`, `uploadedBy` — audit from last upload affecting that row

## Errors (400)

`TermRateIngestErrorResponse`:

- `error` — `TERM_RATE_STRUCTURAL_ERROR` (file/header/parse) or `TERM_RATE_INGEST_ERROR` (row validation)
- `message` — summary
- `errors` — optional array of `{ line, field?, message }` for row-level failures

No persistence occurs on 400.

## Role-scoped semantics (per `delegated-institution-grants`)

No new endpoints and no breaking schema changes; role scoping is an authorisation layer on the
existing surface.

- **Trader on a TradingHub**: full read/write — uploads and lists the hub's term rates.
- **ClientRepresentative on a TradingClient**: Settings-only and **read-only**. `GET` operations
  return the connected hub's term rates for granted institutions (resolved in-process, ADR-0001).
  `POST /api/v1/settings/term-rates/upload` returns `403` — term rates are hub-owned.
