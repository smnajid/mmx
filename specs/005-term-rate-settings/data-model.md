# Data model — Term rate settings

## Term rate (persisted)

Table: `term_rate`

| Field | Type | Notes |
|-------|------|-------|
| `trading_date` | DATE NOT NULL | Business date from CSV |
| `institution_code` | VARCHAR(32) NOT NULL | FK → `institution.institution_code` |
| `currency` | VARCHAR(3) NOT NULL | ISO 4217 |
| `tenor` | VARCHAR(10) NOT NULL | `1W`, `2W`, `1M`, `3M`, `6M`, `1Y` |
| `rate` | DECIMAL(12,8) NOT NULL | Strictly positive |
| `uploaded_at` | TIMESTAMPTZ NOT NULL | Set on upload |
| `uploaded_by` | VARCHAR(100) NOT NULL | Trader id from `X-Trader-Id` |

**Primary key:** `(trading_date, institution_code, currency, tenor)`

**Index:** `trading_date` for day queries and replace-day delete.

## Term rate (domain value object)

| Field | Type |
|-------|------|
| `tradingDate` | `LocalDate` |
| `institutionCode` | string |
| `currency` | string (ISO 4217) |
| `tenor` | `Tenor` enum |
| `rate` | `BigDecimal` |

## CSV ingest (not persisted)

Header columns (case-sensitive): `tradingDate`, `institutionCode`, `currency`, `tenor`, `rate`.

Validation references **Institution** and **ManagedCurrency** catalog snapshots (active flags, `enabledTenors`).
