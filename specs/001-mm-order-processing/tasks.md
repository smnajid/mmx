# Tasks: Money Market Order Processing

**Input**: Design documents from `/specs/001-mm-order-processing/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/

**Constitution**: Principle I requires **contract-first OpenAPI 3** (`specs/.../contracts/openapi.yaml`), **generated** REST models and API interfaces, and **controllers that implement** those interfaces; prose `contracts/api-v1.md` is not sufficient alone (see plan Constitution Check **CF**).

**Tests**: TDD is mandatory per Constitution Principle V. Domain and application tests are written before production code.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Backend**: `backend/mmx-{module}/src/main/java/com/mmx/order/...`
- **Backend tests**: `backend/mmx-{module}/src/test/java/com/mmx/order/...`
- **Frontend**: `frontend/src/app/...`
- **Migrations**: `backend/mmx-bootstrap/src/main/resources/db/migration/`
- **OpenAPI (canonical HTTP contract)**: `specs/001-mm-order-processing/contracts/openapi.yaml` — generate REST models (`generated.model`), Spring API interfaces (`generated.api`), and compile sources under `mmx-adapter-in-rest` per plan §6 and constitution v1.4.0; do **not** add hand-written duplicate request/response classes when codegen provides them (extend generated types only with documented exceptions).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, Maven multi-module scaffold, Angular scaffold, Docker

- [x] T001 Create Maven parent POM at backend/pom.xml declaring 6 modules (mmx-domain, mmx-application, mmx-adapter-in-rest, mmx-adapter-out-persistence, mmx-adapter-out-integration, mmx-bootstrap) with Spring Boot 4.0.5 parent, Java 25, and shared dependency management for JUnit 5, AssertJ, Mockito, Testcontainers
- [x] T002 [P] Create mmx-domain/pom.xml with zero framework dependencies (only JUnit 5 + AssertJ in test scope)
- [x] T003 [P] Create mmx-application/pom.xml depending on mmx-domain only (no Spring)
- [x] T004 [P] Create mmx-adapter-in-rest/pom.xml depending on mmx-application + spring-boot-starter-web + spring-boot-starter-validation + springdoc-openapi
- [x] T005 [P] Create mmx-adapter-out-persistence/pom.xml depending on mmx-application + spring-boot-starter-data-jpa + postgresql + flyway-core + Testcontainers in test scope
- [x] T006 [P] Create mmx-adapter-out-integration/pom.xml depending on mmx-application only
- [x] T007 Create mmx-bootstrap module: pom.xml depending on all adapter modules, MmxApplication.java at backend/mmx-bootstrap/src/main/java/com/mmx/order/MmxApplication.java, application.yml and application-test.yml at backend/mmx-bootstrap/src/main/resources/
- [x] T008 [P] Create docker-compose.yml at repo root with PostgreSQL 16 service (database: mmx, user: mmx, password: mmx, port 5432)
- [x] T009 [P] Initialize Angular 21 project at frontend/ with standalone components, create proxy.conf.json for /api → localhost:8080
- [x] T010 [P] Create Angular core models at frontend/src/app/core/models/: order.model.ts (OrderSummary, OrderDetails interfaces), order-type.enum.ts, order-operation.enum.ts, order-status.enum.ts
- [x] T011 [P] Create OrderApiService at frontend/src/app/core/api/order-api.service.ts with typed HTTP methods for all 11 endpoints per contracts/api-v1.md

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain model, persistence layer, and shared adapter infrastructure that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

### Domain Model (TDD: write tests first)

- [x] T012 [P] Create domain enums at backend/mmx-domain/src/main/java/com/mmx/order/domain/model/: OrderType.java (TERM, ON_CALL), OrderOperation.java (SUBSCRIPTION, INCREASE, DECREASE, REDEMPTION), Tenor.java (_1W through _1Y with code field), NoticePeriod.java (_24H, _48H with code field)
- [x] T013 [P] Create OrderStatus enum with transitionTo(OrderStatus) method and allowed-transitions validation at backend/mmx-domain/src/main/java/com/mmx/order/domain/model/OrderStatus.java
- [x] T014 [P] Create typed value objects at backend/mmx-domain/src/main/java/com/mmx/order/domain/model/: ExternalOrderReference.java, ContractNumber.java, DealingReference.java, PortfolioNumber.java, TraderId.java (immutable Java `record` types with validation in compact constructors)
- [x] T015 [P] Create composite value objects at backend/mmx-domain/src/main/java/com/mmx/order/domain/model/: Assignment.java (traderId + assignedAt), ExecutionDetails.java (executedRate + counterparty + executionTime + dealingReference + generatedContractNumber) — both Java `record` types
- [x] T016 [P] Create domain exceptions at backend/mmx-domain/src/main/java/com/mmx/order/domain/exception/: InvalidOrderException.java, InvalidStatusTransitionException.java, OrderNotFoundException.java
- [x] T017 Write domain unit tests for OrderStatus transitions (all 5 valid transitions pass, all invalid transitions throw InvalidStatusTransitionException) at backend/mmx-domain/src/test/java/com/mmx/order/domain/model/OrderStatusTest.java
- [x] T018 Create MoneyMarketOrder aggregate root with static factory method, all creation invariants (OrderType/OrderOperation combos, field requirements per operation, ValueDate >= today+2, Amount > 0, MinimumRate >= 0, Tenor/NoticePeriod exclusivity), and lifecycle methods (assign, unassign, update, execute, cancel, reject) at backend/mmx-domain/src/main/java/com/mmx/order/domain/model/MoneyMarketOrder.java
- [x] T019 Write domain unit tests for MoneyMarketOrder creation invariants at backend/mmx-domain/src/test/java/com/mmx/order/domain/model/MoneyMarketOrderCreationTest.java: valid Term/Subscription, valid OnCall/Subscription, valid OnCall/Increase, all invalid OrderType/OrderOperation combos rejected, missing required fields rejected, invalid Tenor rejected, invalid NoticePeriod rejected, ValueDate in past rejected, Amount <= 0 rejected, MinimumRate < 0 rejected, BigDecimal precision preserved
- [x] T020 Write domain unit tests for MoneyMarketOrder lifecycle methods at backend/mmx-domain/src/test/java/com/mmx/order/domain/model/MoneyMarketOrderLifecycleTest.java: assign from RECEIVED, unassign from ASSIGNED, unassign by wrong Trader rejected, execute from ASSIGNED, execute by wrong Trader rejected, execute with missing data rejected, cancel from RECEIVED, reject from RECEIVED with reason, all invalid transitions rejected, update by assigned Trader, update by wrong Trader rejected, update from wrong status rejected

### Outbound Ports and Commands

- [x] T021 [P] Create all outbound port interfaces at backend/mmx-application/src/main/java/com/mmx/order/application/port/out/: OrderRepository.java (save, findById, findByExternalOrderReference, findByStatusAndOrderType, findByAssignedTraderIdAndStatus), ReferenceGenerator.java (generateDealingReference, generateContractNumber), AuditLogger.java (log event), DepositsGateway.java (notifyExecution — no-op in V1), Clock.java (now → Instant)
- [x] T022 [P] Create all command records at backend/mmx-application/src/main/java/com/mmx/order/application/command/: ReceiveOrderCommand, AssignOrderCommand, UnassignOrderCommand, UpdateOrderCommand, ExecuteOrderCommand, CancelOrderCommand, RejectOrderCommand

### Persistence Adapter

- [x] T023 [P] Create Flyway migrations at backend/mmx-bootstrap/src/main/resources/db/migration/: V1__create_money_market_order_table.sql (all columns per plan section 7), V2__create_order_audit_log_table.sql, V3__add_indexes.sql (unique on external_order_reference, composite on status+order_type, composite on assigned_trader_id+status, on value_date, on audit order_id and event_time)
- [x] T024 [P] Create OrderEntity JPA entity at backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/entity/OrderEntity.java mapping all columns from the money_market_order table
- [x] T025 [P] Create AuditLogEntity JPA entity at backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/entity/AuditLogEntity.java
- [x] T026 [P] Create SpringDataOrderRepository (Spring Data JPA interface) at backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/repository/SpringDataOrderRepository.java with query methods for findByExternalOrderReference, findByStatusAndOrderType, findByAssignedTraderIdAndStatus
- [x] T027 [P] Create SpringDataAuditLogRepository at backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/repository/SpringDataAuditLogRepository.java
- [x] T028 Create OrderPersistenceMapper at backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/mapper/OrderPersistenceMapper.java mapping MoneyMarketOrder ↔ OrderEntity (manual, explicit)
- [x] T029 Create JpaOrderRepository implementing OrderRepository port at backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/JpaOrderRepository.java
- [x] T030 Create JpaAuditLogger implementing AuditLogger port at backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/JpaAuditLogger.java
- [x] T031 Write persistence integration tests with Testcontainers at backend/mmx-adapter-out-persistence/src/test/java/com/mmx/order/adapter/out/persistence/JpaOrderRepositoryTest.java: save and retrieve, unique constraint on external_order_reference, query by status+orderType, query by assignedTraderId+status, OrderEntity ↔ MoneyMarketOrder mapping roundtrip

### Integration Adapters

- [x] T032 [P] Create UuidReferenceGenerator at backend/mmx-adapter-out-integration/src/main/java/com/mmx/order/adapter/out/integration/UuidReferenceGenerator.java (DL-{UUID}, CN-{UUID})
- [x] T033 [P] Create NoOpDepositsGateway at backend/mmx-adapter-out-integration/src/main/java/com/mmx/order/adapter/out/integration/NoOpDepositsGateway.java
- [x] T034 [P] Create SystemClock at backend/mmx-adapter-out-integration/src/main/java/com/mmx/order/adapter/out/integration/SystemClock.java

### Shared REST Infrastructure

- [x] T035 [P] Create ErrorResponse DTO at backend/mmx-adapter-in-rest/src/main/java/com/mmx/order/adapter/in/rest/dto/ErrorResponse.java and GlobalExceptionHandler at backend/mmx-adapter-in-rest/src/main/java/com/mmx/order/adapter/in/rest/GlobalExceptionHandler.java mapping domain exceptions to HTTP status codes (InvalidOrderException→400, OrderNotFoundException→404, InvalidStatusTransitionException→409)
- [x] T036 [P] Create OrderSummaryResponse and OrderDetailsResponse DTOs at backend/mmx-adapter-in-rest/src/main/java/com/mmx/order/adapter/in/rest/dto/
- [x] T037 [P] Create OrderRestMapper at backend/mmx-adapter-in-rest/src/main/java/com/mmx/order/adapter/in/rest/mapper/OrderRestMapper.java (MoneyMarketOrder → OrderSummaryResponse, MoneyMarketOrder → OrderDetailsResponse)

### Bootstrap Wiring

- [x] T038 Create OrderModuleConfiguration at backend/mmx-bootstrap/src/main/java/com/mmx/order/config/OrderModuleConfiguration.java wiring all ports to adapter implementations via @Bean methods

**Checkpoint**: Foundation ready — verify Maven builds cleanly, Flyway migrations run against Docker PostgreSQL, all domain tests pass

---

## Phase 2.5: Contract-first OpenAPI (Constitution & plan §6 — CF gate)

**Purpose**: Machine-readable OpenAPI 3 spec and codegen **before** REST controllers; closes Constitution/plan **CF** gap (prefer generated models and `*Api` interfaces over redundant adapter-local DTO duplicates).

**Independent check**: `openapi.yaml` validates; `mvn generate-sources` (or equivalent) produces Java models and API interfaces; controllers implement generated contracts and do not diverge from spec

- [x] T098 [P] Create canonical OpenAPI 3.0 document at specs/001-mm-order-processing/contracts/openapi.yaml covering paths, operations, components/schemas, and errors aligned with contracts/api-v1.md and plan §6 (single source for codegen)
- [X] T099 Add openapi-generator-maven-plugin (or equivalent) to backend/mmx-adapter-in-rest/pom.xml generating Java API models (and optional Spring interfaces) into target/generated-sources/openapi under package agreed in plan (e.g. `com.mmx.order.adapter.in.rest.generated`)
- [X] T100 Wire generated sources into the Maven compile lifecycle for mmx-adapter-in-rest; replace hand-written OrderSummaryResponse, OrderDetailsResponse, ErrorResponse usage in OrderRestMapper and GlobalExceptionHandler with generated equivalents (or thin adapters) so shared REST layer conforms to openapi.yaml
- [X] T101 [P] Document OpenAPI maintenance workflow in specs/001-mm-order-processing/quickstart.md (edit YAML → regenerate → align controllers) and note relationship to contracts/api-v1.md

**Checkpoint**: Contract-first pipeline works — generated artifacts compile; remaining REST tasks (T047+) implement controllers against generated contract

---

## Phase 3: User Story 1 — Receive and List Orders (Priority: P1) 🎯 MVP

**Goal**: Orders from Portfolio Management are received via REST and appear in separate Term/OnCall lists

**Independent Test**: POST an order → GET /term/received or /oncall/received → order appears

### Inbound Ports

- [x] T039 [P] [US1] Create ReceiveOrderUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/ReceiveOrderUseCase.java
- [x] T040 [P] [US1] Create ListReceivedTermOrdersUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/ListReceivedTermOrdersUseCase.java
- [x] T041 [P] [US1] Create ListReceivedOnCallOrdersUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/ListReceivedOnCallOrdersUseCase.java
- [x] T042 [P] [US1] Create GetOrderDetailsUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/GetOrderDetailsUseCase.java

### Application Services (TDD)

- [x] T043 [US1] Write application tests for ReceiveOrderService at backend/mmx-application/src/test/java/com/mmx/order/application/service/ReceiveOrderServiceTest.java: new order persisted, audit logged, idempotent duplicate returns existing order without modification, validation delegated to domain
- [x] T044 [US1] Create ReceiveOrderService implementing ReceiveOrderUseCase at backend/mmx-application/src/main/java/com/mmx/order/application/service/ReceiveOrderService.java
- [x] T045 [US1] Write application tests for OrderQueryService (list Term, list OnCall, get details) at backend/mmx-application/src/test/java/com/mmx/order/application/service/OrderQueryServiceTest.java
- [x] T046 [US1] Create OrderQueryService implementing List* and GetOrderDetails use cases at backend/mmx-application/src/main/java/com/mmx/order/application/service/OrderQueryService.java

### REST Endpoints

- [X] T047 [US1] Use OpenAPI-generated `ReceiveOrderRequest` and Bean Validation from codegen (`useBeanValidation` in `mmx-adapter-in-rest`); **no** hand-written `dto/ReceiveOrderRequest` — intake implements `IntakeApi` with generated model types per constitution
- [X] T048 [US1] Create OrderIntakeController (POST /api/v1/orders) at backend/mmx-adapter-in-rest/src/main/java/com/mmx/order/adapter/in/rest/OrderIntakeController.java returning 201 for new, 200 for idempotent duplicate
- [X] T049 [US1] Create OrderManagementController with GET /api/v1/orders/term/received, GET /api/v1/orders/oncall/received, GET /api/v1/orders/{orderId} at backend/mmx-adapter-in-rest/src/main/java/com/mmx/order/adapter/in/rest/OrderManagementController.java
- [X] T050 [US1] Write REST API tests for intake and list/detail endpoints at backend/mmx-bootstrap/src/test/java/com/mmx/order/rest/OrderRestApiIntegrationTest.java (`@ActiveProfiles("rest-test")`): 201 new order, 200 duplicate, 400 invalid payload, Term/OnCall list filtering, GET details 200

### Frontend

- [X] T051 [P] [US1] Create term-orders feature: TermOrderListComponent and routes at frontend/src/app/features/term-orders/
- [X] T052 [P] [US1] Create oncall-orders feature: OnCallOrderListComponent and routes at frontend/src/app/features/oncall-orders/
- [X] T053 [P] [US1] Create shared OrderTableComponent at frontend/src/app/shared/components/order-table.component.ts and StatusBadgeComponent at frontend/src/app/shared/components/status-badge.component.ts
- [X] T054 [US1] Create app.routes.ts with /term-orders, /oncall-orders routes and navigation layout in app.component.ts

**Checkpoint**: User Story 1 complete — receive orders via curl, verify they appear in Term/OnCall lists in both API and UI

---

## Phase 4: User Story 2 — Assign and Unassign Orders (Priority: P2)

**Goal**: Traders can assign Received orders to themselves, view their assigned list, and unassign

**Independent Test**: Assign an order → appears in /assigned list → unassign → back in Received list

### Inbound Ports

- [x] T055 [P] [US2] Create AssignOrderUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/AssignOrderUseCase.java
- [x] T056 [P] [US2] Create UnassignOrderUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/UnassignOrderUseCase.java
- [x] T057 [P] [US2] Create ListAssignedOrdersUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/ListAssignedOrdersUseCase.java

### Application Services (TDD)

- [ ] T058 [US2] Write application tests for AssignmentService at backend/mmx-application/src/test/java/com/mmx/order/application/service/AssignmentServiceTest.java: assign from RECEIVED, unassign from ASSIGNED, unassign by wrong Trader → 403, assign non-RECEIVED → 409, audit events logged
- [ ] T059 [US2] Create AssignmentService implementing Assign/Unassign/ListAssigned use cases at backend/mmx-application/src/main/java/com/mmx/order/application/service/AssignmentService.java

### REST Endpoints

- [ ] T060 [US2] Add POST /api/v1/orders/{orderId}/assign, POST /api/v1/orders/{orderId}/unassign, GET /api/v1/orders/assigned endpoints to OrderManagementController
- [ ] T061 [US2] Write REST API tests for assignment endpoints at backend/mmx-adapter-in-rest/src/test/java/com/mmx/order/adapter/in/rest/OrderAssignmentControllerTest.java: 200 assign success, 409 wrong status, 403 wrong Trader on unassign, assigned list filtered by X-Trader-Id

### Frontend

- [ ] T062 [P] [US2] Create assigned-orders feature: AssignedOrderListComponent and routes at frontend/src/app/features/assigned-orders/
- [ ] T063 [US2] Add Assign and Unassign action buttons to order list rows and order detail view, calling OrderApiService.assign/unassign
- [ ] T064 [US2] Add /assigned-orders route to app.routes.ts and navigation

**Checkpoint**: User Story 2 complete — assign from list, see in assigned orders, unassign back to Received

---

## Phase 5: User Story 3 — Execute an Order (Priority: P3)

**Goal**: Assigned Trader records execution with ExecutedRate and Counterparty; system generates DealingReference, ContractNumber, ExecutionTime

**Independent Test**: Assign → execute with valid data → verify EXECUTED status + generated references

### Inbound Port

- [x] T065 [US3] Create ExecuteOrderUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/ExecuteOrderUseCase.java

### Application Service (TDD)

- [ ] T066 [US3] Write application tests for ExecuteOrderService at backend/mmx-application/src/test/java/com/mmx/order/application/service/ExecuteOrderServiceTest.java: execute success with generated references and system clock time, missing ExecutedRate rejected, missing Counterparty rejected, wrong Trader → 403, non-ASSIGNED → 409, audit event with dealingReference and contractNumber
- [ ] T067 [US3] Create ExecuteOrderService implementing ExecuteOrderUseCase at backend/mmx-application/src/main/java/com/mmx/order/application/service/ExecuteOrderService.java (calls ReferenceGenerator + Clock ports)

### REST Endpoint

- [ ] T068 [US3] Map execute endpoint to generated `ExecuteOrderRequest` / `OrdersApi` (OpenAPI models + Spring interface from codegen); add mapper/`OrderManagementController` implementation — **no** duplicate `dto/ExecuteOrderRequest.java` unless a documented codegen gap
- [ ] T069 [US3] Add POST /api/v1/orders/{orderId}/execute endpoint to OrderManagementController
- [ ] T070 [US3] Write REST API tests for execute endpoint at backend/mmx-adapter-in-rest/src/test/java/com/mmx/order/adapter/in/rest/OrderExecutionControllerTest.java: 200 success with generated fields in response, 400 missing fields, 403 wrong Trader, 409 wrong status

### Frontend

- [ ] T071 [US3] Create order-details feature: OrderDetailsComponent at frontend/src/app/features/order-details/order-details.component.ts showing full order details with status-conditional action buttons
- [ ] T072 [US3] Create OrderExecutionFormComponent at frontend/src/app/features/order-details/order-execution-form.component.ts with ExecutedRate (number) and Counterparty (free text) fields
- [ ] T073 [US3] Add /orders/:id route to app.routes.ts, wire order list rows to navigate to detail view

**Checkpoint**: User Story 3 complete — full receive → assign → execute flow works end-to-end

---

## Phase 6: User Story 4 — Cancel and Reject Orders (Priority: P4)

**Goal**: Traders can cancel (withdrawn) or reject (refused, with mandatory reason) Received orders

**Independent Test**: Receive → cancel → verify CANCELLED; Receive → reject with reason → verify REJECTED + reason stored

### Inbound Ports

- [x] T074 [P] [US4] Create CancelOrderUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/CancelOrderUseCase.java
- [x] T075 [P] [US4] Create RejectOrderUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/RejectOrderUseCase.java

### Application Service (TDD)

- [ ] T076 [US4] Write application tests for OrderLifecycleService at backend/mmx-application/src/test/java/com/mmx/order/application/service/OrderLifecycleServiceTest.java: cancel from RECEIVED, reject from RECEIVED with reason, cancel non-RECEIVED → 409, reject non-RECEIVED → 409, reject without reason → validation error, audit events for both
- [ ] T077 [US4] Create OrderLifecycleService implementing Cancel/Reject use cases at backend/mmx-application/src/main/java/com/mmx/order/application/service/OrderLifecycleService.java

### REST Endpoints

- [ ] T078 [US4] Use generated `RejectOrderRequest` from OpenAPI for POST reject body (mandatory reason in spec/codegen); wire via `OrdersApi` — **no** hand-written `dto/RejectOrderRequest.java` unless documented exception
- [ ] T079 [US4] Add POST /api/v1/orders/{orderId}/cancel and POST /api/v1/orders/{orderId}/reject endpoints to OrderManagementController
- [ ] T080 [US4] Write REST API tests for cancel and reject endpoints at backend/mmx-adapter-in-rest/src/test/java/com/mmx/order/adapter/in/rest/OrderLifecycleControllerTest.java: 200 cancel success, 200 reject success with reason, 409 wrong status, reject without reason → 400

### Frontend

- [ ] T081 [US4] Add Cancel and Reject action buttons to order detail view (visible only for RECEIVED orders), with ConfirmDialogComponent for cancel and a reason input dialog for reject at frontend/src/app/features/order-details/
- [ ] T082 [P] [US4] Create ConfirmDialogComponent at frontend/src/app/shared/components/confirm-dialog.component.ts

**Checkpoint**: User Story 4 complete — cancel and reject from Received status, rejection reason visible in details

---

## Phase 7: User Story 5 — Update an Assigned Order (Priority: P5)

**Goal**: Assigned Trader can modify Amount, MinimumRate, ValueDate, DesiredCounterpartyComment before execution

**Independent Test**: Assign → update Amount → verify new value persisted and revalidated

### Inbound Port

- [x] T083 [US5] Create UpdateAssignedOrderUseCase interface at backend/mmx-application/src/main/java/com/mmx/order/application/port/in/UpdateAssignedOrderUseCase.java

### Application Service (TDD)

- [ ] T084 [US5] Write application tests for UpdateOrderService at backend/mmx-application/src/test/java/com/mmx/order/application/service/UpdateOrderServiceTest.java: update Amount success, update ValueDate too soon rejected, update by wrong Trader → 403, update non-ASSIGNED → 409, audit event with changed fields
- [ ] T085 [US5] Create UpdateOrderService implementing UpdateAssignedOrderUseCase at backend/mmx-application/src/main/java/com/mmx/order/application/service/UpdateOrderService.java

### REST Endpoint

- [ ] T086 [US5] Use generated `UpdateOrderRequest` from OpenAPI (optional fields per spec); implement under `OrdersApi` — **no** hand-written `dto/UpdateOrderRequest.java` unless documented exception
- [ ] T087 [US5] Add PUT /api/v1/orders/{orderId} endpoint to OrderManagementController
- [ ] T088 [US5] Write REST API tests for update endpoint at backend/mmx-adapter-in-rest/src/test/java/com/mmx/order/adapter/in/rest/OrderUpdateControllerTest.java: 200 success, 400 invalid values, 403 wrong Trader, 409 wrong status

### Frontend

- [ ] T089 [US5] Create OrderUpdateFormComponent at frontend/src/app/features/order-details/order-update-form.component.ts with editable Amount, MinimumRate, ValueDate, DesiredCounterpartyComment fields (visible only for ASSIGNED orders owned by current Trader)

**Checkpoint**: User Story 5 complete — all mutable fields updatable with revalidation

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end tests, OpenAPI documentation, Angular component tests, final cleanup

- [ ] T090 Write end-to-end test: full Trader workflow (receive → list → assign → execute → verify) at backend/mmx-bootstrap/src/test/java/com/mmx/order/e2e/TraderWorkflowE2ETest.java using Testcontainers
- [ ] T091 [P] Write end-to-end test: cancel flow (receive → cancel → verify terminal) at backend/mmx-bootstrap/src/test/java/com/mmx/order/e2e/CancelFlowE2ETest.java
- [ ] T092 [P] Write end-to-end test: idempotent receive (POST same ExternalOrderReference twice → 201 then 200, same orderId) at backend/mmx-bootstrap/src/test/java/com/mmx/order/e2e/IdempotentReceiveE2ETest.java
- [ ] T093 [P] Configure springdoc-openapi in mmx-bootstrap to expose Swagger UI (e.g. /swagger-ui.html) using the canonical OpenAPI document from specs/001-mm-order-processing/contracts/openapi.yaml (resource/MBean config) so docs match codegen source
- [ ] T094 [P] Write Angular component tests for TermOrderListComponent, OnCallOrderListComponent, and AssignedOrderListComponent using HttpClientTestingModule
- [ ] T095 [P] Write Angular component tests for OrderDetailsComponent: correct action buttons shown per status
- [ ] T096 Configure Cypress and write e2e test for full Trader workflow (receive → assign → execute) at frontend/cypress/e2e/trader-workflow.cy.ts
- [ ] T097 Run full build verification (mvn verify + ng test + Cypress) and fix any remaining issues

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion — BLOCKS all user stories
- **Contract-first OpenAPI (Phase 2.5)**: Depends on Phase 2 — **strongly recommended before** US1 REST endpoint tasks **T047–T050** (and ideally before other REST DTO/controller tasks) so new code uses generated contract types; application-layer tasks **T043–T046** do not require Phase 2.5
- **User Stories (Phase 3–7)**: Depend on Foundational completion; REST adapter work should align with **T098–T100** once executed
  - User stories can proceed in priority order (P1 → P2 → P3 → P4 → P5)
  - US4 and US5 can run in parallel after US2 is complete (both need assignment but don't depend on each other)
- **Polish (Phase 8)**: Depends on all user stories being complete

### User Story Dependencies

- **US1 (P1)**: Depends on Foundational — no dependency on other stories
- **US2 (P2)**: Depends on US1 (needs orders to exist for assignment)
- **US3 (P3)**: Depends on US2 (needs assigned orders for execution)
- **US4 (P4)**: Depends on US1 (needs received orders for cancel/reject); can run in parallel with US3
- **US5 (P5)**: Depends on US2 (needs assigned orders for update); can run in parallel with US3 and US4

### Within Each User Story

- Tests MUST be written and FAIL before implementation (TDD for domain/application)
- Inbound ports before services
- Services before controllers
- Backend before frontend components
- Story complete before moving to next priority

### Parallel Opportunities

```text
Phase 1: T002–T006 in parallel (module POMs), T008–T011 in parallel (Docker, Angular, models)
Phase 2: T012–T016 in parallel (enums, VOs, exceptions), T021–T022 in parallel (ports, commands),
         T023–T027 in parallel (migrations, entities, Spring Data repos), T032–T034 in parallel (adapters)
Phase 2.5: T098, T101 in parallel (openapi.yaml authoring vs docs quickstart); T099→T100 sequential (plugin then migrate DTOs)
US1:     T039–T042 in parallel (ports), T051–T053 in parallel (frontend features)
US2:     T055–T057 in parallel (ports), T062 parallel with backend work
US4/US5: Can run entirely in parallel with US3
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 2.5: Contract-first OpenAPI **before** US1 REST controllers (T047+) for constitutional compliance; may overlap with application services already done
4. Complete Phase 3: User Story 1
5. **STOP and VALIDATE**: Receive orders via curl, verify in Term/OnCall lists
6. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add Phase 2.5 OpenAPI + codegen → Align REST layer with constitution
3. Add US1 → Test independently → Deploy/Demo (MVP!)
4. Add US2 → Assign/unassign works → Deploy/Demo
5. Add US3 → Full execute flow → Deploy/Demo
6. Add US4 → Cancel/reject → Deploy/Demo
7. Add US5 → Update → Deploy/Demo
8. Polish → E2E tests pass, Swagger live → Final delivery

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- TDD is mandatory for domain and application layers per Constitution Principle V
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
