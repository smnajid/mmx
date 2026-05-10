## Context

mmx today treats `OrderStatus.EXECUTED` as terminal. Once a Trader executes an order, it leaves every list view, even though the deal has not yet been booked downstream by the back-office (Deposits) system. Two facts about the current codebase shape this change:

- A no-op outbound port (`DepositsGateway`) was scaffolded in feature 001 with the explicit comment _"Notify the downstream Deposits system after execution. No-op in V1."_ — and it is **never invoked** by `ExecuteOrderService`. The skeleton was waiting for this work.
- The Executed list query logic already exists as orphan methods on `OrderQueryService` (`listExecutedTermOrders`, `listExecutedOnCallOrders`). What is missing is the `port/in` interfaces, the REST surface, and the counterparty on the response shape.

This change adds an `ACCOUNTED` terminal state, a synchronous best-effort outbound transmission to the back-office on `EXECUTED`, an inbound callback that flips `EXECUTED → ACCOUNTED`, and a per-workspace Trader screen of orders currently in `EXECUTED`. Reliable delivery (transactional outbox) and the related `contractNumber`-per-subscription bug are explicitly **deferred** — see `proposal.md` "Out of scope".

## Goals / Non-Goals

**Goals:**

- A new terminal `OrderStatus.ACCOUNTED` driven by an inbound back-office callback.
- A best-effort synchronous outbound transmission that fires after `EXECUTED` is committed.
- An idempotent inbound callback handler addressed by `orderId`.
- A per-workspace (Term / OnCall) Executed list with `counterparty` visible.
- Rename of the existing outbound port `DepositsGateway` → `BackOfficeGateway` for ubiquitous-language alignment, with feature-001 SDD references updated in the same delivery.
- TDD-first delivery of all behavioural changes; contract-first OpenAPI for every new HTTP surface.

**Non-Goals:**

- Reliable outbound transmission (transactional outbox, retries, durable queue) — deferred to a separate change.
- Fixing the `contractNumber` generation bug (mints a fresh number on every `OrderOperation` instead of inheriting on INCREASE / DECREASE / REDEMPTION) — deferred to a separate change.
- Authentication on the inbound back-office callback — POC accepts an unauthenticated endpoint; production hardening is a known follow-up.
- The broader Term / OnCall **tab + Received / Assigned / Executed sub-tab** UI restructure described in `scratch.txt`.
- Cross-session persistence of Trader filter preferences for the Executed view.
- Storing the back-office's own `contractId` on the mmx side (mmx does not need it; routing is by mmx `orderId`).
- Real-time push of accounted updates to the frontend (auto-refresh on next load is sufficient).

## Decisions

### D1. Status lifecycle: purism, not a TRANSMITTED substate

`EXECUTED → ACCOUNTED` is the only new transition; `ACCOUNTED` is terminal. Transmission to the back-office is purely an integration concern and is **not** modelled in `OrderStatus`.

```
RECEIVED ─▶ ASSIGNED ─▶ EXECUTED ─▶ ACCOUNTED   ◀── new terminal
                            ▲
                            └── EXECUTED is no longer terminal
```

**Alternatives considered.** A `TRANSMITTED` substate between `EXECUTED` and `ACCOUNTED` was discussed. Rejected: it leaks an integration step into the domain, adds another transition row to `ALLOWED_TRANSITIONS`, and gives Traders no operational signal they cannot already get from the new Executed list. The Trader's mental model is "executed but not yet accounted" — a single observable state.

### D2. Synchronous transmission, placed after the @Transactional commit

`backOfficeGateway.notifyExecution(saved)` is a **synchronous** call invoked **after** `ExecuteOrderService.execute(...)` returns and its transaction has committed.

```
                EXECUTE-ORDER  @Transactional  (single tx)
                ┌──────────────────────────────────────────┐
   Trader ───▶  │  orders.status = EXECUTED                │
                │  orders.execution_details = (...)        │
                └─────────────────┬────────────────────────┘
                                  │ COMMIT
                                  ▼
                ┌──────────────────────────────────────────┐
                │  backOfficeGateway.notifyExecution(saved)│  best-effort sync
                │  — failure logged + swallowed            │
                └──────────────────────────────────────────┘
```

