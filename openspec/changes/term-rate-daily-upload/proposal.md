## Why

Term dealing starts each day with institution-specific rates by currency. After institutions exist (phase 1), traders need to **upload a morning CSV** and persist that trading day’s Term rate sheet for reference (and later pricing workflows).

**Programme context:** Phase 2 of hybrid C — see [`../institution-onboarding-rates/PROGRAMME.md`](../institution-onboarding-rates/PROGRAMME.md). Product choices **T-01–T-05** are decided — see [`design.md`](design.md) and [`../institution-onboarding-rates/OPEN-DECISIONS.md`](../institution-onboarding-rates/OPEN-DECISIONS.md).

## What Changes

- **CSV upload** API and UI; trading day from explicit **`tradingDate`** column in the file (T-03).
- Parse and validate rows against **onboarded institutions** and **managed currencies** (active codes).
- Persist the day’s Term rate set; clear error reporting for malformed rows (row-level failures).
- Trader settings UI: date + file upload (and view/upload status for that day).

**Out of scope (this phase):**

- OnCall curve maintenance or Kafka rate events.
- Auto-applying rates to intake or execution.
- Back-office async ingest of Term CSV (T-04: trader-only reference).

No **BREAKING** order lifecycle changes.

## Capabilities

### New Capabilities

- `term-rate-daily-upload`: Morning CSV ingest and persistence of Term rates per **(institution, currency, tenor)** and trading day; sample CSV download.

### Modified Capabilities

- (none unless settings navigation delta is bundled here instead of phase 1 — prefer U-01 decision in programme tracker)

## Impact

- **Contracts**: Extend or add OpenAPI under institution/rates feature folder for upload + query day’s rates.
- **Backend**: `TermRate` / batch model; ingest use case; Flyway for rate tables scoped by business date.
- **Frontend**: Term rates upload screen (likely under settings).
- **Tests**: CSV parser unit tests; integration tests for validation matrix.
- **Dependencies**: **Requires** [`institution-onboarding-rates`](../institution-onboarding-rates/) phase 1 merged; managed currencies.

