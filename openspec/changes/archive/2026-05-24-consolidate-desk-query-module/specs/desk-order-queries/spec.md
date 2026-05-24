# desk-order-queries Specification

## Purpose

Define the application-layer seam for Trader **desk queue reads** (Money Market Order list cohorts and order detail lookup). HTTP shape remains defined by `specs/002-trader-orders-views/contracts/openapi.yaml`; this capability governs how the modular monolith exposes those reads behind a single inbound port.

## ADDED Requirements

### Requirement: Single inbound port for desk reads

The application layer SHALL expose exactly one inbound port (`DeskOrderQueries`) that owns all Trader desk **read** operations: workspace-scoped Received, Assigned, and Executed list cohorts, legacy trader-scoped Assigned listing, and order detail lookup by id. The REST adapter (`mmx-adapter-in-rest`) SHALL depend on this port for reads, not on concrete query service classes.

#### Scenario: REST lists use the desk query port

- **WHEN** `OrderManagementController` serves any workspace list GET or order detail GET
- **THEN** it delegates to `DeskOrderQueries` and does not call `OrderQueryService` or `AssignmentService` for those operations

#### Scenario: Mutations stay on command ports

- **WHEN** the controller handles assign, unassign, execute, cancel, reject, or update
- **THEN** it uses the existing mutation use-case ports (`AssignOrderUseCase`, `UnassignOrderUseCase`, etc.) and not `DeskOrderQueries`

### Requirement: Desk query service implements cohort rules unchanged

`DeskOrderQueryService` (or equivalent) SHALL implement `DeskOrderQueries` by applying the same cohort rules as before consolidation:

- **Received** — `OrderStatus.RECEIVED` filtered by workspace `OrderType`, with `ReceivedListView` controlling near-term vs all (business calendar `Europe/Paris` for default window).
- **Assigned (workspace)** — desk-wide `OrderStatus.ASSIGNED` filtered by `OrderType` (Term or OnCall); MUST NOT filter by assignee `TraderId`.
- **Executed** — `OrderStatus.EXECUTED` filtered by `OrderType`.
- **Assigned (legacy)** — trader-scoped `ASSIGNED` filtered by assignee when serving deprecated `GET /api/v1/orders/assigned`.
- **Detail** — load by order id via `OrderRepository`.

#### Scenario: Desk-wide Assigned Term unchanged

- **WHEN** `listAssignedTermOrders(page, size)` is invoked
- **THEN** results include all `ASSIGNED` orders with `OrderType.TERM` regardless of assignee

#### Scenario: Received near-term window preserved

- **WHEN** `listReceivedTermOrders` is called with default `ReceivedListView`
- **THEN** pagination applies the near-term `valueDate` window per existing trader-received-queue behaviour

#### Scenario: Order detail lookup preserved

- **WHEN** `getOrderDetails(orderId)` is called for an existing order
- **THEN** the service returns the aggregate; when missing, the REST layer maps to not-found as today

### Requirement: Assignment service excludes list reads

`AssignmentService` SHALL implement only assign and unassign mutation ports. It MUST NOT expose public list methods for desk cohorts after consolidation.

#### Scenario: Assign does not require query service

- **WHEN** a trader assigns an order via POST
- **THEN** only `AssignmentService` (or `AssignOrderUseCase`) participates; `DeskOrderQueryService` is not involved

### Requirement: Per-endpoint list ports removed

The application layer MUST NOT retain separate inbound ports named `ListReceivedTermOrdersUseCase`, `ListReceivedOnCallOrdersUseCase`, `ListExecutedTermOrdersUseCase`, `ListExecutedOnCallOrdersUseCase`, `ListAssignedOrdersUseCase`, or `GetOrderDetailsUseCase` as the REST seam for desk reads.

#### Scenario: Old list ports deleted

- **WHEN** the change is complete
- **THEN** those interfaces are removed from `mmx-application` and no production code references them

### Requirement: Bootstrap wires desk queries bean

`mmx-bootstrap` SHALL register a `DeskOrderQueries` bean implemented by the desk query service, satisfying constructor injection in `OrderManagementController`.

#### Scenario: Application starts with single query bean

- **WHEN** the Spring context loads
- **THEN** `DeskOrderQueries` is available and `OrderQueryService` is not required for desk reads