**Placement.** The orchestrating **caller** (controller / facade in `mmx-adapter-in-rest`) invokes the use case and then the gateway, *not* a `@TransactionalEventListener(AFTER_COMMIT)`. Rationale: explicit ordering is easier to read in tests and PRs than implicit Spring event magic, and feature 001 already uses caller-orchestrated wiring elsewhere. The use case itself does **not** depend on the gateway, preserving its purity.

**Why post-commit, not inside the transaction.** If the call were inside the `@Transactional` boundary, the back-office could process the transmission and even fire the accounted callback before mmx had committed `EXECUTED`, breaking the inbound handler's `assert status == EXECUTED` invariant. Post-commit makes the ordering invariant a property of the code, not the network.

**Alternatives considered.**

- _Outbox + drainer (async, durable)._ Strictly better for production. Deferred per Non-Goal — adds a Flyway migration, a poller, and reattempt semantics this POC does not need.
- _Sync call inside the transaction._ Rejected — see "Why post-commit" above.
- _Roll back the execute on transmission failure._ Rejected — couples Trader-facing execution to back-office availability; a Trader cannot complete a deal if Deposits is down.
- _@TransactionalEventListener(AFTER_COMMIT)._ Functionally correct alternative; rejected for explicitness.

### D3. Best-effort failure semantic on the sync transmission

A failed `backOfficeGateway.notifyExecution(...)` call is **logged** (WARN with `orderId` and exception) and **swallowed**. The Trader's HTTP response is **200**. The order remains `EXECUTED` indefinitely until either (a) the back-office is independently nudged and sends an accounted callback, or (b) operator intervention.

**This is a known POC compromise.** When the outbox change lands, every executed order will be eventually transmitted. Until then, the gap is documented in `proposal.md` "Out of scope".

**Alternative considered.** Mark the order as `TRANSMISSION_PENDING` and expose a manual "retry transmission" admin action. Rejected for POC — that is two-thirds of an outbox; if we are doing that, we should do the outbox.

### D4. Idempotent accounted callback: status-based

The handler uses **status-based idempotency**: if the order is already `ACCOUNTED`, the handler returns **200** without invoking `markAccounted`. No notification-ID dedup table.

```
   handle(orderId, payload):
     order ← orderRepository.findById(orderId) || 404
     if order.status == ACCOUNTED  →  return 200 no-op   ── idempotent fast path
     if order.status != EXECUTED   →  return 409 INVALID_STATUS_TRANSITION
     order.markAccounted(clock.now())
     orderRepository.save(order)
     auditLogger.log(orderId, "ORDER_ACCOUNTED", "BACK_OFFICE", now)
     return 200
```

**Why this is safe.** The `EXECUTED → ACCOUNTED` transition is monotonic: once made, it cannot be undone (`ACCOUNTED` has no allowed transitions). So "already in target state" is a valid stopping condition; replaying the callback can never produce a different observable state.

**Alternatives considered.**

- _Notification-ID dedup table._ Stronger guarantee against duplicate-with-different-payload, but the payload is small (status flip) so there is nothing to be inconsistent about. Adds a table and a pruning job. Deferred.
- _Throw on duplicate (let `OrderStatus.transitionTo(ACCOUNTED)` from `ACCOUNTED` raise)._ Loud, but every retry from the back-office becomes a 4xx to log noise. Rejected.

### D5. Domain shape for accounting: status flip only, no AccountingDetails record

`MoneyMarketOrder.markAccounted(Instant now)` performs the transition and bumps `updatedAt`. **No** `backOfficeContractId`, **no** `accountedAt` distinct from `updatedAt`, **no** new value object on the order.

```java
public void markAccounted(Instant now) {
    Objects.requireNonNull(now, "now must not be null");
    this.status = this.status.transitionTo(OrderStatus.ACCOUNTED);
    this.updatedAt = now;
}
```

