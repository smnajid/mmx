## 1. Frontend models and SDD prose

- [x] 1.1 Add `institutionCode: string | null` to `OrderDetails` in `frontend/src/app/core/models/order.model.ts`; change `ExecuteOrderRequest` to `{ executedRate: number }` only.
- [x] 1.2 Update `specs/002-trader-orders-views/spec.md` FR-003a and Session 2026-05-30 execute delta to rate-only execute (align with `api-v1.md` v1.7.0).

## 2. Order creation API client (red-first)

- [x] 2.1 **frontend** — Write failing `order-creation-api.service.spec.ts`: `listOnCallCounterparties` and `listTermCounterparties` call correct URLs and return typed rows. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/order-creation-api.service.spec.ts' --no-watch`
- [x] 2.2 **frontend** — Add `frontend/src/app/core/api/order-creation-api.service.ts` with counterparties methods until green.

## 3. Execution form refactor (red-first)

- [x] 3.1 **frontend** — Rewrite failing `order-execution-form.component.spec.ts`: no institution picker; shows locked counterparty from `order` input; pre-fills rate from counterparties mock; emits `{ executedRate }` only; shows Indicative badge when `indicative: true`; blocks rate below `minimumRate`. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/order-execution-form.component.spec.ts' --no-watch`
- [x] 3.2 **frontend** — Refactor `order-execution-form.component.ts`: accept `[order]` input, remove `InstitutionSettingsApiService`, integrate `OrderCreationApiService` indicative pre-fill, rate-only submit until green.

## 4. Order details integration (red-first)

- [x] 4.1 **frontend** — Update failing `order-details.component.spec.ts`: counterparty visible on ASSIGNED order; execution form receives `order` binding; execute calls API with rate-only body. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/order-details.component.spec.ts' --no-watch`
- [x] 4.2 **frontend** — Update `order-details.component.ts` template: show counterparty + institutionCode before execution; pass `[order]` to `mmx-order-execution-form` until green.
- [x] 4.3 **frontend** — Fix any other specs/mocks referencing `ExecuteOrderRequest.institutionCode` (grep `institutionCode` in `order-details/` and `order.model` consumers).

## 5. Final verification

- [x] 5.1 Run `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm run test` — green.
