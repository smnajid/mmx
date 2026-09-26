## 1. Feature specs and contracts (`specs/005-term-rate-settings/`)

- [x] 1.1 Create `specs/005-term-rate-settings/spec.md`, `data-model.md`, and `plan.md` from OpenSpec capability `term-rate-daily-upload` (archive delta or copy on merge)
- [x] 1.2 Author `specs/005-term-rate-settings/contracts/openapi.yaml`: `POST /upload` (multipart `file`), `GET` by `tradingDate`, `GET /days`, `GET /sample`; upload success body (`tradingDate`, `rowCount`, `uploadedAt`); row-level `400` error schema (`line`, `field`, `message`)
- [x] 1.3 Mirror Term rate settings REST in `specs/005-term-rate-settings/contracts/api-v1.md` (no drift from OpenAPI)
- [x] 1.4 Add OpenAPI codegen execution in `mmx-adapter-in-rest/pom.xml` for `005` (server + API interface, same pattern as `003`/`004`)
- [x] 1.5 Run codegen and `mvn compile` on reactor; confirm generated `TermRateSettingsApi` (or equivalent) compiles

## 2. Domain (`mmx-domain`) — TDD

- [x] 2.1 Add `TermRate` value object (tradingDate, institutionCode, currency, tenor, rate) and `Tenor` validation helper aligned with order tenor enum
- [x] 2.2 Add `TermRateIngestPolicy` validating row against institution/currency snapshots (active flags, enabled tenors, positive rate, max fractional digits)
- [x] 2.3 Write failing `TermRateIngestPolicyTest`: unknown/inactive institution, unknown/inactive currency, tenor not enabled, rate ≤ 0, invalid tenor code
- [x] 2.4 Implement policy until domain tests pass (green)

## 3. Application (`mmx-application`) — TDD

- [x] 3.1 Add `TermRateRepository` port (`replaceAllForDate`, `findByTradingDate`, `findDistinctTradingDates`)
- [x] 3.2 Add `TermRateCsvParser` (structural parse: header columns, ISO dates, single date per file, duplicate keys in batch, empty data)
- [x] 3.3 Write failing `TermRateCsvParserTest`: missing column, mixed dates, duplicate keys, header-only file, bad date format
- [x] 3.4 Implement parser until tests pass
- [x] 3.5 Add `SampleTermRateCsvGenerator` using `InstitutionRepository`, `ManagedCurrencyRepository`, `Clock`/`ZoneId` (default `Europe/Paris`)
- [x] 3.6 Write failing `SampleTermRateCsvGeneratorTest`: header row, active institution × active currency × enabled tenor rows, excludes inactive catalog entries
- [x] 3.7 Implement sample generator until tests pass
- [x] 3.8 Add inbound port `UploadTermRatesUseCase` and `UploadTermRatesService` (parse → load catalog snapshots → policy → replace-day transaction)
- [x] 3.9 Write failing `UploadTermRatesServiceTest`: successful first upload; re-upload replaces whole day; any row error rolls back (no partial persist)
- [x] 3.10 Implement upload service until tests pass
- [x] 3.11 Add query use cases: `ListTermRatesForDay`, `ListTermRateTradingDays` (thin services over repository)

## 4. Persistence (`mmx-adapter-out-persistence`)

- [x] 4.1 Add Flyway migration `term_rate` table per `design.md` (composite PK, index on `trading_date`, FK to `institution` if enforced in POC)
- [x] 4.2 Add JPA entity, Spring Data repository, `JpaTermRateRepository` implementing `TermRateRepository` (`deleteByTradingDate` + bulk insert in `@Transactional`)
- [x] 4.3 Write `JpaTermRateRepositoryTest`: replace-day semantics, list by date, distinct dates ordered newest first

## 5. Term rate settings REST (`mmx-adapter-in-rest`)

- [x] 5.1 Implement generated `TermRateSettingsApi` controller + mapper (multipart upload, CSV sample response `text/csv`, list/day GETs; no business rules in adapter)
- [x] 5.2 Map ingest failures to contract `400` with row errors in `GlobalExceptionHandler`
- [x] 5.3 Write `TermRateSettingsControllerTest` (or integration test): upload 200 + rowCount; re-upload replace; 400 mixed dates / duplicate keys / unknown institution; sample `Content-Type` and attachment filename
- [x] 5.4 Write integration test asserting **no** Kafka/rate-handoff publish on successful upload (T-04)
- [x] 5.5 Run `mvn test` on `mmx-application` and `mmx-adapter-in-rest` — green

## 6. Frontend settings hub delta (`frontend/`)

- [x] 6.1 Add **Term rates** tab to settings shell sub-nav; route `/settings/term-rates` under settings layout
- [x] 6.2 Write Vitest: sub-nav includes Term rates link; active state on `/settings/term-rates`

## 7. Frontend Term rate settings (`frontend/`)

- [x] 7.1 Add API client/service for `005` endpoints (multipart upload, list by date, list days, sample blob download)
- [x] 7.2 Add `features/term-rate-settings/` component: date picker (default today, optional days from `GET /days`), rates table, empty state
- [x] 7.3 Add **Download sample CSV** button → `GET /sample` → browser download `term-rates-sample.csv`
- [x] 7.4 Add upload control (file input + submit); show success (`tradingDate`, `rowCount`) or row errors from 400; refresh day view on success
- [x] 7.5 Reuse `DeskReturnService`, `settings-panel`, `settings-toolbar`, desk theme tokens (U-02)
- [x] 7.6 Write Vitest: sample download invoked; upload success refreshes table; upload errors displayed; no rates on order-detail component

## 8. Bootstrap, seeds, and docs

- [x] 8.1 Register `TermRateSettingsModuleConfiguration`, `TermRateRepository`, upload/query services, `SampleTermRateCsvGenerator` beans in `mmx-bootstrap`
- [x] 8.2 Extend REST test bootstrap to seed institutions + currencies for term-rate integration tests
- [x] 8.3 Update demo/quickstart script: download sample → edit rates → upload (after phase 1 catalog exists)
- [x] 8.4 Add `specs/005-term-rate-settings/quickstart.md` with CSV column reference and replace-day behaviour

## 9. Final verification

- [x] 9.1 Run full `mvn test` from `backend/`
- [x] 9.2 Run `npm run test` (or `ng test`) in `frontend/`
- [x] 9.3 Confirm `005` OpenAPI, `api-v1.md`, and runtime match (no contract drift); confirm order execute/intake unchanged (T-04)