**Why.** mmx does not know — and does not need — the back-office's own `contractId`. The callback's only side effect on the mmx side is a status flip. `updatedAt` already records "when this last changed", and because the only legal transition out of `EXECUTED` is `ACCOUNTED`, "last `updatedAt` of an `ACCOUNTED` order" is unambiguously "when accounted".

**Alternatives considered.**

- _New `AccountingDetails` record on `MoneyMarketOrder` (`backOfficeContractId`, `accountedAt`)._ Cleaner lifecycle separation, but adds a record we never read and a field we never store from BO. Rejected for POC; can be added without breaking change later if a use case appears.
- _Extend `ExecutionDetails` with `accountedAt`._ Mixes execution-time and post-accounting data on the same record. Rejected.

### D6. Accounted callback shape — addressed by mmx `orderId`

```
POST /api/v1/back-office/orders/{orderId}/accounted
Content-Type: application/json
Body: {}                                           ← acceptable
   or  { "accountedAt": "2026-05-10T19:18:00Z" }   ← optional, ignored if missing
```

The path identifier is the mmx `orderId` (UUID). The `accountedAt` body field is optional and informational; mmx records `clock.now()` on the server side regardless (single source of truth in mmx, no clock-skew handling needed for POC).

**Why `orderId` and not `contractNumber`.** The (intended-but-not-yet-enforced) invariant is that a single `contractNumber` is shared across SUBSCRIPTION + later INCREASE / DECREASE / REDEMPTION operations on the same subscription. Each operation is a distinct `MoneyMarketOrder` with its own status lifecycle and gets its own accounted callback. Routing by `contractNumber` would be ambiguous; routing by `orderId` is unambiguous regardless of how the (deferred) `contractNumber` bug fix lands.

**Status codes.**

- `200` — accounted (or already accounted; idempotent no-op).
- `404` `ORDER_NOT_FOUND` — unknown `orderId`.
- `409` `INVALID_STATUS_TRANSITION` — order found but not in `EXECUTED` (e.g. `RECEIVED`, `CANCELLED`). The back-office is expected to retry only on transient errors; a `409` is a configuration / data-integrity error to surface.

### D7. Counterparty exposure: extend `OrderSummaryResponse` globally (Option A)

A nullable `counterparty: string | null` is added to `OrderSummaryResponse` in the canonical OpenAPI. The field is populated only for orders in `EXECUTED` or `ACCOUNTED` (i.e. orders with `executionDetails`). Backend serialization uses `@JsonInclude(Include.NON_NULL)` (or the equivalent on the generated model) so non-executed list responses do not carry the key on the wire — matching the existing pattern called out for `minimumRate` in `api-v1.md`.

**Alternative considered.** A new `ExecutedOrderSummaryResponse` schema (via OpenAPI `allOf` over `OrderSummaryResponse`). Rejected: cleaner schema-wise, but creates a second list type the frontend must select between, and counterparty is a small addition. Option A's nullable-with-omit-null is the standard pattern in this codebase.

### D8. ACCOUNTED enum delivered in the same PR as the rest

The frontend's `OrderStatus` enum (`frontend/src/app/core/models/order-status.enum.ts`) is **hand-maintained**, not codegen — so there is no risk of generated-client parse failure on a previously-unknown value.

In the same PR:

- Append `ACCOUNTED = 'ACCOUNTED'` to the enum.
- Add a `[data-status='ACCOUNTED']` style block to `frontend/src/app/shared/components/status-badge.component.ts` (otherwise an `ACCOUNTED` badge renders unstyled).
- Audit any frontend code that switches over `OrderStatus` exhaustively. The current Executed list will not show `ACCOUNTED` rows (they leave the list), but the order details page will, so the badge styling is required.

### D9. REST surface — workspace-scoped paths, contract-first

Following the v1.1.0 convention in `specs/002-trader-orders-views/contracts/api-v1.md` (workspace-scoped list endpoints), the new endpoints are:

```
GET   /api/v1/orders/term/executed           ← Trader, X-Trader-Id
GET   /api/v1/orders/oncall/executed         ← Trader, X-Trader-Id
POST  /api/v1/back-office/orders/{orderId}/accounted   ← system, no auth (D10)
```

