## 1. Contract and feature-spec parity (SDD, contract-first)

- [x] 1.1 Extend `specs/002-trader-orders-views/contracts/openapi.yaml`: add `ACCOUNTED` to the `OrderStatus` enum; add nullable `counterparty: string` on `OrderSummaryResponse` (mark for omit-on-null serialization).
- [x] 1.2 In the same `openapi.yaml`: add operation `GET /api/v1/orders/term/executed` returning paginated `OrderSummaryResponse`, requiring `X-Trader-Id`.
- [x] 1.3 Add operation `GET /api/v1/orders/oncall/executed` (same shape).
- [x] 1.4 Add operation `POST /api/v1/back-office/orders/{orderId}/accounted` with `security: []` (override global), optional body `{ accountedAt?: string }`, responses `200`, `404` (`ORDER_NOT_FOUND`), `409` (`INVALID_STATUS_TRANSITION`).
- [x] 1.5 Mirror every change above into `specs/002-trader-orders-views/contracts/api-v1.md` (parameters, request/response examples, error codes, `ACCOUNTED` in the status enum line, counterparty omit-null note).
- [x] 1.6 Update `specs/002-trader-orders-views/spec.md`: add a User Story / acceptance section for the per-workspace Executed views with counterparty visible, and the back-office accounting callback that flips `EXECUTED → ACCOUNTED`.
- [x] 1.7 Run codegen: `mvn -pl backend/mmx-adapter-in-rest -am compile` (or the project's codegen task) and confirm new generated server interfaces appear for the three new operations and the updated `OrderSummaryResponse`.

## 2. Backend — `domain` (TDD)

- [x] 2.1 Failing test: extend `OrderStatusTest` with cases `EXECUTED → ACCOUNTED` allowed, `ACCOUNTED → *` rejected, and non-`EXECUTED → ACCOUNTED` rejected (RECEIVED, ASSIGNED, CANCELLED, REJECTED).
- [x] 2.2 Implement: add `OrderStatus.ACCOUNTED`; update `ALLOWED_TRANSITIONS` so `EXECUTED → {ACCOUNTED}` and `ACCOUNTED → {}`. Tests from 2.1 pass.
- [x] 2.3 Failing test: extend `MoneyMarketOrderTest` to cover `markAccounted(now)` flips status to `ACCOUNTED` and bumps `updatedAt`; rejects from any non-`EXECUTED` status with `InvalidStatusTransitionException`.
- [x] 2.4 Implement: add `MoneyMarketOrder.markAccounted(Instant now)` with null-check on `now` and the transition assertion.
- [x] 2.5 Run `mvn -pl backend/mmx-domain test` → green.

## 3. Backend — `application` (gateway rename + new use cases) (TDD)

- [x] 3.1 Refactor: rename `port/out` `DepositsGateway` → `BackOfficeGateway` (file + class + import paths). Signature unchanged. Update every reference in `mmx-application` and its tests.
- [x] 3.2 Add `port/in` interfaces `ListExecutedTermOrdersUseCase` and `ListExecutedOnCallOrdersUseCase` (single method returning `OrderPage` with `(int page, int size)`); make `OrderQueryService` implement both, delegating to the existing `listExecutedTermOrders` / `listExecutedOnCallOrders` methods.
- [x] 3.3 Failing test: `MarkOrderAccountedServiceTest` covers — order in `EXECUTED` becomes `ACCOUNTED` and audit `ORDER_ACCOUNTED` is logged once; order already `ACCOUNTED` is a no-op (no second save, no second audit); unknown `orderId` raises `OrderNotFoundException`; order in any other status raises `InvalidStatusTransitionException`.
- [x] 3.4 Implement: `MarkOrderAccountedUseCase` (port/in) + `MarkOrderAccountedService` that loads, applies status-based idempotency (already-`ACCOUNTED` → no-op), invokes `MoneyMarketOrder.markAccounted(clock.now())`, persists, audits.
- [x] 3.5 Run `mvn -pl backend/mmx-application test` → green.

## 4. Backend — outbound adapters (`adapter-out-*`)

- [x] 4.1 `mmx-adapter-out-persistence`: extend the JPA enum mapping for `OrderStatus` to recognize `ACCOUNTED`; round-trip an `ACCOUNTED` order in `JpaOrderRepositoryTest`.
- [x] 4.2 Inspect existing schema migrations for a CHECK constraint on the order-status column. If one exists, add a Flyway migration extending allowed values to include `ACCOUNTED`; if not, no migration is needed (enum-only change).
- [x] 4.3 `mmx-adapter-out-integration`: rename `NoOpDepositsGateway` → `NoOpBackOfficeGateway`; update its `implements` clause to `BackOfficeGateway`.
- [x] 4.4 Update `backend/mmx-adapter-out-integration/pom.xml` `<description>` to replace `NoOpDepositsGateway` with `NoOpBackOfficeGateway`.
- [ ] 4.5 (Optional) Add `LoggingBackOfficeGateway implements BackOfficeGateway` that logs WARN with `orderId` on every `notifyExecution`, useful for integration-test visibility. Bean wiring is decided in section 6.
- [x] 4.6 Run `mvn -pl backend/mmx-adapter-out-persistence,backend/mmx-adapter-out-integration test` → green.

## 5. Backend — `adapter-in-rest` (TDD, against generated interfaces)

- [x] 5.1 Failing test: extend `OrderRestApiIntegrationTest` — `GET /api/v1/orders/term/executed` returns only orders with `status=EXECUTED ∧ orderType=TERM`, each row carries `counterparty`; cross-workspace and non-EXECUTED orders are excluded.
- [x] 5.2 Failing test: same shape for `GET /api/v1/orders/oncall/executed`.
- [x] 5.3 Implement: controllers (or methods on the existing trader controller) implementing the generated API interfaces from 1.7, delegating to `ListExecuted{Term,OnCall}OrdersUseCase` and mapping to the updated `OrderSummaryResponse` (with `counterparty` populated from `ExecutionDetails`).
- [x] 5.4 Failing test: `POST /api/v1/back-office/orders/{orderId}/accounted` for an `EXECUTED` order returns `200`, persists `status=ACCOUNTED`, bumps `updatedAt`, emits exactly one `ORDER_ACCOUNTED` audit event.
- [x] 5.5 Failing tests for the callback: `404 ORDER_NOT_FOUND` on unknown id; `409 INVALID_STATUS_TRANSITION` for non-EXECUTED states; duplicate callback returns `200`, leaves `updatedAt` from the first call unchanged, and does not emit a second audit event; empty JSON body `{}` is accepted.
- [x] 5.6 Failing test: the callback endpoint is reachable **without** any authentication header (no `X-Trader-Id`), confirming `security: []`.
- [x] 5.7 Implement: `BackOfficeAccountingCallbackController` against the generated interface, delegating to `MarkOrderAccountedUseCase`. Map `OrderNotFoundException → 404` and `InvalidStatusTransitionException → 409` per existing error-mapping conventions.
- [x] 5.8 Failing test: when `BackOfficeGateway.notifyExecution(...)` throws, the Trader's `POST /api/v1/orders/{orderId}/execute` still returns `200`, the order is persisted as `EXECUTED`, and the gateway exception does not surface to the caller (assert via WARN log capture or audit observation).
- [x] 5.9 Failing test: on a successful execute, `BackOfficeGateway.notifyExecution(saved)` is invoked **exactly once** AFTER the `@Transactional execute(...)` has returned (assert by querying the order inside a spy gateway and observing `status=EXECUTED`).
- [x] 5.10 Implement: in the execute-flow caller (the controller, not inside `ExecuteOrderService`), invoke `backOfficeGateway.notifyExecution(saved)` after the use case returns; wrap the call in `try/catch (Exception ex)`, log WARN with `orderId`, swallow.
- [x] 5.11 Run `mvn -pl backend/mmx-adapter-in-rest test` → green.

## 6. Backend — `bootstrap`

- [x] 6.1 Update `OrderModuleConfiguration`: rename bean factory `depositsGateway()` → `backOfficeGateway()`, return type `BackOfficeGateway`, import the renamed adapter (`NoOpBackOfficeGateway` or `LoggingBackOfficeGateway` per environment).
- [x] 6.2 Run `mvn -pl backend/mmx-bootstrap test` → green (covers `TraderWorkflowE2ETest` and `OrderRestApiIntegrationTest`).
- [x] 6.3 Run `mvn test` from repo root → all green.

## 7. Frontend — Angular

- [x] 7.1 Append `ACCOUNTED = 'ACCOUNTED'` to `frontend/src/app/core/models/order-status.enum.ts`.
- [x] 7.2 Add a `[data-status='ACCOUNTED']` style block in `frontend/src/app/shared/components/status-badge.component.ts` with a distinct color (suggest a desaturated green or muted neutral, distinct from `EXECUTED`'s vivid green).
- [x] 7.3 Audit any `switch`/`match` over `OrderStatus` in `frontend/src/app/` and add an `ACCOUNTED` branch where exhaustiveness matters (keep behaviour neutral if no case applies).
- [x] 7.4 Add API client methods `listTermExecutedOrders(...)` and `listOnCallExecutedOrders(...)` to `frontend/src/app/core/api/order-api.service.ts`, reusing existing pagination conventions.
- [x] 7.5 Create `term-executed-order-list` standalone component under `frontend/src/app/features/term-orders/` (mirror the existing `term-order-list.component.ts` pattern) with a `counterparty` column.
- [x] 7.6 Create `oncall-executed-order-list` standalone component under `frontend/src/app/features/oncall-orders/` (mirror `oncall-order-list.component.ts`) with the same column.
- [x] 7.7 Wire the new components into the existing routing so they're reachable from the Term and OnCall workspace pages (no tab restructuring per Out of Scope; minimal navigation entry point only).
- [x] 7.8 Vitest unit tests: `term-executed-order-list.component.spec.ts` and `oncall-executed-order-list.component.spec.ts` covering successful render with counterparty; `order-api.service.spec.ts` updates for the two new methods.
- [x] 7.9 Cypress e2e: extend `frontend/cypress/e2e/trader-workflow.cy.ts` (or add a new spec) — execute a Term order, assert it appears in the Term Executed list with counterparty; trigger the back-office callback (via `cy.request('POST', '/api/v1/back-office/orders/<orderId>/accounted', {})`); assert the row leaves the list on next load.
- [ ] 7.10 Run `npm run test` in `frontend/` → green; run Cypress e2e → green.

## 8. SDD parity (Principle VI) — feature 001 docs

- [x] 8.1 In `specs/001-mm-order-processing/plan.md`, replace descriptive references to `DepositsGateway` / `NoOpDepositsGateway` with `BackOfficeGateway` / `NoOpBackOfficeGateway` at lines 142, 189, 889-890, 938.
- [x] 8.2 In `specs/001-mm-order-processing/tasks.md`, **do not rewrite** the historical `[x]` entries on lines 68 (T021) and 86 (T033). Add a brief footnote at the top of the file noting that the rename `DepositsGateway → BackOfficeGateway` was delivered under change `executed-orders-accounting` and link to it.
- [x] 8.3 Verify `specs/002-trader-orders-views/contracts/openapi.yaml` and `api-v1.md` already match the implementation (covered by 1.1-1.5; this task is the final cross-check before opening the PR).

## 9. OpenSpec closure

- [x] 9.1 `openspec validate executed-orders-accounting` passes.
- [ ] 9.2 Manual smoke: run `scripts/seed-demo-orders.sh`, execute one Term order, verify it appears in `GET /api/v1/orders/term/executed` with counterparty, `POST` to `/api/v1/back-office/orders/{orderId}/accounted`, verify the order disappears from the Executed list and reads as `ACCOUNTED` via `GET /api/v1/orders/{orderId}`.
- [ ] 9.3 Self-review against `proposal.md` Out-of-Scope bullets — confirm no outbox/drainer was added, no `contractNumber` per-`OrderOperation` fix slipped in, no authentication was added to the BO callback, the tab/sub-tab restructure was not touched, and no cross-session preference store was introduced.
- [ ] 9.4 Open the PR and link to this change directory; `openspec archive executed-orders-accounting` once merged.
