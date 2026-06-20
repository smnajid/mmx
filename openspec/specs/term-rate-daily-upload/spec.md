# term-rate-daily-upload Specification

## Purpose
TBD - created by archiving change term-rate-daily-upload. Update Purpose after archive.
## Requirements
### Requirement: Upload Term rates from CSV

The system SHALL expose an authenticated trader operation to upload a UTF-8 comma-separated CSV file via `POST /api/v1/settings/term-rates/upload` (`multipart/form-data`, field `file`). The file MUST include a header row with case-sensitive columns: `tradingDate`, `institutionCode`, `currency`, `tenor`, `rate`. The system SHALL parse all data rows and validate them before persisting.

**Row grain (T-01):** Each data row SHALL represent one rate for the tuple `(tradingDate, institutionCode, currency, tenor)`.

**Trading day (T-03):** `tradingDate` MUST be ISO `YYYY-MM-DD` on every data row. All data rows in one upload MUST share the same `tradingDate`; mixed dates in one file MUST be rejected.

**Re-upload (T-02):** On successful upload for a given `tradingDate`, the system SHALL replace the entire persisted rate set for that date (delete all existing rows for that date, then insert the validated rows) in a single transaction.

**Atomicity:** If any structural or business validation fails, the system MUST NOT change persisted rates for that upload.

**Audit:** On successful persist, each row SHALL record `uploadedAt` (timestamp) and `uploadedBy` (trader id from the standard trader request header).

#### Scenario: Successful upload persists rates

- **WHEN** the trader uploads a valid CSV with three rows for `tradingDate` `2026-05-30` and no prior rates exist for that date
- **THEN** the system returns HTTP success with `tradingDate`, `rowCount` 3, and `uploadedAt`; `GET` for that date returns the three rates

#### Scenario: Re-upload replaces whole day

- **WHEN** rates already exist for `2026-05-30` and the trader uploads a new valid CSV for the same `tradingDate` with two rows
- **THEN** only the two new rows remain for `2026-05-30` and the prior rows are removed

#### Scenario: Reject mixed trading dates in one file

- **WHEN** the CSV contains data rows with `tradingDate` `2026-05-30` and `2026-05-31`
- **THEN** the system rejects the upload with HTTP 400 and does not change persisted rates

#### Scenario: Reject duplicate keys in file

- **WHEN** two data rows share the same `(tradingDate, institutionCode, currency, tenor)`
- **THEN** the system rejects the upload with HTTP 400 and does not change persisted rates

#### Scenario: Reject header-only or empty data

- **WHEN** the file has a valid header but no data rows
- **THEN** the system rejects the upload with HTTP 400 and does not change persisted rates

#### Scenario: Reject missing or unknown CSV columns

- **WHEN** the header omits `tenor` or uses non-standard column names
- **THEN** the system rejects the upload with HTTP 400 and a clear structural error

---

### Requirement: Validate Term rate rows against catalogs

For each data row, the system SHALL enforce:

- `institutionCode` references an **existing active** institution in the catalog.
- `currency` references an **existing active** managed currency (ISO 4217 uppercase).
- `tenor` is one of `1W`, `2W`, `1M`, `3M`, `6M`, `1Y` and is included in that currency’s **enabledTenors**.
- `rate` is a strictly positive decimal with at most eight fractional digits.

Unknown or inactive institutions, unknown or inactive currencies, disallowed tenors, non-positive rates, and invalid `tradingDate` formats MUST produce row-level validation errors.

#### Scenario: Reject unknown institution

- **WHEN** a row references `institutionCode` `NOBANK-01` that does not exist
- **THEN** the upload fails with HTTP 400, includes a row error identifying the line and field, and does not persist

#### Scenario: Reject inactive institution

- **WHEN** a row references an institution that exists but `active` is false
- **THEN** the upload fails with HTTP 400 and does not persist

#### Scenario: Reject tenor not enabled for currency

- **WHEN** EUR has `enabledTenors` `["1M","3M"]` only and a row has `currency` EUR and `tenor` `1W`
- **THEN** the upload fails with HTTP 400 and does not persist

#### Scenario: Reject non-positive rate

- **WHEN** a row has `rate` `0` or negative
- **THEN** the upload fails with HTTP 400 and does not persist

---

### Requirement: Query Term rates for a trading day

The system SHALL expose `GET /api/v1/settings/term-rates` with required query parameter `tradingDate` (`YYYY-MM-DD`). The response SHALL list all persisted rates for that date, each including `institutionCode`, `currency`, `tenor`, `rate`, `uploadedAt`, and `uploadedBy`.

#### Scenario: List rates for a day with data

- **WHEN** three rates exist for `2026-05-30` and the trader requests that `tradingDate`
- **THEN** the system returns HTTP 200 with three entries

#### Scenario: Empty day returns empty list

- **WHEN** no rates exist for `2026-05-30` and the trader requests that `tradingDate`
- **THEN** the system returns HTTP 200 with an empty array

---