The Trader endpoints reuse the existing pagination conventions and return `OrderSummaryResponse` (now carrying `counterparty`). The contract is updated in `specs/002-trader-orders-views/contracts/openapi.yaml` first; codegen produces the server interfaces under `mmx-adapter-in-rest`; controllers implement them. The frontend's API service consumes the same contract.

### D10. No authentication on the back-office callback (POC)

The callback path is declared with `security: []` in OpenAPI (overriding any global security scheme). It accepts requests from any caller on the deployed network. This is documented as a known POC compromise; production deployment must add at minimum a shared API key or a network-level allow-list. The callback is **not** exposed via the Trader-facing path prefix (`/api/v1/orders/...`); it sits under `/api/v1/back-office/...` so a future security configuration can target the prefix without touching Trader endpoints.

### D11. Adapter placement and module boundaries

| Module | Additions / changes |
|---|---|
| `mmx-domain` | `OrderStatus.ACCOUNTED` + transition row; `MoneyMarketOrder.markAccounted(Instant)`; new `OrderStatusTest` cases |
| `mmx-application` | `port/in`: `ListExecutedTermOrdersUseCase`, `ListExecutedOnCallOrdersUseCase` (interfaces over the existing `OrderQueryService` methods), `MarkOrderAccountedUseCase`. `port/out`: rename `DepositsGateway` → `BackOfficeGateway` (signature unchanged). New `MarkOrderAccountedService` (or method on `OrderQueryService` / a new `AccountingService`). |
| `mmx-adapter-in-rest` | New controllers for the three endpoints in D9, implementing generated API interfaces. The execute controller becomes the **caller** that invokes `executeOrderService.execute(...)` then `backOfficeGateway.notifyExecution(saved)` post-commit. |
| `mmx-adapter-out-integration` | Rename `NoOpDepositsGateway` → `NoOpBackOfficeGateway`. The POC keeps a no-op default; a real HTTP adapter is **not** required for this change. Adding a logging variant (`LoggingBackOfficeGateway`) that records transmissions for test visibility is acceptable. |
| `mmx-adapter-out-persistence` | Add `ACCOUNTED` to the JPA enum mapping. Inspect the existing schema for a CHECK constraint on the status column; add a Flyway migration **only if** such a constraint exists. |
| `mmx-bootstrap` | Update `OrderModuleConfiguration`: bean factory `depositsGateway()` → `backOfficeGateway()`, return type, import. No new beans for the post-commit wiring (it is caller-orchestrated). |
| `frontend/` | New `executed-orders` feature (`term-executed-order-list`, `oncall-executed-order-list` standalone components + a service); enum + badge updates per D8; counterparty column. |

Tests live one module up from the unit they test (domain tests in `mmx-domain/src/test`, application tests in `mmx-application/src/test`, REST integration tests in `mmx-bootstrap`, e2e in `frontend/cypress/e2e`). Per project rule, `tasks.md` orders **failing tests before implementation**.

### D12. Feature-001 SDD parity

`specs/001-mm-order-processing/plan.md` references `DepositsGateway` / `NoOpDepositsGateway` descriptively (lines 142, 189, 889-890, 938) and is updated to the new names in the same delivery. `specs/001-mm-order-processing/tasks.md` records the historical `[x]` deliveries on lines 68 and 86 — **not rewritten**; a footnote noting the rename ships under change `executed-orders-accounting` is added.

`specs/002-trader-orders-views/spec.md`, `contracts/openapi.yaml`, and `contracts/api-v1.md` are updated per D7 / D9 / D10 in the same delivery (Principle VI).

## Risks / Trade-offs

