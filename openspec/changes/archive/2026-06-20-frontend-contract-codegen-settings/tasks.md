## 1. Extend generation to settings contracts

- [x] 1.1 Extend the `generate:api` script in `frontend/package.json` to also run `openapi-typescript` for `003-managed-currency-settings`, `004-institution-settings`, and `005-term-rate-settings` (one git-ignored output file per contract under `src/app/core/api/generated/`)
- [x] 1.2 Run `npm run generate:api` in `frontend/` and confirm all four generated files are produced

## 2. Finish 002 adoption

- [x] 2.1 Migrate `order-creation-api.service.ts` (`CounterpartyRow`, `CounterpartiesResponse`) to generated `002` `CounterpartyOption`/`CounterpartiesResponse`; reconcile any field differences
- [x] 2.2 Migrate `oncall-rate-settings-api.service.ts` (`OnCallRateSegment`, `AddOnCallRateRequest`, `OnCallRateSegmentStatus`) to generated `002` `OnCallRateSegmentResponse`/`AddOnCallRateRequest`/`OnCallRateSegmentStatus`

## 3. Migrate settings services

- [x] 3.1 Migrate `currency-settings-api.service.ts` to generated `003` types (`ManagedCurrencyResponse`, `OnboardCurrencyRequest`, `UpdateCurrencyRulesRequest`, `TenorCode`, `NoticePeriodCode`), preserving app-side enum names via aliases
- [x] 3.2 Migrate `institution-settings-api.service.ts` to generated `004` types (`InstitutionResponse`, `OnboardInstitutionRequest`)
- [x] 3.3 Migrate `term-rate-settings-api.service.ts` to generated `005` types (`TermRateResponse`, `TermRateUploadResponse`, `TermRateTradingDayResponse`, `TermRateRowError`, `TermRateIngestErrorResponse`, `TenorCode`)
- [x] 3.4 Reconcile name/shape/optionality differences surfaced by the compiler across all migrated services and their importing components/specs (adjust aliases; do not edit generated files)

## 4. Final verification

- [x] 4.1 Run `npm run test` in `frontend/` — green
- [x] 4.2 Run `openspec validate frontend-contract-codegen-settings` — passes
