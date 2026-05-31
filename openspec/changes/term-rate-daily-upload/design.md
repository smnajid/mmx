## Context

Phase 2 of the institution & rates programme ([`../institution-onboarding-rates/PROGRAMME.md`](../institution-onboarding-rates/PROGRAMME.md)). Phase 1 delivers the **institution catalog** and **managed currencies** with per-currency **enabled tenors** (`1W` … `1Y`). Traders produce a **morning Term rate sheet** each trading day: institution-specific rates across currencies and tenors.

This change adds **CSV ingest + persistence** for reference on the desk. It does **not** wire rates into execute, intake, or back-office feeds.

**Programme decisions (locked):** T-01–T-05 resolved in this design; see §2.

**Depends on:** phase 1 merged (`institution` table, settings hub under `/settings/*`, active institution + currency catalogs).

## Goals / Non-Goals

**Goals:**

- Parse morning CSV with **explicit `tradingDate` per row** (T-03); persist rates at grain **(institution, currency, tenor)** (T-01).
- **Replace whole trading day** on re-upload for the same date (T-02).
- Validate rows against **active** institutions, **active** managed currencies, and currency **enabled tenors**.
- Row-level error reporting; successful rows for a date commit atomically per date (all-or-nothing per `tradingDate` in one upload).
- Trader **Settings → Term rates** UI: upload file, view day status/summary, **download sample CSV** (T-05).
- **Trader-only reference** — no Kafka/async BO handoff (T-04).
- Contract-first REST under a new feature spec folder; hexagonal modules; TDD.

**Non-Goals:**

- Applying Term rates to order pricing, execute validation, or minimum-rate checks.
- OnCall curve maintenance, segment history, or rate events.
- Back-office async ingest of the same CSV (T-04).
- Versioned batch history beyond “last upload wins” for a day.
- Multi-date files (one logical trading day per upload).

## Decisions

### 1. Programme decisions T-01–T-05 (locked)

| ID | Decision | Implementation |
|----|----------|----------------|
| **T-01** | Rate grain = **(institutionCode, currency, tenor)** | One numeric rate per triple per `tradingDate`; PK `(trading_date, institution_code, currency, tenor)` |
| **T-02** | Same-day re-upload **replaces whole day** | Transaction: `DELETE` all `term_rate` for parsed `tradingDate`, then `INSERT` validated rows |
| **T-03** | **Explicit date column** in CSV | Required header `tradingDate` (`YYYY-MM-DD`); all data rows MUST share the same date; reject mixed dates in one file |
| **T-04** | Morning CSV = **trader-only reference** | No adapter-out messaging; no BO API; documented in spec as desk reference only |
| **T-05** | **Sample CSV** | Server-generated template from current catalog; `GET` returns `text/csv`; UI **“Download sample CSV”** button on Term rates screen |

Record in [`../institution-onboarding-rates/OPEN-DECISIONS.md`](../institution-onboarding-rates/OPEN-DECISIONS.md) when specs are written (status → **decided**).

### 2. CSV format

**Encoding:** UTF-8. **Delimiter:** comma. **Header row required** (case-sensitive names below).

| Column | Required | Format | Notes |
|--------|----------|--------|-------|
| `tradingDate` | Yes | `YYYY-MM-DD` | Single date per file; must match on every row |
| `institutionCode` | Yes | string | Must exist; **active** |
| `currency` | Yes | ISO 4217 uppercase | Must exist in managed catalog; **active** |
| `tenor` | Yes | `1W` \| `2W` \| `1M` \| `3M` \| `6M` \| `1Y` | Must be in that currency’s `enabledTenors` |
| `rate` | Yes | decimal | Strictly positive; max 8 fractional digits (align with `DECIMAL(12,8)`) |

**Example (illustrative):**

```csv
tradingDate,institutionCode,currency,tenor,rate
2026-05-30,HSBC-01,EUR,1M,3.25000000
2026-05-30,HSBC-01,EUR,3M,3.41000000
2026-05-30,BCI-01,USD,1W,4.12000000
```

**Duplicate keys:** Two rows with the same `(tradingDate, institutionCode, currency, tenor)` in one file → **file-level validation error** (no partial apply).

**Empty file / header only:** Reject with clear error (does not clear an existing day unless a dedicated “clear day” is added later — out of scope).

### 3. Sample CSV (T-05)

**Generator** (`SampleTermRateCsvGenerator` in `mmx-application`):

- Inputs: active institutions, active managed currencies, each currency’s `enabledTenors`.
- Emit one **example row per (institution, currency, tenor)** combination (or cap with a documented max for POC if Cartesian product is huge — prefer full product for POC catalog sizes).
- Placeholder `rate` (e.g. `0.00000001` or `1.00000000`) and `tradingDate` = **today** in desk timezone (inject `Clock` / `ZoneId` from bootstrap, default `Europe/Paris` unless product config exists).
- Include header row matching §2.

**Delivery:**

- `GET /api/v1/settings/term-rates/sample` → `Content-Type: text/csv`, `Content-Disposition: attachment; filename="term-rates-sample.csv"`.
- Frontend: primary button or link beside upload control; no auth beyond existing trader header pattern.

### 4. Validation architecture