### Requirement: List trading days with uploaded Term rates

The system SHALL expose `GET /api/v1/settings/term-rates/days` returning distinct `tradingDate` values that have at least one persisted rate, ordered newest first, to support the settings UI date picker.

#### Scenario: Days endpoint returns uploaded dates

- **WHEN** rates exist for `2026-05-29` and `2026-05-30` only
- **THEN** the response includes both dates and excludes dates with no rates

---

### Requirement: Download sample Term rate CSV

The system SHALL expose `GET /api/v1/settings/term-rates/sample` returning `Content-Type: text/csv` and `Content-Disposition: attachment; filename="term-rates-sample.csv"` (T-05).

The sample SHALL include the same header row as upload (`tradingDate`, `institutionCode`, `currency`, `tenor`, `rate`). It SHALL contain one example row per combination of **active** institution, **active** managed currency, and that currency’s **enabledTenors**, with placeholder `rate` and `tradingDate` set to the current calendar date in the desk timezone (default `Europe/Paris` unless configured otherwise).

#### Scenario: Sample includes header and catalog-driven rows

- **WHEN** `HSBC-01` is active, EUR is active with `enabledTenors` `["1M"]`, and the trader downloads the sample
- **THEN** the CSV contains the header and at least one row with `institutionCode` `HSBC-01`, `currency` EUR, `tenor` `1M`

#### Scenario: Sample omits inactive catalog entries

- **WHEN** institution `OLD-01` is inactive and EUR is active
- **THEN** the sample does not include rows for `OLD-01`

---

### Requirement: Term rates are trader-only reference

The system MUST NOT publish Term rate uploads to back-office messaging, external rate feeds, or order lifecycle handlers. Persisted rates are for trader desk reference and future capabilities only (T-04).

#### Scenario: Upload does not emit rate events

- **WHEN** a CSV upload succeeds
- **THEN** no Kafka (or equivalent) rate-handoff message is produced for that upload

---

### Requirement: Term rate settings UI under Settings hub

Term rate upload and day view SHALL live at `/settings/term-rates` within the Settings hub. The Settings sub-navigation SHALL include a **Term rates** entry (alongside Currencies and Institutions). Screens SHALL use the same return-to-desk and visual patterns as other settings flows (`DeskReturnService`, `settings-panel`, `settings-toolbar`, desk theme tokens per `currency-settings-ui` and `term-rate-settings-ui`).

The screen SHALL provide:

- **Download sample CSV** — invokes the sample `GET` and triggers a browser file download.
- **Upload** — styled file picker and submit to the upload endpoint; display success summary (`tradingDate`, `rowCount`) or row/structural errors from HTTP 400.
- **View day** — date control (default today), **quick-select for trading days returned by `GET /api/v1/settings/term-rates/days`**, day summary when rates exist, and table of rates from `GET` by `tradingDate`; actionable empty state when no upload exists for the selected date.
- **Replace-day guard** — when the selected day already has rates, require trader confirmation before upload (T-02).
- **Workflow presentation** — lede explaining morning sheet and whole-day replace; prepare / upload / review sections on elevated settings surfaces.

Term rate **management** UI MUST NOT appear on order detail screens. The trader execute form MAY display a **single proposed indicative rate** for the PM-chosen intake institution (via order-creation counterparties APIs) as an execution aid.

#### Scenario: Download sample from UI

- **WHEN** the trader clicks **Download sample CSV** on the Term rates screen
- **THEN** the browser saves a file named `term-rates-sample.csv` with the expected header row

#### Scenario: Upload success refreshes day view

- **WHEN** the trader uploads a valid CSV for `2026-05-30` and the view is set to that date
- **THEN** the UI shows the uploaded rates without requiring a full page reload

#### Scenario: Upload errors shown to trader

- **WHEN** the upload returns HTTP 400 with row errors
- **THEN** the UI displays line and message details and does not claim success

#### Scenario: Term rates tab in settings sub-nav

- **WHEN** the trader opens any `/settings/*` route
- **THEN** sub-navigation includes **Term rates** linking to `/settings/term-rates`

#### Scenario: Trading days quick-select

- **WHEN** `GET /api/v1/settings/term-rates/days` returns one or more dates
- **THEN** the UI offers quick-select for those dates and loads rates when one is chosen

#### Scenario: Replace-day confirmation

- **WHEN** the selected trading day already has rates and the trader initiates upload
- **THEN** the UI requires confirmation explaining whole-day replace before sending the upload request

### Requirement: Term rates REST is contract-first

HTTP paths, request bodies, response schemas, and error shapes for Term rate settings SHALL be defined only in `contracts/005-term-rate-settings/openapi.yaml` and prose mirror `api-v1.md`. Server controllers SHALL implement generated API interfaces; the Angular client SHALL call the published contract.

#### Scenario: Upload path matches OpenAPI

- **WHEN** integration tests call the upload operation
- **THEN** they use `POST /api/v1/settings/term-rates/upload` as defined in the canonical OpenAPI document

