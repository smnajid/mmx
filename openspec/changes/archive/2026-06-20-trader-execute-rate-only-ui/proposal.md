## Why

Portfolio Management now locks the counterparty at order intake (`institutionCode` on `POST /api/v1/orders`). The backend and OpenAPI v1.7.0 already enforce **rate-only execute** — the trader submits only `executedRate` and the server uses the intake institution. The trader order-details execution form was never updated: it still presents an institution autocomplete and sends `institutionCode` on execute, forcing traders to re-select a counterparty PM already chose. Playground-submitted orders (e.g. QNB via `institutionCode: QNB-01`) expose this gap immediately.

## What Changes

- **Execution form — locked counterparty:** remove the institution picker from `OrderExecutionFormComponent`. Show the PM-chosen counterparty (and `institutionCode` as secondary mono label) read-only on ASSIGNED orders before execute.
- **Execution form — rate-only submit:** align `ExecuteOrderRequest` TypeScript model and `OrderApiService.executeOrder` body with OpenAPI (`executedRate` only).
- **Order details — intake counterparty visible:** show `counterparty` and `institutionCode` on the detail grid for non-executed orders (not only after EXECUTED).
- **Indicative rate proposal:** on opening the execute form, fetch the matching order-creation counterparties endpoint for the order's type/currency/tenor-or-notice/valueDate, locate the row for the intake `institutionCode`, and pre-fill `executedRate` with that rate. Display `rateDate` and an **Indicative** badge when `indicative: true` (same semantics as the PM wizard). The trader may edit the rate before submit.
- **Spec alignment:** update stale `trader-order-detail-actions` requirements (institution picker at execute) and relax term-rate UI specs that forbid any rate display on execute screens. Update `specs/002-trader-orders-views/spec.md` FR-003a to match rate-only execute.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- **`trader-order-detail-actions`**: replace institution-picker execute flow with locked intake counterparty, rate-only submit, and indicative rate pre-fill.
- **`term-rate-settings-ui`**: narrow the "no rates on order screens" rule so indicative rate proposal on execute is permitted.
- **`term-rate-daily-upload`**: same narrowing as term-rate-settings-ui for consistency.

## Impact

| Area | Notes |
|------|-------|
| `frontend/src/app/features/order-details/` | `order-execution-form`, `order-details` template, models, specs |
| `frontend/src/app/core/models/order.model.ts` | Add `institutionCode` to `OrderDetails`; remove `institutionCode` from `ExecuteOrderRequest` |
| `frontend/src/app/core/api/` | Optional thin service for order-creation counterparties lookup (or inline `HttpClient` in execution form) |
| `openspec/specs/trader-order-detail-actions/spec.md` | Delta merged on archive |
| `openspec/specs/term-rate-*` | Delta merged on archive |
| `specs/002-trader-orders-views/spec.md` | FR-003a prose fix (rate-only execute) |
| Backend / OpenAPI | **No changes** — consumes existing execute and order-creation endpoints |

## Out of Scope

- Backend execute logic changes (already rate-only).
- OpenAPI schema changes for execute (already correct).
- Institution catalog management or rate upload workflows.
- Auto-execute or changing minimum-rate validation rules.
- Cypress e2e for this delivery (Vitest component tests suffice).
