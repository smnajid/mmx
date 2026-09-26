## Context

The Trader desk exposes workspace-scoped list operations defined in `specs/002-trader-orders-views/contracts/openapi.yaml` (mirrored in `contracts/api-v1.md`). Today:

- `OrderManagementController` injects concrete `OrderQueryService` and `AssignmentService` for reads.
- Five `List*OrdersUseCase` ports plus `GetOrderDetailsUseCase` are implemented on `OrderQueryService` but not used as the REST seam.
- Desk-wide **Assigned** reads (`listAssignedTermOrders` / `listAssignedOnCallOrders`) live on `AssignmentService` without a port.
- Mutations correctly use inbound ports (`ExecuteOrderUseCase`, `AssignOrderUseCase`, etc.).

This is an **internal application-layer** refactor. HTTP paths, query parameters (`page`, `size`, `receivedView`), and response DTOs remain unchanged.

## Goals / Non-Goals

**Goals:**

- One inbound port (`DeskOrderQueries`) as the REST adapter’s only dependency for desk reads (lists + order detail).
- One implementation (`DeskOrderQueryService`) consolidating cohort logic from `OrderQueryService` and Assigned list reads from `AssignmentService`.
- `AssignmentService` limited to assign/unassign commands (existing mutation ports).
- Bootstrap exposes `DeskOrderQueries` bean; controller and tests depend on the port.
- Remove obsolete per-endpoint list ports and delete or slim `OrderQueryService` after migration.
- Preserve all existing runtime list behaviour (Received near-term window, desk-wide Assigned, Executed cohorts, deprecated trader-scoped `/orders/assigned` if still in OpenAPI).

**Non-Goals:**

- OpenAPI or frontend API client changes.
- Database pagination strategy (in-memory `paginate` for Assigned/Executed stays as-is).
- Merging mutation services or changing execute/handoff flow.
- Removing deprecated HTTP routes unless product explicitly drops them in a separate change.

## Decisions

### 1. Port name and placement

**Decision:** Add `com.mmx.order.application.port.in.DeskOrderQueries` in `mmx-application`, implemented by `DeskOrderQueryService` in `application.service`.

**Rationale:** Name reflects the desk model (workspace × queue) from spec 002, not URL shape. Single seam matches exploration option **B** (one query module).

**Alternatives considered:**

- *Inject existing list ports in REST* — multiplies types; does not fix desk-wide Assigned gap without two more ports.
- *Keep `OrderQueryService` name* — misleading once Assigned reads move off `AssignmentService`.

### 2. Port interface shape

**Decision:** Keep **explicit methods** aligned with OpenAPI operations (not a generic `listQueue(enum, enum)`), so the controller stays a thin delegate and codegen operation names stay traceable:

- `listReceivedTermOrders(page, size, receivedView)`
- `listReceivedOnCallOrders(page, size, receivedView)`
- `listAssignedTermOrders(page, size)` — desk-wide, no `TraderId`
- `listAssignedOnCallOrders(page, size)`
- `listExecutedTermOrders(page, size)`
- `listExecutedOnCallOrders(page, size)`
- `listAssignedOrders(TraderId, page, size)` — legacy trader-scoped cohort for deprecated route
- `getOrderDetails(UUID orderId)`

**Rationale:** Deep module via **locality** (one class owns all methods), not via a single generic entry point that pushes complexity to callers.

### 3. REST adapter seam

**Decision:** `OrderManagementController` replaces `OrderQueryService` + `AssignmentService` list usage with `DeskOrderQueries` only for reads; keeps `AssignmentService` (or `AssignOrderUseCase` / `UnassignOrderUseCase`) for assign/unassign POSTs.

**Canonical contract:** No edits to `specs/002-trader-orders-views/contracts/openapi.yaml`; controller continues to implement generated `OrdersApi`.

### 4. Deprecation handling

**Decision:** Move `listAssignedOrders(TraderId, …)` onto `DeskOrderQueries` so deprecated `GET /api/v1/orders/assigned` delegates through the same port. Remove `ListAssignedOrdersUseCase` and `implements ListAssignedOrdersUseCase` from `AssignmentService`.

**Rationale:** One read module; legacy HTTP remains contract-compliant until a separate change removes the operation.

### 5. `OrderPage` and outbound port

**Decision:** Leave `OrderPage` in `port.in` for now; `DeskOrderQueries` methods return `OrderPage`. Moving `OrderPage` off `OrderRepository` is out of scope.

### 6. Testing strategy (TDD)

**Decision:**

- Rename/refactor `OrderQueryServiceTest` → `DeskOrderQueryServiceTest` (red-first: tests fail on old type, then pass on new service).
- Trim `AssignmentServiceTest` to assign/unassign only; move list tests to desk query tests.
- REST tests: single `@Mock DeskOrderQueries` instead of `OrderQueryService` + `AssignmentService` for list endpoints; keep mutation mocks as today.

**Layers:** Application unit tests primary; existing integration tests (`OrderRestApiIntegrationTest` etc.) should pass unchanged if HTTP behaviour is preserved.

### 7. Documentation (SDD)

**Decision:** Update `specs/001-mm-order-processing/plan.md` application layer tree (ports list) in the same implementation PR — implementation-only but avoids spec drift on architecture.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Large mechanical diff touches many test files | Task ordering: port + service first, then controller, then delete old types; run `mvn test` per module |
| Missed call site still referencing `OrderQueryService` | Compile-time break; grep before delete |
| Confusion between `DeskOrderQueries` and mutation ports | Keep `AssignmentService` name for writes only; document in plan.md |
| `OrderQueryService` bean left wired but unused | Remove bean from `OrderModuleConfiguration`; expose `DeskOrderQueries` only |

## Migration Plan

1. Add `DeskOrderQueries` + `DeskOrderQueryService` (copy logic from `OrderQueryService` + Assigned list methods).
2. Wire bean; switch controller to port.
3. Update tests; remove old list ports and `OrderQueryService`.
4. Remove list methods from `AssignmentService`; update `AssignmentService` implements clause.
5. Run full backend test suite; smoke desk routes manually or rely on integration tests.

**Rollback:** Revert commit; no DB migration.

## Open Questions

- None blocking implementation. Optional follow-up: delete unused `OrderRepository.findByAssignedTraderIdAndStatusAndOrderType` in a separate change.
