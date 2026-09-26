## 1. Application port and tests (TDD)

- [x] 1.1 Add `DeskOrderQueries` interface at `backend/mmx-application/.../port/in/DeskOrderQueries.java` with methods per design (lists, legacy assigned, getOrderDetails)
- [x] 1.2 Copy `OrderQueryServiceTest` to `DeskOrderQueryServiceTest` targeting `DeskOrderQueryService` / `DeskOrderQueries` (red: class does not exist yet)
- [x] 1.3 Move Assigned **list** test cases from `AssignmentServiceTest` into `DeskOrderQueryServiceTest`; leave assign/unassign tests in `AssignmentServiceTest`
- [x] 1.4 Implement `DeskOrderQueryService` implementing `DeskOrderQueries` (logic from `OrderQueryService` + `listAssignedTermOrders` / `listAssignedOnCallOrders` / `listAssignedOrders` from `AssignmentService`)

## 2. Remove obsolete application types

- [x] 2.1 Delete `OrderQueryService.java` after logic migrated
- [x] 2.2 Remove list methods and `ListAssignedOrdersUseCase` from `AssignmentService`; keep `AssignOrderUseCase` / `UnassignOrderUseCase` only
- [x] 2.3 Delete inbound ports: `ListReceivedTermOrdersUseCase`, `ListReceivedOnCallOrdersUseCase`, `ListExecutedTermOrdersUseCase`, `ListExecutedOnCallOrdersUseCase`, `ListAssignedOrdersUseCase`, `GetOrderDetailsUseCase`
- [x] 2.4 Grep `mmx-application` and backend for stale imports; fix compile errors

## 3. Bootstrap wiring

- [x] 3.1 In `OrderModuleConfiguration`, replace `orderQueryService` bean with `DeskOrderQueries deskOrderQueries(...)` returning `DeskOrderQueryService`
- [x] 3.2 Verify `AssignmentService` bean unchanged for mutations

## 4. REST adapter (`mmx-adapter-in-rest`)

- [x] 4.1 Update `OrderManagementController` constructor: inject `DeskOrderQueries` for reads; keep `AssignmentService` (or assign/unassign ports) for POST assign/unassign only
- [x] 4.2 Delegate all list GETs and `getOrderDetails` to `DeskOrderQueries` (no OpenAPI/codegen change)
- [x] 4.3 Refactor REST tests (`OrderAssignmentControllerTest`, `OrderExecutionControllerTest`, `OrderLifecycleControllerTest`, `OrderUpdateControllerTest`): mock `DeskOrderQueries` instead of `OrderQueryService` / `AssignmentService` for list endpoints
- [x] 4.4 Confirm desk-wide Assigned tests (`doesNotPassTraderToListingUseCase`) still pass against `DeskOrderQueries` mock

## 5. Verification and documentation

- [x] 5.1 Run `mvn test` from `backend/` (or root Maven reactor) — all modules green
- [x] 5.2 Run existing integration test `OrderRestApiIntegrationTest` (bootstrap) if present — no HTTP behaviour change
- [x] 5.3 Update `specs/001-mm-order-processing/plan.md` application `port/in` tree to document `DeskOrderQueries` and note removal of per-list ports (SDD architecture sync; no `openapi.yaml` change)
