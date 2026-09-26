## Why

The `frontend-contract-codegen` pilot generated types only for `order.model.ts`, leaving most frontend API services still hand-writing their request/response shapes — including endpoints that already live in the generated `002` contract. To realise the convention ("frontend types are generated from the canonical contract, never hand-authored where a contract exists") the remaining services must be migrated and the settings contracts (`003`/`004`/`005`) must be wired into generation.

## What Changes

- **Finish `002` adoption** (no new tooling; the `002` types file already exists):
  - `order-creation-api.service.ts`: `CounterpartyRow`/`CounterpartiesResponse` → generated `CounterpartyOption`/`CounterpartiesResponse`.
  - `oncall-rate-settings-api.service.ts`: `OnCallRateSegment`/`AddOnCallRateRequest`/`OnCallRateSegmentStatus` → generated `OnCallRateSegmentResponse`/`AddOnCallRateRequest`/`OnCallRateSegmentStatus`.
- **Extend generation to `003`/`004`/`005`**: add three `generate:api` outputs (git-ignored), one generated file per contract.
- **Migrate the settings services** to generated types:
  - `currency-settings-api.service.ts` → `003` (`ManagedCurrencyResponse`, `OnboardCurrencyRequest`, `UpdateCurrencyRulesRequest`, `TenorCode`, `NoticePeriodCode`).
  - `institution-settings-api.service.ts` → `004` (`InstitutionResponse`, `OnboardInstitutionRequest`).
  - `term-rate-settings-api.service.ts` → `005` (`TermRateResponse`, `TermRateUploadResponse`, `TermRateTradingDayResponse`, `TermRateRowError`, `TermRateIngestErrorResponse`, `TenorCode`).
- This is a **non-material / internal** change: no HTTP contract, backend, or REST surface change. Contracts are consumed read-only.

## Capabilities

### Modified Capabilities
- `frontend-contract-codegen`: broaden the pilot requirement from "`002` is the pilot scope" to "all current contracts (`002`–`005`) are generated and consumed; no hand-authored duplicate of any contracted schema remains".

## Impact

- **Frontend build/tooling**: `frontend/package.json` (`generate:api` runs `openapi-typescript` for `003`/`004`/`005` in addition to `002`), `frontend/.gitignore` (generated dir already ignored).
- **Frontend code**: `order-creation-api.service.ts`, `oncall-rate-settings-api.service.ts`, `currency-settings-api.service.ts`, `institution-settings-api.service.ts`, `term-rate-settings-api.service.ts`, and any components/specs importing their hand-written types. Vitest suite must stay green.
- **Contracts**: `002`–`005` consumed read-only; unchanged.
- **No backend impact**; no Flyway, domain, or REST changes.
- **AsyncAPI/event-schema codegen and a CI drift-check remain out of scope** (candidate follow-ups).
