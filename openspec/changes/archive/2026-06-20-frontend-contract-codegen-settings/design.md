## Context

`frontend-contract-codegen` (archived) established `openapi-typescript` generation for the `002` contract and migrated `order.model.ts`. The tooling, git-ignore, and `pre*` hooks are in place. What remains is breadth: several services still hand-write types, including two that consume `002` endpoints already covered by the generated file, plus the three settings contracts that aren't generated at all.

Schema-name mapping verified against the contracts:

| Service | Hand-written | Generated schema | Contract |
|---|---|---|---|
| `order-creation-api.service.ts` | `CounterpartyRow`, `CounterpartiesResponse` | `CounterpartyOption`, `CounterpartiesResponse` | 002 |
| `oncall-rate-settings-api.service.ts` | `OnCallRateSegment`, `AddOnCallRateRequest`, `OnCallRateSegmentStatus` | `OnCallRateSegmentResponse`, `AddOnCallRateRequest`, `OnCallRateSegmentStatus` | 002 |
| `currency-settings-api.service.ts` | `ManagedCurrency`, `OnboardCurrencyRequest`, `UpdateCurrencyRulesRequest`, `TenorCode`, `NoticePeriodCode` | `ManagedCurrencyResponse`, `OnboardCurrencyRequest`, `UpdateCurrencyRulesRequest`, `TenorCode`, `NoticePeriodCode` | 003 |
| `institution-settings-api.service.ts` | `Institution`, `OnboardInstitutionRequest` | `InstitutionResponse`, `OnboardInstitutionRequest` | 004 |
| `term-rate-settings-api.service.ts` | `TermRate`, `TermRateUploadResult`, `TermRateTradingDay`, `TermRateRowError`, `TermRateIngestError` | `TermRateResponse`, `TermRateUploadResponse`, `TermRateTradingDayResponse`, `TermRateRowError`, `TermRateIngestErrorResponse` | 005 |

## Goals / Non-Goals

**Goals:**
- Generate types for `003`/`004`/`005` alongside `002`, one git-ignored file per contract.
- Migrate all five remaining services to generated types; remove hand-written duplicates of contracted schemas.
- Update the `frontend-contract-codegen` capability to assert full `002`–`005` coverage.

**Non-Goals:**
- No HTTP contract / backend / REST changes.
- No AsyncAPI/event-schema codegen; no CI drift-check (separate follow-ups).
- No replacement of hand-written `HttpClient` services with generated clients (types-only stays).

## Decisions

### D1: One generated file per contract, same pattern as `002`
Add `generate:api` sub-invocations producing `currency-settings.ts` (003), `institution-settings.ts` (004), `term-rate-settings.ts` (005) under the existing git-ignored `src/app/core/api/generated/`. Compose them under the single `generate:api` script (already wired to `pre*` hooks) so no new lifecycle wiring is needed.
- **Alternative**: a single merged file from multiple specs (e.g. redocly bundle). Rejected — per-contract files match the `002` precedent and keep provenance obvious.

### D2: Preserve app-side enums and friendly names via alias wrappers
Reuse the pilot's pattern: where the app keeps a TS enum (e.g. `TenorCode`, `OnCallRateSegmentStatus`) but the contract emits a string union, alias generated `components['schemas'][...]` and intersect/`Omit` only where a nominal enum is genuinely needed. Re-export friendly names (`ManagedCurrency` = `ManagedCurrencyResponse`, `Institution` = `InstitutionResponse`, `TermRate` = `TermRateResponse`) to minimise churn in importers.
- **Alternative**: rename all usages to the generated `*Response` names. Rejected — larger diff, no behavioural benefit.

### D3: Keep type definitions colocated with their service
The settings services already declare their own interfaces at the top of each service file. Replace those blocks in place with generated-type aliases rather than introducing new model files — smallest diff, matches current structure.

## Risks / Trade-offs

- **Field/optionality drift between hand-written and generated** (e.g. `validatedAt?` vs nullable, `CounterpartyRow.indicative`/`rateDate` vs `CounterpartyOption`) → reconcile per compiler errors during apply; the Vitest suite is the guardrail. If the contract genuinely lacks a field the UI uses, that is a contract gap to surface, not paper over.
- **Enum nominal-vs-structural mismatches** → handle with the same wrapper approach proven in the pilot; do not edit generated files.
- **Larger single change** (5 services) → mitigated by per-service task grouping so progress is checkpointed and a failure is isolated.

## Migration Plan

1. Add `003`/`004`/`005` generation to `generate:api`; run it.
2. Migrate services one contract at a time (002 stragglers → 003 → 004 → 005), reconciling types as the compiler surfaces them.
3. Run `npm run test` in `frontend/`; fix drift.
- **Rollback**: revert the service files and `package.json` script additions; generated dir is ephemeral.

## Open Questions

- Whether any hand-written field absent from a contract (e.g. counterparty `rateDate`/`indicative`) indicates a missing contract field. If so, that is out of scope here and should be raised as a contract change, with the UI keeping a local extension type in the interim.
