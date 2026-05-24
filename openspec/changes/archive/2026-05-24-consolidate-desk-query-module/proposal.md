## Why

Desk queue list reads are split across five thin `List*OrdersUseCase` ports and two concrete services (`OrderQueryService`, `AssignmentService`), while the REST adapter injects the concrete classes and ignores the list ports. Spec 002 added desk-wide **Assigned** listing as public methods on `AssignmentService` with no port at all. This shallow seam forces maintainers to bounce between ports, services, and the controller to trace a single desk surface, and it will worsen as workspace queues evolve.

Consolidating reads behind one **desk query** module restores locality for cohort filtering (Received / Assigned / Executed × Term / OnCall) without changing the HTTP contract.

## What Changes

- Introduce a single inbound port (e.g. `DeskOrderQueries` / `OrderDeskQueries`) that owns all **read** operations for desk list cohorts and order detail lookup.
- Merge list-read logic from `OrderQueryService` and desk-wide Assigned reads from `AssignmentService` into one application service implementing that port.
- Remove the five per-endpoint list use-case interfaces (`ListReceivedTermOrdersUseCase`, `ListReceivedOnCallOrdersUseCase`, `ListExecutedTermOrdersUseCase`, `ListExecutedOnCallOrdersUseCase`, `ListAssignedOrdersUseCase`) and `GetOrderDetailsUseCase` as separate ports (detail moves into the desk query port).
- Update `OrderManagementController` to depend on the desk query **port**, not concrete query service classes.
- Keep **mutation** ports unchanged (`AssignOrderUseCase`, `UnassignOrderUseCase`, `ExecuteOrderUseCase`, etc.); `AssignmentService` retains assign/unassign only.
- Remove deprecated trader-scoped `listAssignedOrders` from the desk query surface if still only used by deprecated `GET /api/v1/orders/assigned` (retain deprecated HTTP route delegating to repository-equivalent behaviour or document removal in design).
- Update bootstrap wiring and REST/application tests to mock the single query port.
- Update feature documentation (`specs/001` / `specs/002` plan or architecture notes) to describe the query seam; **no OpenAPI or runtime HTTP behaviour change**.

## Capabilities

### New Capabilities

- `desk-order-queries`: Application-layer seam for Trader desk queue reads (workspace × queue cohorts, Received view mode, order detail by id) behind one port and implementation.

### Modified Capabilities

<!-- No product requirement or HTTP contract changes; implementation-only refactor aligned with existing 002 desk list behaviour. -->

## Impact

- **Backend modules**: `mmx-application` (ports, services), `mmx-adapter-in-rest` (`OrderManagementController`, REST tests), `mmx-bootstrap` (`OrderModuleConfiguration` beans).
- **HTTP / OpenAPI**: Unchanged paths, parameters, and response shapes (`specs/002-trader-orders-views/contracts/openapi.yaml`).
- **Frontend**: No change (same API client calls).
- **Tests**: TDD-friendly refactor — adjust `OrderQueryServiceTest` / `AssignmentServiceTest` into desk-query tests; REST slice tests inject one mock port.
- **Out of scope for this change**: DB-level pagination unification (separate deepening opportunity); removal of unused `findByAssignedTraderIdAndStatusAndOrderType` repository method (may be done opportunistically).
