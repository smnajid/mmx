## Context

Follow-up from architecture review (points 4, 5, 7, 8) after `consolidate-desk-query-module`. The **Money Market Order** aggregate is mapped independently in:

- `OrderRestMapper` (`mmx-adapter-in-rest`) — OpenAPI DTOs, `double` amounts, tenor **code** on wire
- `OrderPersistenceMapper` (`mmx-adapter-out-persistence`) — flat `OrderEntity`, tenor **`enum.name()`** in DB

Desk UI: **Executed** uses `ExecutedOrderListComponent` + thin shells; **Assigned** uses one list + `route.data.workspace`; **Received** still has parallel `term-order-list` / `oncall-order-list` (~200 lines each). **Order details** (~650 lines) embeds status/action rules inline.

**Constraints:** Contract-first REST unchanged (`specs/002-trader-orders-views/contracts/openapi.yaml`). Server assignee-only rules unchanged. TDD for new tests and refactors.

## Goals / Non-Goals

**Goals:**

- Mapper unit tests fail on field/encoding drift before integration tests.
- Remove dead `findByAssignedTraderIdAndStatusAndOrderType` from port + JPA.
- One Received list module with workspace parameter; routes match Executed shell pattern.
- Testable order-detail action model; smaller `order-details.component.ts`.

**Non-Goals:**

- Shared mapping kernel in `mmx-application` (no cross-adapter module this pass).
- OpenAPI changes (e.g. `handoffStatus` on details).
- DB pagination unification for Assigned/Executed.
- Cypress e2e unless a gap is found in unit tests.

## Decisions

### 1. Mapper tests (point 4)

**Decision:** Add `OrderPersistenceMapperTest` and `OrderRestMapperTest` in respective adapter modules. Use **fixture builders** for `MoneyMarketOrder` (reuse patterns from `JpaOrderRepositoryTest` / domain tests).

**Cases (minimum):**

| Lifecycle | Persistence round-trip | REST summary | REST details |
|-----------|---------------------|--------------|--------------|
| RECEIVED | tenor, notice, no assignment | columns, no handoff | full intake fields |
| ASSIGNED | assignment columns | assignedTraderId | assignedAt |
| EXECUTED + PENDING handoff | execution + handoff_status | counterparty, handoffStatus | execution block, no handoff on details (per OpenAPI) |

**Encoding assertions:**

- Tenor: DB stores `Tenor._3M.name()` → `_3M`; REST `toSummary` → `"3M"`.
- Notice: inbound REST `_24_H` → `NoticePeriod._24H`; outbound code on summary.
- Amount: persistence `BigDecimal` equality; REST `double` within tolerance or exact for test values.

**Refactor:** Extract private `applyCoreFields(MoneyMarketOrder, …)` used by `toSummary`/`toDetails` only if tests stay green — optional, not required for archive.

### 2. Dead query removal (point 5)

**Decision:** Delete method from `OrderRepository`, `SpringDataOrderRepository`, `JpaOrderRepository`. Grep repo; update `specs/002-trader-orders-views/tasks.md` note only if we touch historical task text (optional footnote in plan.md).

**Rationale:** Deletion test passed — no caller after desk-query consolidation. `findByAssignedTraderIdAndStatus` remains for deprecated `/orders/assigned`.

### 3. Received list shell (point 7)

**Decision:** New `frontend/src/app/features/received-orders/received-order-list.component.ts`:

- `WorkspaceKind = 'term' | 'oncall'` (same as executed).
- `@Input() fixedWorkspace` optional; else read `ActivatedRoute.snapshot.data['workspace']`.
- Inject `ReceivedViewModeService`, `OrderApiService`, `TraderContextService`.
- API: `listReceivedTermOrders` / `listReceivedOnCallOrders` with `receivedView` from mode service.
- Template: show-all checkbox, assign column, tenor vs notice column flags.

**Routes:**

```text
term/received  → TermReceivedOrderListShell → <mmx-received-order-list fixedWorkspace="term" />
oncall/received → OnCallReceivedOrderListShell → fixedWorkspace="oncall"
```

Or set `data: { workspace: 'term' }` on `app.routes.ts` children (mirror Assigned) and drop duplicate feature modules’ list components.

**Delete:** `term-order-list.component.ts`, `oncall-order-list.component.ts` (+ specs) after migration.

**Shared util:** `formatHttpError` → `frontend/src/app/core/http/format-http-error.ts` (used by received + order-details if touched).

### 4. Order detail actions (point 8)

**Decision:** Add `order-detail-actions.ts` (pure functions) or `OrderDetailActionPolicy` class in `features/order-details/`:

```text
export type OrderDetailAction = 'assign' | 'cancel' | 'reject' | 'unassign' | 'update' | 'execute';

export function allowedActions(order: OrderDetails, traderId: string): ReadonlySet<OrderDetailAction>
```

Rules **must match current template**:

- RECEIVED: assign, cancel, reject
- ASSIGNED: unassign; reject + update + execute forms only if `assignedTraderId === traderId`

Component: `readonly actions = computed(() => allowedActions(order(), trader.traderId()))`; template uses `@if (actions().has('assign'))`.

**Tests:** `order-detail-actions.spec.ts` table per status/assignee — no HTTP.

Sub-forms (`order-execution-form`, `order-update-form`) stay; only visibility/orchestration moves.

### 5. SDD / OpenSpec

**Decision:** Delta specs for three new capabilities + `trader-desk-navigation` MODIFIED. No OpenAPI edit.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Mapper tests brittle on `double` | Use integer amounts in fixtures or `BigDecimal` compare on parse |
| Received refactor breaks show-all | Port existing `term-order-list` spec cases to `received-order-list.spec.ts` |
| Action policy drifts from server | Document parity with domain rules; keep integration tests |
| Large PR | Tasks ordered: dead query → mapper tests → received shell → detail actions |

## Migration Plan

1. Backend: remove dead query + mapper tests (green `mvn test`).
2. Frontend: received shell + route update (`ng test`).
3. Order detail policy extract + component slim (`ng test`).
4. Remove obsolete components.

**Rollback:** Revert single commit/PR.

## Open Questions

- None blocking. Cypress for received show-all toggle: add only if Vitest coverage is insufficient.
