## Why

Architecture review surfaced four follow-ups after desk-query consolidation: adapter mappers are the only guard against wire/DB drift but lack dedicated tests; an unused three-argument repository finder misleads readers; Received desk lists duplicate Term/OnCall components while Executed and Assigned already use a shared shell; and order details concentrate lifecycle UI orchestration in one large component. Addressing these together improves locality, testability, and desk UI consistency without changing the HTTP contract.

## What Changes

### Backend — adapter mapping (point 4)

- Add dedicated unit tests for `OrderRestMapper` and `OrderPersistenceMapper` (table-driven by `OrderStatus` / lifecycle: RECEIVED, ASSIGNED, EXECUTED with handoff, etc.).
- Cover encoding rules called out in review: tenor/notice on wire vs DB, `BigDecimal` vs `double`, handoff on summary only, assignment/execution flattening.
- Optionally deduplicate shared field wiring inside `OrderRestMapper` (`toSummary` / `toDetails`) as part of test-driven refactor — no OpenAPI shape change.

### Backend — dead repository query (point 5)

- Remove unused `OrderRepository.findByAssignedTraderIdAndStatusAndOrderType` and its Spring Data / JPA implementations (desk-wide Assigned uses `findByStatusAndOrderType`; legacy trader-scoped list uses `findByAssignedTraderIdAndStatus`).
- No HTTP or domain behaviour change.

### Frontend — Received list unification (point 7)

- Introduce shared `ReceivedOrderListComponent` (mirror `ExecutedOrderListComponent`): workspace from route `data` or `fixedWorkspace` input.
- Replace `term-order-list` / `oncall-order-list` duplication with thin route shells or direct route wiring; preserve show-all (`ReceivedViewModeService`), assign affordance, and column flags per workspace.
- Consolidate duplicated HTTP error formatting where touched.

### Frontend — order details workflow shell (point 8)

- Extract status- and assignee-driven **action visibility** from `order-details.component.ts` into a testable module (pure functions or small service): which actions (assign, cancel, reject, unassign, update, execute) are offered for the loaded `OrderDetails` and current trader.
- Slim the component template to consume that model; preserve existing server-side rules and visible UX (assignee-only execute/unassign/update/reject on ASSIGNED).

## Capabilities

### New Capabilities

- `order-adapter-mapping`: Unit-test coverage and documented encoding correspondence for `MoneyMarketOrder` ↔ REST DTOs ↔ `OrderEntity`.
- `trader-received-list-shell`: Single Received queue list component parameterized by Term/OnCall workspace, aligned with Executed/Assigned desk patterns.
- `trader-order-detail-actions`: Client-side workflow model for order-detail allowed actions derived from order status and trader identity.

### Modified Capabilities

- `trader-desk-navigation`: Route/shell structure for Received queues documents the shared list component pattern (thin Term/OnCall wrappers like Executed).

## Impact

- **Backend**: `mmx-adapter-in-rest` (mapper + tests), `mmx-adapter-out-persistence` (mapper tests, repository cleanup), `mmx-application` (`OrderRepository` port).
- **Frontend**: `features/received-orders/` (new), `term-orders/`, `oncall-orders/`, `order-details/`, `app.routes.ts`; Vitest updates.
- **HTTP / OpenAPI**: Unchanged.
- **SDD**: No `openapi.yaml` change; optional note in `specs/001-mm-order-processing/plan.md` for dead query removal; feature `spec.md` only if navigation wording needs sync.