- **Sync transmission swallowed failure → mmx ↔ back-office drift.** If the back-office is unreachable when an order is executed, the execution lives in mmx as `EXECUTED` forever (no callback ever arrives, no retry).  → _Mitigation_: explicit WARN log including `orderId`; documented in `proposal.md` "Out of scope"; outbox change is the planned long-term fix.
- **No auth on the callback → anyone on the deployed network can flip an order to `ACCOUNTED`.**  → _Mitigation_: dedicated `/api/v1/back-office/...` path prefix that production can lock down with a single security policy without touching Trader endpoints; explicit `security: []` in OpenAPI to make the gap auditable.
- **Counterparty added to all `OrderSummaryResponse` payloads.** Future requirement to hide counterparty by role becomes a refactor that touches every list endpoint.  → _Mitigation_: counterparty is null/omitted for non-executed orders, so most existing payloads are unchanged on the wire; if a role-based hide is needed later, it is a controller-layer transformation, not a schema refactor.
- **Frontend exhaustiveness.** Any TypeScript code switching over `OrderStatus` without a default case will silently miss `ACCOUNTED`.  → _Mitigation_: D8 audit step in tasks; Vitest covers the badge and the new list component; Cypress covers the Term Executed view including row removal on accounted.
- **Hidden coupling between the test stub and production behaviour.** A `LoggingBackOfficeGateway` that always succeeds gives a false sense of reliability in tests.  → _Mitigation_: explicitly test the failure path (gateway throws → execute still returns 200, log emitted, audit captured) in `mmx-adapter-in-rest` integration tests.
- **Renaming the gateway across feature-001 docs without rewriting `[x]` task lines** is a correct decision but creates a small linguistic gap (plan describes `BackOfficeGateway`, tasks list says `DepositsGateway` was created).  → _Mitigation_: footnote in tasks.md pointing forward to this change.

## Migration Plan

Single PR, FE + BE together (per D8). Work order:

1. Extend `specs/002-trader-orders-views/contracts/openapi.yaml` and `api-v1.md` with the three new endpoints (D9), the nullable `counterparty` on `OrderSummaryResponse` (D7), the `ACCOUNTED` enum value, and `security: []` on the callback (D10). Run codegen.
2. Domain (red → green): `OrderStatus.ACCOUNTED`, transition table, `markAccounted(Instant)`. Cover with `OrderStatusTest` and `MoneyMarketOrderTest` cases.
3. Application: rename `DepositsGateway` → `BackOfficeGateway` and update all imports/tests. Add `MarkOrderAccountedUseCase` + service. Add `ListExecuted{Term,OnCall}OrdersUseCase` interfaces over the existing `OrderQueryService` methods.
4. Persistence: enum mapping; Flyway migration only if a CHECK constraint exists.
5. Inbound adapters: implement the three controllers against generated interfaces. The execute controller now invokes the gateway post-commit.
6. Outbound adapter: rename `NoOpDepositsGateway` → `NoOpBackOfficeGateway`; optionally add `LoggingBackOfficeGateway`. Update bean wiring in `OrderModuleConfiguration`.
7. Frontend: enum + badge style block (D8); new `executed-orders` feature module per workspace; counterparty column; cypress e2e.
8. SDD parity: update `specs/001-mm-order-processing/plan.md` references; add the footnote in `specs/001-mm-order-processing/tasks.md`. Update `specs/002-trader-orders-views/spec.md` per D7 / D9 / D10.

**Rollback.** This is a POC; rollback is "revert the PR". No feature flag is introduced.

## Open Questions

- **Pagination defaults** for the new Executed endpoints — likely follow the same `(page, size)` defaults as the existing Received / Assigned endpoints; locked when authoring the spec for `trader-executed-queue`.
- **Stub gateway visibility** — whether `LoggingBackOfficeGateway` writes to the standard logger only, or also exposes a small in-memory ring buffer for `OrderRestApiIntegrationTest` to assert against. Tasks-level decision.
- **Frontend refresh cadence on the Executed list** — current decision is "refresh on next load", no real-time push. Confirm during the frontend tasks: poll-on-focus is acceptable; WebSocket is out of scope.
- **Specific error code for "BO callback found order in unexpected status"** — proposed `409 INVALID_STATUS_TRANSITION` (existing code from `api-v1.md`); if a more specific code is wanted, it goes in the spec for `back-office-accounting-handoff`.