| Piece | Module | Role |
|-------|--------|------|
| `TermRateCsvParser` | `mmx-application` (or `mmx-domain` if kept pure) | Parse bytes → `List<TermRateRow>` + structural errors (missing columns, bad dates) |
| `TermRateIngestPolicy` | `mmx-domain` | Row rules: known institution (active), currency (active), tenor allowed, rate > 0, duplicate key in batch |
| `UploadTermRatesService` | `mmx-application` | Orchestrate parse → policy → replace-day persist |
| `TermRateRepository` | `mmx-application` `port.out` | `replaceAllForDate(LocalDate, List<TermRate>)`, `findByTradingDate`, optional `existsForDate` |
| `InstitutionRepository` / `ManagedCurrencyRepository` | existing ports | Snapshots for validation and sample generator |

**Upload semantics (T-02):** For the file’s single `tradingDate`, one transaction: delete existing rows for that date, insert all validated rows. Re-upload is idempotent in outcome (last file wins).

**Failure modes:**

- Structural/parse errors → `400` with summary (no DB change).
- Row-level business errors → `400` with `errors[]` `{ line, field, message }` (no DB change).
- Success → `200` or `201` with `{ tradingDate, rowCount, uploadedAt }` (exact shape in OpenAPI).

### 5. HTTP surfaces (contract-first)

**New feature folder:** `specs/005-term-rate-settings/` (mirror `003` / `004` layout: `contracts/openapi.yaml`, `api-v1.md`, `spec.md`, `data-model.md`).

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/v1/settings/term-rates/upload` | `multipart/form-data`, field `file` (`text/csv`) |
| `GET` | `/api/v1/settings/term-rates` | Query `tradingDate` (required) — list rates for that day |
| `GET` | `/api/v1/settings/term-rates/days` | Optional: list dates that have uploads (for UI date picker) |
| `GET` | `/api/v1/settings/term-rates/sample` | Download sample CSV (T-05) |

**Codegen:** Add OpenAPI module or extend bootstrap codegen config; implement in `mmx-adapter-in-rest` against generated API interface (`TermRateSettingsApi` or similar).

**Trader header:** Reuse `X-Trader-Id` (or project-standard header) on upload; persist `uploaded_by` on rows.

### 6. Persistence

```text
term_rate
  trading_date       DATE          NOT NULL
  institution_code   VARCHAR(32)   NOT NULL  -- FK → institution.institution_code
  currency           VARCHAR(3)    NOT NULL
  tenor              VARCHAR(10)   NOT NULL
  rate               DECIMAL(12,8) NOT NULL
  uploaded_at        TIMESTAMPTZ   NOT NULL
  uploaded_by        VARCHAR(100)  NOT NULL
  PRIMARY KEY (trading_date, institution_code, currency, tenor)
```

Index on `trading_date` for day queries. Flyway migration in `mmx-adapter-out-persistence`.

**Domain object:** `TermRate` value object in `mmx-domain` (date, institutionCode, currency, tenor, rate) — no JPA in domain.

### 7. Frontend (Angular)

**Route:** `/settings/term-rates` (child of settings shell per U-01).

**Screen (`term-rate-settings/`):**

- Sub-nav tab **Term rates** enabled in settings shell (phase 2).
- **Download sample CSV** — calls sample `GET`, triggers browser download (T-05).
- **Upload** — file input + submit → `POST` upload; show spinner and result.
- **View day** — date control (defaults to today); `GET` rates for selected `tradingDate`; empty state when no upload yet.
- Reuse **U-02** pattern: `DeskReturnService`, `settings-panel`, `settings-toolbar`, `settings-back`.

**No** desk integration (rates not shown on order detail in this phase).

### 8. Module placement & tests (TDD)

| Layer | Tests |
|-------|--------|
| `TermRateCsvParserTest` | Header missing, bad date, mixed dates, duplicates |
| `TermRateIngestPolicyTest` | Unknown institution, inactive, unknown currency, tenor not enabled, rate ≤ 0 |
| `UploadTermRatesServiceTest` | Replace-day: second upload overwrites; rollback on row error |
| `SampleTermRateCsvGeneratorTest` | Respects enabled tenors; includes header |
| REST integration | Multipart upload 400/200; sample content-type; GET by date |
| Vitest | Sample download click; upload error display; list after upload |

Red-first order in `tasks.md`: parser/policy unit tests → service → Flyway → REST → UI.

### 9. OpenSpec capability

| Capability | Location (this change) |
|------------|-------------------------|
| `term-rate-daily-upload` | `openspec/changes/term-rate-daily-upload/specs/term-rate-daily-upload/spec.md` (next artifact) |

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Large Cartesian sample CSV | POC catalog size is small; document max rows if generator capped |
| Trader uploads wrong date in column | UI shows parsed date in confirmation; spec stresses checking `tradingDate` |
| Replace-day deletes prior work accidentally | Confirm in UI before upload if day already has data (optional enhancement) |
| No rate history | Accepted (T-02); audit only via `uploaded_at` / `uploaded_by` on current rows |
| Multipart not used elsewhere | Single upload endpoint; follow Spring `MultipartFile` pattern in REST adapter |

## Migration Plan

1. Deploy after phase 1 (`institution`, settings hub).
2. Flyway `term_rate` table.
3. Ship REST + UI together; no data backfill required.
4. Demo/quickstart: onboard institutions + currencies, download sample, fill rates, upload.

## Open Questions

- **Desk timezone** for sample `tradingDate` default — confirm `Europe/Paris` or inject from config.
- **Upload response code** — prefer `200` with body vs `201` (align with other settings mutations in OpenAPI).
- **GET /days** — include in v1 if UI needs history picker; otherwise UI uses manual date entry only.
