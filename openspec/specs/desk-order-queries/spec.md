# desk-order-queries Specification

## Purpose

Define the application-layer seam for Trader **desk queue reads** (Money Market Order list cohorts and order detail lookup). HTTP shape remains defined by `contracts/002-trader-orders-views/openapi.yaml`; this capability governs how the modular monolith exposes those reads behind a single inbound port.

## Requirements

### Requirement: Single inbound port for desk reads

The application layer SHALL expose exactly one inbound port (`DeskOrderQueries`) that owns all Trader desk **read** operations: workspace-scoped Received, Assigned, and Executed list cohorts, legacy trader-scoped Assigned listing, and order detail lookup by id. The REST adapter (`mmx-adapter-in-rest`) SHALL depend on this port for reads, not on concrete query service classes. The caller's active session scope `(LegalEntityCode, Role)` SHALL be supplied to the port, and every read SHALL be filtered to the active `LegalEntityCode`; a request authenticated with **`X-User-Id`** (role `Trader`) is the only caller permitted to use desk reads.

#### Scenario: REST lists use the desk query port

- **WHEN** `OrderManagementController` serves any workspace list GET or order detail GET
- **THEN** it delegates to `DeskOrderQueries` with the active scope and does not use `AssignmentService` for those operations

#### Scenario: Desk reads are scoped to the active LegalEntity

- **WHEN** a trader scoped to `PAR` requests any desk list or order detail
- **THEN** only orders owned by `PAR` are returned; orders owned by `LOC` are excluded

#### Scenario: Mutations stay on command ports

- **WHEN** the controller handles assign, unassign, execute, cancel, reject, or update
- **THEN** it uses the existing mutation use-case ports (`AssignOrderUseCase`, `UnassignOrderUseCase`, etc.) and not `DeskOrderQueries`

### Requirement: Desk query service implements cohort rules scoped by LegalEntity

`DeskOrderQueryService` (or equivalent) SHALL implement `DeskOrderQueries` by applying the same cohort rules as before consolidation, additionally filtered by the active `LegalEntityCode`:

- **Received** — `OrderStatus.RECEIVED` filtered by workspace `OrderType` and active `LegalEntityCode`, with `ReceivedListView` controlling near-term vs all (business calendar `Europe/Paris` for default window).
- **Assigned (workspace)** — desk-wide `OrderStatus.ASSIGNED` filtered by `OrderType` (Term or OnCall) and active `LegalEntityCode`; MUST NOT filter by assignee `TraderId`.
- **Executed** — `OrderStatus.EXECUTED` filtered by `OrderType` and active `LegalEntityCode`.
- **Assigned (legacy)** — trader-scoped `ASSIGNED` filtered by assignee and active `LegalEntityCode` when serving deprecated `GET /api/v1/orders/assigned`.
- **Detail** — load by order id via `OrderRepository`; the loaded order's owning `LegalEntityCode` MUST match the active scope, else treated as not found.

#### Scenario: Desk-wide Assigned Term scoped to active entity

- **WHEN** `listAssignedTermOrders(page, size)` is invoked for a session scoped to `LOC`
- **THEN** results include all `ASSIGNED` orders with `OrderType.TERM` owned by `LOC` regardless of assignee

#### Scenario: Received near-term window preserved and entity-scoped

- **WHEN** `listReceivedTermOrders` is called with default `ReceivedListView` for a session scoped to `PAR`
- **THEN** pagination applies the near-term `valueDate` window per existing trader-received-queue behaviour and only to orders owned by `PAR`

#### Scenario: Order detail in another entity is not found

- **WHEN** `getOrderDetails(orderId)` is called for an order owned by `LOC` from a session scoped to `PAR`
- **THEN** the service reports not-found and the REST layer maps to not-found as today

#### Scenario: Order detail lookup preserved within entity

- **WHEN** `getOrderDetails(orderId)` is called for an existing order owned by the active LegalEntity
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
- **THEN** `DeskOrderQueries` is available via `DeskOrderQueryService` and no separate `OrderQueryService` bean is required for desk reads
