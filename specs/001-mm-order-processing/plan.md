# Implementation Plan: Money Market Order Processing

**Branch**: `001-mm-order-processing` | **Date**: 2026-04-28 | **Spec**: [spec.md](spec.md)
**Input**: User description for Money Market Order Processing V1

## Summary

Build an internal application that receives Money Market orders from an external Portfolio Management system via REST API and supports the Trader operational workflow (assign, update, execute, cancel, reject). The backend uses Spring Boot 4.0.5 with strict Hexagonal Architecture inside a Modular Monolith. The frontend uses Angular 21 with feature-based standalone components. Market dealing itself happens outside the system; execution means the Trader records the outcome. The application generates `ContractNumber` and `DealingReference` at execution time and models integration with the downstream Deposits system as an outbound port.

## Technical Context

**Language/Version**: Java 25 (LTS, GA September 2025)
**Primary Dependencies**: Spring Boot 4.0.5 (adapters and bootstrap only), Angular 21.2.9 (frontend)  
**API contract**: OpenAPI 3 (YAML or JSON) under feature `contracts/` is canonical; REST adapters MUST be driven by OpenAPI Generator (or equivalent) — contract-first (see `.specify/memory/constitution.md` v1.3.0). Prose [contracts/api-v1.md](contracts/api-v1.md) documents the same surface for readers; drift MUST NOT exist between OpenAPI and runtime.  
**Storage**: PostgreSQL 16 with Flyway migrations  
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (backend); Jasmine/Karma + Cypress (frontend)  
**Target Platform**: JVM server (Linux/macOS) + modern browser  
**Project Type**: Web application (Modular Monolith)  
**Build Tool**: Maven (multi-module) — see [research.md](research.md) for rationale  
**Performance Goals**: Internal application, single-digit concurrent Traders in V1; no specific throughput targets  
**Constraints**: Financial-grade decimal precision (`BigDecimal`); idempotent intake; all business rules in domain core  
**Scale/Scope**: ~10 screens, ~11 REST endpoints, 1 aggregate, 1 database

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*


| #    | Principle                                 | Status | Evidence                                                                                                                   |
| ---- | ----------------------------------------- | ------ | -------------------------------------------------------------------------------------------------------------------------- |
| I    | Hexagonal Architecture & Modular Monolith | ✅ PASS | 6 Maven modules with strict dependency direction; domain and application have zero Spring dependencies                     |
| II   | Domain Integrity                          | ✅ PASS | All invariants (OrderType/OrderOperation combos, tenors, notice periods, ValueDate, decimal precision) enforced in domain  |
| III  | Workflow Discipline                       | ✅ PASS | Explicit state machine with 5 statuses, 5 transitions, validated in domain aggregate                                       |
| IV   | Idempotency & Integration Boundaries      | ✅ PASS | ReceiveOrder idempotent via ExternalOrderReference UNIQUE constraint; Deposits modeled as outbound port                    |
| V    | Execution Rules                           | ✅ PASS | ExecuteOrder captures ExecutedRate, Counterparty; generates DealingReference, ContractNumber; records system ExecutionTime |
| VI   | API & UI Consistency                      | ✅ PASS | Backend single source of truth; Angular performs UX hints only; separate Term/OnCall views                                 |
| VII  | Testing Discipline                        | ✅ PASS | TDD for domain/application; layered strategy: domain → application → adapter → e2e                                         |
| VIII | Auditability & Security                   | ✅ PASS | `order_audit_log` table; all 8 auditable events covered; actor identity recorded                                           |
| IX   | Simplicity & Learning Focus               | ✅ PASS | No event sourcing, no CQRS, no generic frameworks; explicit code over abstractions                                         |
| UL   | Ubiquitous Language                       | ✅ PASS | All 25 approved terms used consistently; no synonyms introduced                                                            |
| CF   | Contract-first REST (OpenAPI)             | ⚠️ GAP  | Constitution v1.3.0: `openapi.yaml`/`openapi.json` + codegen not yet wired; REST layer currently hand-written against [contracts/api-v1.md](contracts/api-v1.md) — migrate per constitution Sync Impact follow-up |


No violations of principles I–IX beyond documented OpenAPI adoption gap. Complexity Tracking table not needed.

## 1. Monorepo Structure

```text
mmx/                                    ← repository root
├── .specify/                           ← Spec Kit configuration and templates
├── specs/
│   └── 001-mm-order-processing/
│       ├── spec.md
│       ├── plan.md                     ← this file
│       ├── research.md
│       ├── data-model.md
│       ├── contracts/
│       │   └── api-v1.md
│       ├── quickstart.md
│       └── tasks.md                    ← generated by /speckit.tasks
├── backend/
│   ├── pom.xml                         ← Maven parent POM (reactor)
│   ├── mmx-domain/
│   ├── mmx-application/
│   ├── mmx-adapter-in-rest/
│   ├── mmx-adapter-out-persistence/
│   ├── mmx-adapter-out-integration/
│   └── mmx-bootstrap/
├── frontend/
│   ├── angular.json
│   ├── package.json
│   └── src/
└── docs/                               ← optional shared technical docs
```

**Why one Spec Kit project governs both frontend and backend**: The Money Market Order Processing system is a single product with one domain, one ubiquitous language, and one set of business rules. The backend and frontend are deployment artifacts of the same bounded context. A single specification, plan, and task list ensures that API contracts, domain invariants, and UI behavior stay synchronized. Splitting specifications per artifact would create artificial boundaries that invite inconsistency.

## 2. Backend Module Structure

### Module Dependency Graph

```text
mmx-bootstrap
  ├── mmx-adapter-in-rest
  │     └── mmx-application
  │           └── mmx-domain
  ├── mmx-adapter-out-persistence
  │     └── mmx-application
  └── mmx-adapter-out-integration
        └── mmx-application
```

Dependencies point **inward only**. `mmx-domain` has zero external dependencies beyond Java standard library. `mmx-application` depends only on `mmx-domain`. Adapters depend on `mmx-application` (and transitively on `mmx-domain`) plus their respective infrastructure libraries. `mmx-bootstrap` assembles everything and owns Spring Boot configuration.

### Package Layout

```text
backend/
├── pom.xml
├── mmx-domain/
│   ├── pom.xml                                    ← no Spring, no JPA, no infrastructure
│   └── src/main/java/com/mmx/order/domain/
│       ├── model/
│       │   ├── MoneyMarketOrder.java              ← aggregate root
│       │   ├── OrderType.java                     ← enum: TERM, ON_CALL
│       │   ├── OrderOperation.java                   ← enum: SUBSCRIPTION, INCREASE, DECREASE, REDEMPTION
│       │   ├── OrderStatus.java                   ← enum with transition validation
│       │   ├── Tenor.java                         ← enum: _1W, _2W, _1M, _3M, _6M, _1Y
│       │   ├── NoticePeriod.java                  ← enum: _24H, _48H
│       │   ├── Assignment.java                    ← value object (`record`)
│       │   ├── ExecutionDetails.java              ← value object (`record`)
│       │   ├── ExternalOrderReference.java        ← value object (`record`, typed wrapper)
│       │   ├── ContractNumber.java                ← value object (`record`, typed wrapper)
│       │   ├── DealingReference.java              ← value object (`record`, typed wrapper)
│       │   ├── PortfolioNumber.java               ← value object (`record`, typed wrapper)
│       │   └── TraderId.java                      ← value object (`record`, typed wrapper)
│       └── exception/
│           ├── InvalidOrderException.java
│           ├── InvalidStatusTransitionException.java
│           └── OrderNotFoundException.java
│
├── mmx-application/
│   ├── pom.xml                                    ← depends on mmx-domain only
│   └── src/main/java/com/mmx/order/application/
│       ├── port/
│       │   ├── in/
│       │   │   ├── ReceiveOrderUseCase.java
│       │   │   ├── ListReceivedTermOrdersUseCase.java
│       │   │   ├── ListReceivedOnCallOrdersUseCase.java
│       │   │   ├── AssignOrderUseCase.java
│       │   │   ├── UnassignOrderUseCase.java
│       │   │   ├── ListAssignedOrdersUseCase.java
│       │   │   ├── GetOrderDetailsUseCase.java
│       │   │   ├── UpdateAssignedOrderUseCase.java
│       │   │   ├── ExecuteOrderUseCase.java
│       │   │   ├── CancelOrderUseCase.java
│       │   │   └── RejectOrderUseCase.java
│       │   └── out/
│       │       ├── OrderRepository.java           ← persistence port
│       │       ├── ReferenceGenerator.java        ← generates DealingReference + ContractNumber
│       │       ├── AuditLogger.java               ← audit port
│       │       ├── DepositsGateway.java           ← integration boundary (no-op in V1)
│       │       └── Clock.java                     ← time abstraction for testability
│       ├── service/
│       │   ├── ReceiveOrderService.java           ← implements ReceiveOrderUseCase
│       │   ├── OrderQueryService.java             ← implements List* and GetOrderDetails
│       │   ├── AssignmentService.java             ← implements Assign/Unassign
│       │   ├── UpdateOrderService.java            ← implements UpdateAssignedOrder
│       │   ├── ExecuteOrderService.java           ← implements ExecuteOrder
│       │   └── OrderLifecycleService.java         ← implements Cancel/Reject
│       └── command/
│           ├── ReceiveOrderCommand.java
│           ├── AssignOrderCommand.java
│           ├── UnassignOrderCommand.java
│           ├── UpdateOrderCommand.java
│           ├── ExecuteOrderCommand.java
│           ├── CancelOrderCommand.java
│           └── RejectOrderCommand.java
│
├── mmx-adapter-in-rest/
│   ├── pom.xml                                    ← depends on mmx-application + Spring Web
│   └── src/main/java/com/mmx/order/adapter/in/rest/
│       ├── OrderIntakeController.java             ← POST /api/v1/orders; implements generated IntakeApi
│       ├── OrderManagementController.java         ← Trader-facing endpoints; implements generated OrdersApi
│       ├── generated/                             ← OpenAPI codegen output (not in repo): model + *Api under target/
│       ├── mapper/
│       │   └── OrderRestMapper.java
│       └── validation/
│           └── (Bean Validation from OpenAPI/codegen on generated models; adapter-specific constraints only if documented)
│
├── mmx-adapter-out-persistence/
│   ├── pom.xml                                    ← depends on mmx-application + Spring Data JPA
│   └── src/main/java/com/mmx/order/adapter/out/persistence/
│       ├── JpaOrderRepository.java                ← implements OrderRepository port
│       ├── JpaAuditLogger.java                    ← implements AuditLogger port
│       ├── entity/
│       │   ├── OrderEntity.java                   ← JPA entity (not exposed to domain)
│       │   └── AuditLogEntity.java
│       ├── mapper/
│       │   └── OrderPersistenceMapper.java
│       └── repository/
│           ├── SpringDataOrderRepository.java     ← Spring Data JPA interface
│           └── SpringDataAuditLogRepository.java
│
├── mmx-adapter-out-integration/
│   ├── pom.xml                                    ← depends on mmx-application
│   └── src/main/java/com/mmx/order/adapter/out/integration/
│       ├── UuidReferenceGenerator.java            ← implements ReferenceGenerator port
│       ├── NoOpDepositsGateway.java               ← implements DepositsGateway (V1 no-op)
│       └── SystemClock.java                       ← implements Clock port
│
└── mmx-bootstrap/
    ├── pom.xml                                    ← depends on all adapter modules
    └── src/main/java/com/mmx/order/
        ├── MmxApplication.java                    ← @SpringBootApplication
        └── config/
            └── OrderModuleConfiguration.java      ← wires ports to adapters via @Bean
```

### Naming Conventions


| Artifact            | Pattern                                            | Example                                 |
| ------------------- | -------------------------------------------------- | --------------------------------------- |
| Aggregate root      | `{Name}.java` in `domain/model/`                   | `MoneyMarketOrder.java`                 |
| Value object        | `{Name}.java` in `domain/model/`                   | `ExternalOrderReference.java`           |
| Enum                | `{Name}.java` in `domain/model/`                   | `OrderType.java`                        |
| Domain exception    | `{Condition}Exception.java` in `domain/exception/` | `InvalidStatusTransitionException.java` |
| Inbound port        | `{Action}UseCase.java` in `port/in/`               | `ReceiveOrderUseCase.java`              |
| Outbound port       | `{Capability}.java` in `port/out/`                 | `OrderRepository.java`                  |
| Application service | `{Concern}Service.java` in `service/`              | `ExecuteOrderService.java`              |
| Command object      | `{Action}Command.java` in `command/`               | `ExecuteOrderCommand.java`              |
| REST controller     | `{Concern}Controller.java`                         | `OrderManagementController.java`        |
| REST wire types     | Generated from OpenAPI into `adapter.in.rest.generated.model` / `generated.api` | `ReceiveOrderRequest` (generated), `IntakeApi` |
| JPA entity          | `{Name}Entity.java`                                | `OrderEntity.java`                      |
| Persistence adapter | `Jpa{Name}.java`                                   | `JpaOrderRepository.java`               |
| Integration adapter | `{Implementation}{Port}.java`                      | `UuidReferenceGenerator.java`           |


### How Hexagonal Architecture Works in This Project

1. **Domain layer** (`mmx-domain`): Contains the `MoneyMarketOrder` aggregate root, value objects (implemented as Java `record` types per constitution), enums, and domain exceptions. This module has **zero dependencies** on any framework. It is pure Java. All business invariants (OrderType/OrderOperation validation, status transitions, ValueDate rules, decimal precision) live here.
2. **Application layer** (`mmx-application`): Defines inbound ports (use case interfaces) and outbound ports (repository, reference generator, audit logger, clock). Application services implement inbound ports by orchestrating domain logic and calling outbound ports. This module depends only on `mmx-domain`. It has **no Spring annotations**.
3. **Inbound adapters** (`mmx-adapter-in-rest`): REST controllers that implement OpenAPI-generated `*Api` interfaces, validate input (Bean Validation on generated models), map generated request types to commands, and invoke use cases. They do not contain business logic. Spring Web is used here.
4. **Outbound adapters** (`mmx-adapter-out-persistence`, `mmx-adapter-out-integration`): Implement the outbound ports defined by the application layer. JPA entities, Spring Data repositories, and mapper classes live here. The domain never sees these implementations.
5. **Bootstrap** (`mmx-bootstrap`): The Spring Boot entry point that wires everything together via `@Configuration` classes. This is the only module that knows about all other modules.

## 3. Initial Domain Model

See [data-model.md](data-model.md) for the full detailed model. Summary below.

### Aggregate Root: `MoneyMarketOrder`

The single aggregate root that encapsulates the entire order lifecycle.

**Core fields:**

- `id: UUID` — internal identity
- `externalOrderReference: ExternalOrderReference` — unique business key from Portfolio Management
- `orderType: OrderType` — TERM or ON_CALL
- `orderOperation: OrderOperation` — SUBSCRIPTION, INCREASE, DECREASE, REDEMPTION
- `portfolioNumber: PortfolioNumber`
- `currency: String` (ISO 4217)
- `amount: BigDecimal`
- `valueDate: LocalDate`
- `minimumRate: BigDecimal`
- `tenor: Tenor` — nullable, required for TERM
- `noticePeriod: NoticePeriod` — nullable, required for ON_CALL
- `sourceContractNumber: ContractNumber` — nullable, required for INCREASE/DECREASE/REDEMPTION
- `desiredCounterpartyComment: String` — nullable

**Lifecycle fields:**

- `status: OrderStatus`
- `assignment: Assignment` — nullable (traderId + assignedAt)
- `executionDetails: ExecutionDetails` — nullable (executedRate, counterparty, executionTime, dealingReference, generatedContractNumber)
- `createdAt: Instant`
- `updatedAt: Instant`

### OrderType vs OrderOperation

`OrderType` classifies the financial instrument: **Term** (fixed duration deposit) vs **OnCall** (demand deposit with notice period). `OrderOperation` describes what the order requests: **Subscription** (new), **Increase** (add amount), **Decrease** (reduce amount), **Redemption** (close).

The aggregate enforces the allowed combinations:

- `TERM` → only `SUBSCRIPTION`
- `ON_CALL` → `SUBSCRIPTION`, `INCREASE`, `DECREASE`, `REDEMPTION`

### Key Value Objects

Domain value objects are Java `record` types with validation in compact constructors (see constitution II).

- **ExternalOrderReference**: Typed wrapper around `String` (`record`). The idempotency key for order intake. Unique across the system.
- **ContractNumber**: Typed wrapper (`record`). Used in two contexts: `sourceContractNumber` (existing contract for lifecycle actions) and `generatedContractNumber` (created at execution for Deposits integration).
- **DealingReference**: Typed wrapper (`record`). System-generated at execution time.
- **Assignment**: Composite record: `traderId: TraderId` + `assignedAt: Instant`. Cleared on unassign.
- **ExecutionDetails**: Composite record: `executedRate: BigDecimal` + `counterparty: String` + `executionTime: Instant` + `dealingReference: DealingReference` + `generatedContractNumber: ContractNumber`. Immutable once set.

### Status State Machine

```text
                    ┌──────────┐
         ┌─cancel──│ RECEIVED │──reject─┐
         ▼         └────┬─────┘         ▼
   ┌───────────┐        │         ┌──────────┐
   │ CANCELLED │     assign       │ REJECTED │
   └───────────┘        │         └──────────┘
                   ┌────▼─────┐
            ┌──────│ ASSIGNED │
         unassign  └────┬─────┘
            │        execute
            ▼           │
      ┌──────────┐ ┌────▼─────┐
      │ RECEIVED │ │ EXECUTED │
      └──────────┘ └──────────┘
```

## 4. V1 Use Cases

### 4.1 ReceiveOrder


| Aspect                   | Detail                                                                                                                                                                                                                                                                                                                                                              |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Intent**               | Accept a MoneyMarketOrder from the Portfolio Management system                                                                                                                                                                                                                                                                                                      |
| **Inputs**               | `ReceiveOrderCommand { externalOrderReference, orderType, orderOperation, portfolioNumber, currency, amount, valueDate, minimumRate, tenor?, noticePeriod?, sourceContractNumber?, desiredCounterpartyComment? }`                                                                                                                                                   |
| **Output**               | `OrderId` + `OrderStatus.RECEIVED`                                                                                                                                                                                                                                                                                                                                  |
| **Idempotency**          | If `externalOrderReference` already exists, return the existing order without modification. Do not fail.                                                                                                                                                                                                                                                            |
| **Business validations** | OrderType ∈ {TERM, ON_CALL}; OrderOperation valid for OrderType; Subscription requires portfolioNumber, currency, amount, valueDate, minimumRate + (tenor for TERM, noticePeriod for ON_CALL); Increase/Decrease/Redemption requires sourceContractNumber; ValueDate ≥ today + 2 days; Amount > 0; MinimumRate ≥ 0; Tenor ∈ allowed set; NoticePeriod ∈ allowed set |
| **Authorization**        | System caller (Portfolio Management). V1: no fine-grained API key validation.                                                                                                                                                                                                                                                                                       |
| **Status transition**    | → RECEIVED (new order) or no transition (idempotent return)                                                                                                                                                                                                                                                                                                         |
| **Failure scenarios**    | Invalid payload → 400 with validation errors; constraint violation → 422                                                                                                                                                                                                                                                                                            |
| **Audit events**         | `ORDER_RECEIVED` (new) or `DUPLICATE_RECEIVE_IGNORED` (idempotent)                                                                                                                                                                                                                                                                                                  |


### 4.2 ListReceivedTermOrders


| Aspect                   | Detail                                                              |
| ------------------------ | ------------------------------------------------------------------- |
| **Intent**               | Display all Term orders currently in Received status                |
| **Inputs**               | Pagination parameters (optional)                                    |
| **Output**               | List of `OrderSummary` where `orderType=TERM` and `status=RECEIVED` |
| **Business validations** | None                                                                |
| **Authorization**        | Any authenticated Trader                                            |
| **Status transition**    | None (read-only)                                                    |
| **Failure scenarios**    | None                                                                |
| **Audit events**         | None                                                                |


### 4.3 ListReceivedOnCallOrders


| Aspect                   | Detail                                                                 |
| ------------------------ | ---------------------------------------------------------------------- |
| **Intent**               | Display all OnCall orders currently in Received status                 |
| **Inputs**               | Pagination parameters (optional)                                       |
| **Output**               | List of `OrderSummary` where `orderType=ON_CALL` and `status=RECEIVED` |
| **Business validations** | None                                                                   |
| **Authorization**        | Any authenticated Trader                                               |
| **Status transition**    | None (read-only)                                                       |
| **Failure scenarios**    | None                                                                   |
| **Audit events**         | None                                                                   |


### 4.4 AssignOrder


| Aspect                   | Detail                                                             |
| ------------------------ | ------------------------------------------------------------------ |
| **Intent**               | Assign a Received order to the requesting Trader                   |
| **Inputs**               | `AssignOrderCommand { orderId, traderId }`                         |
| **Output**               | Updated order with `status=ASSIGNED`                               |
| **Business validations** | Order must exist; order must be in RECEIVED status                 |
| **Authorization**        | Any authenticated Trader                                           |
| **Status transition**    | RECEIVED → ASSIGNED                                                |
| **Failure scenarios**    | Order not found → 404; order not in RECEIVED status → 409 Conflict |
| **Audit events**         | `ORDER_ASSIGNED` (orderId, traderId, timestamp)                    |


### 4.5 UnassignOrder


| Aspect                   | Detail                                                                                    |
| ------------------------ | ----------------------------------------------------------------------------------------- |
| **Intent**               | Remove Trader assignment, returning order to Received                                     |
| **Inputs**               | `UnassignOrderCommand { orderId, traderId }`                                              |
| **Output**               | Updated order with `status=RECEIVED`, `assignment=null`                                   |
| **Business validations** | Order must exist; order must be in ASSIGNED status; only the assigned Trader can unassign |
| **Authorization**        | Only the currently assigned Trader                                                        |
| **Status transition**    | ASSIGNED → RECEIVED                                                                       |
| **Failure scenarios**    | Order not found → 404; order not ASSIGNED → 409; different Trader → 403                   |
| **Audit events**         | `ORDER_UNASSIGNED` (orderId, traderId, timestamp)                                         |


### 4.6 ListAssignedOrders


| Aspect                   | Detail                                                                            |
| ------------------------ | --------------------------------------------------------------------------------- |
| **Intent**               | Display all orders assigned to the requesting Trader                              |
| **Inputs**               | `traderId` (from authentication context), pagination parameters                   |
| **Output**               | List of `OrderSummary` where `status=ASSIGNED` and `assignment.traderId=traderId` |
| **Business validations** | None                                                                              |
| **Authorization**        | Authenticated Trader sees only their own assignments                              |
| **Status transition**    | None (read-only)                                                                  |
| **Failure scenarios**    | None                                                                              |
| **Audit events**         | None                                                                              |


### 4.7 GetOrderDetails


| Aspect                   | Detail                                                                               |
| ------------------------ | ------------------------------------------------------------------------------------ |
| **Intent**               | View full details of a specific order                                                |
| **Inputs**               | `orderId`                                                                            |
| **Output**               | `OrderDetails` with all fields including assignment and execution details if present |
| **Business validations** | Order must exist                                                                     |
| **Authorization**        | Any authenticated Trader                                                             |
| **Status transition**    | None (read-only)                                                                     |
| **Failure scenarios**    | Order not found → 404                                                                |
| **Audit events**         | None                                                                                 |


### 4.8 UpdateAssignedOrder


| Aspect                   | Detail                                                                                                                                                              |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Intent**               | Modify mutable fields of an assigned order before execution                                                                                                         |
| **Inputs**               | `UpdateOrderCommand { orderId, traderId, amount?, minimumRate?, valueDate?, desiredCounterpartyComment? }`                                                          |
| **Output**               | Updated order                                                                                                                                                       |
| **Business validations** | Order must exist; must be ASSIGNED; only assigned Trader can update; if valueDate changed → ≥ today + 2 days; if amount changed → > 0; if minimumRate changed → ≥ 0 |
| **Authorization**        | Only the assigned Trader                                                                                                                                            |
| **Status transition**    | None (stays ASSIGNED)                                                                                                                                               |
| **Failure scenarios**    | Not found → 404; not ASSIGNED → 409; wrong Trader → 403; invalid values → 400                                                                                       |
| **Audit events**         | `ORDER_UPDATED` (orderId, traderId, timestamp, changed fields)                                                                                                      |


### 4.9 ExecuteOrder


| Aspect                   | Detail                                                                                                                                                      |
| ------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Intent**               | Record execution data after market dealing happened externally                                                                                              |
| **Inputs**               | `ExecuteOrderCommand { orderId, traderId, executedRate, counterparty }`                                                                                     |
| **Output**               | Executed order with `status=EXECUTED`, generated `DealingReference`, generated `ContractNumber`, system `ExecutionTime`                                     |
| **Business validations** | Order must exist; must be ASSIGNED; only assigned Trader can execute; executedRate must be provided and ≥ 0; counterparty must be provided and non-blank    |
| **Authorization**        | Only the assigned Trader                                                                                                                                    |
| **Status transition**    | ASSIGNED → EXECUTED                                                                                                                                         |
| **System actions**       | Generate `DealingReference` via `ReferenceGenerator` port; generate `ContractNumber` via `ReferenceGenerator` port; record `ExecutionTime` via `Clock` port |
| **Failure scenarios**    | Not found → 404; not ASSIGNED → 409; wrong Trader → 403; missing execution data → 400                                                                       |
| **Audit events**         | `ORDER_EXECUTED` (orderId, traderId, timestamp, dealingReference, contractNumber, executedRate, counterparty)                                               |


### 4.10 CancelOrder


| Aspect                   | Detail                                           |
| ------------------------ | ------------------------------------------------ |
| **Intent**               | Cancel a received order                          |
| **Inputs**               | `CancelOrderCommand { orderId, traderId }`       |
| **Output**               | Order with `status=CANCELLED`                    |
| **Business validations** | Order must exist; must be in RECEIVED status     |
| **Authorization**        | Any authenticated Trader                         |
| **Status transition**    | RECEIVED → CANCELLED                             |
| **Failure scenarios**    | Not found → 404; not RECEIVED → 409              |
| **Audit events**         | `ORDER_CANCELLED` (orderId, traderId, timestamp) |


### 4.11 RejectOrder


| Aspect                   | Detail                                                  |
| ------------------------ | ------------------------------------------------------- |
| **Intent**               | Reject a received order with an optional reason         |
| **Inputs**               | `RejectOrderCommand { orderId, traderId, reason? }`     |
| **Output**               | Order with `status=REJECTED`                            |
| **Business validations** | Order must exist; must be in RECEIVED status            |
| **Authorization**        | Any authenticated Trader                                |
| **Status transition**    | RECEIVED → REJECTED                                     |
| **Failure scenarios**    | Not found → 404; not RECEIVED → 409                     |
| **Audit events**         | `ORDER_REJECTED` (orderId, traderId, timestamp, reason) |


## 5. Idempotent Intake Strategy

### Mechanism

`ReceiveOrder` uses `ExternalOrderReference` as the idempotency key.

1. The `ReceiveOrderService` calls `OrderRepository.findByExternalOrderReference(ref)`.
2. If an order already exists with that reference, the service returns the existing order's ID and RECEIVED status. No modification, no error. This is a **successful idempotent response**.
3. If no order exists, the service creates and persists the new `MoneyMarketOrder`.

### Persistence Guarantees

- The `external_order_reference` column in the `money_market_order` table has a `UNIQUE` constraint.
- If a race condition causes two concurrent inserts for the same reference, the database constraint prevents duplication. The second transaction catches the unique constraint violation, re-fetches the existing order, and returns it idempotently.

### API Response Semantics


| Scenario                         | HTTP Status       | Body                                                          |
| -------------------------------- | ----------------- | ------------------------------------------------------------- |
| New order created                | `201 Created`     | `{ "orderId": "...", "status": "RECEIVED" }`                  |
| Duplicate reference (idempotent) | `200 OK`          | `{ "orderId": "...", "status": "RECEIVED" }` (existing order) |
| Invalid payload                  | `400 Bad Request` | `{ "errors": [...] }`                                         |


The caller (Portfolio Management) can distinguish new creation (`201`) from idempotent return (`200`) and implement retry logic safely.

### Audit

- New order: `ORDER_RECEIVED` event logged.
- Duplicate: `DUPLICATE_RECEIVE_IGNORED` event logged (with the existing orderId for traceability).

## 6. REST API Design

**Contract-first (constitutional requirement, v1.3.0)**: The authoritative HTTP contract is the **OpenAPI 3** artifact under this feature’s `contracts/` directory (to be added: e.g. `openapi.yaml`). It MUST stay equivalent to the human-readable reference [contracts/api-v1.md](contracts/api-v1.md). The `mmx-adapter-in-rest` module MUST consume **generated** request/response types and, where applicable, generated server interfaces from that OpenAPI file; API changes start by editing OpenAPI and regenerating — not by editing hand-written DTOs first.

See [contracts/api-v1.md](contracts/api-v1.md) for complete request/response payloads (narrative mirror of the OpenAPI). Summary:

### Endpoints


| Method | Path                                | Use Case                 | Description                      |
| ------ | ----------------------------------- | ------------------------ | -------------------------------- |
| `POST` | `/api/v1/orders`                    | ReceiveOrder             | Intake from Portfolio Management |
| `GET`  | `/api/v1/orders/term/received`      | ListReceivedTermOrders   | Term orders in Received status   |
| `GET`  | `/api/v1/orders/oncall/received`    | ListReceivedOnCallOrders | OnCall orders in Received status |
| `GET`  | `/api/v1/orders/{orderId}`          | GetOrderDetails          | Full order detail                |
| `POST` | `/api/v1/orders/{orderId}/assign`   | AssignOrder              | Assign to Trader                 |
| `POST` | `/api/v1/orders/{orderId}/unassign` | UnassignOrder            | Remove assignment                |
| `GET`  | `/api/v1/orders/assigned`           | ListAssignedOrders       | Current Trader's assignments     |
| `PUT`  | `/api/v1/orders/{orderId}`          | UpdateAssignedOrder      | Update before execution          |
| `POST` | `/api/v1/orders/{orderId}/execute`  | ExecuteOrder             | Record execution                 |
| `POST` | `/api/v1/orders/{orderId}/cancel`   | CancelOrder              | Cancel order                     |
| `POST` | `/api/v1/orders/{orderId}/reject`   | RejectOrder              | Reject order                     |


### Status Codes


| Code  | Meaning                                                   |
| ----- | --------------------------------------------------------- |
| `200` | Successful read or idempotent duplicate                   |
| `201` | Order successfully created                                |
| `400` | Validation error (format, missing fields)                 |
| `403` | Forbidden (not the assigned Trader)                       |
| `404` | Order not found                                           |
| `409` | Conflict (invalid status transition, assignment conflict) |


### Validation Error Format

```json
{
  "error": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    { "field": "amount", "message": "must be greater than 0" },
    { "field": "valueDate", "message": "must be at least 2 days in the future" }
  ]
}
```

### Versioning Strategy

V1 uses path-based versioning (`/api/v1/`). This is the simplest approach and sufficient for an internal application. If V2 introduces breaking changes, `/api/v2/` endpoints can coexist temporarily.

## 7. Persistence Strategy

### Tables

#### `money_market_order`


| Column                         | Type            | Constraints      |
| ------------------------------ | --------------- | ---------------- |
| `id`                           | `UUID`          | PK               |
| `external_order_reference`     | `VARCHAR(100)`  | NOT NULL, UNIQUE |
| `order_type`                   | `VARCHAR(20)`   | NOT NULL         |
| `order_operation`              | `VARCHAR(20)`   | NOT NULL         |
| `portfolio_number`             | `VARCHAR(50)`   | NOT NULL         |
| `currency`                     | `VARCHAR(3)`    | NOT NULL         |
| `amount`                       | `DECIMAL(18,2)` | NOT NULL         |
| `value_date`                   | `DATE`          | NOT NULL         |
| `minimum_rate`                 | `DECIMAL(12,8)` | NOT NULL         |
| `tenor`                        | `VARCHAR(10)`   | NULLABLE         |
| `notice_period`                | `VARCHAR(10)`   | NULLABLE         |
| `source_contract_number`       | `VARCHAR(50)`   | NULLABLE         |
| `desired_counterparty_comment` | `TEXT`          | NULLABLE         |
| `status`                       | `VARCHAR(20)`   | NOT NULL         |
| `assigned_trader_id`           | `VARCHAR(100)`  | NULLABLE         |
| `assigned_at`                  | `TIMESTAMPTZ`   | NULLABLE         |
| `executed_rate`                | `DECIMAL(12,8)` | NULLABLE         |
| `counterparty`                 | `VARCHAR(200)`  | NULLABLE         |
| `execution_time`               | `TIMESTAMPTZ`   | NULLABLE         |
| `dealing_reference`            | `VARCHAR(50)`   | NULLABLE         |
| `generated_contract_number`    | `VARCHAR(50)`   | NULLABLE         |
| `rejection_reason`             | `TEXT`          | NULLABLE         |
| `created_at`                   | `TIMESTAMPTZ`   | NOT NULL         |
| `updated_at`                   | `TIMESTAMPTZ`   | NOT NULL         |


#### `order_audit_log`


| Column       | Type           | Constraints                          |
| ------------ | -------------- | ------------------------------------ |
| `id`         | `BIGSERIAL`    | PK                                   |
| `order_id`   | `UUID`         | NOT NULL, FK → money_market_order.id |
| `event_type` | `VARCHAR(50)`  | NOT NULL                             |
| `actor_id`   | `VARCHAR(100)` | NOT NULL                             |
| `event_time` | `TIMESTAMPTZ`  | NOT NULL                             |
| `details`    | `JSONB`        | NULLABLE                             |


### Indexes

```sql
CREATE UNIQUE INDEX idx_order_ext_ref ON money_market_order (external_order_reference);
CREATE INDEX idx_order_status_type ON money_market_order (status, order_type);
CREATE INDEX idx_order_assigned_trader ON money_market_order (assigned_trader_id, status);
CREATE INDEX idx_order_value_date ON money_market_order (value_date);
CREATE INDEX idx_audit_order_id ON order_audit_log (order_id);
CREATE INDEX idx_audit_event_time ON order_audit_log (event_time);
```

### Flyway Migrations

- `V1__create_money_market_order_table.sql`
- `V2__create_order_audit_log_table.sql`
- `V3__add_indexes.sql`

### Generated References Storage

`dealing_reference` and `generated_contract_number` are stored as plain `VARCHAR` columns on the `money_market_order` table. They are populated only when `status = EXECUTED`. This avoids a separate table and keeps queries simple.

## 8. Execution and Reference Generation

### DealingReference Generation

- **Format**: `DL-{UUID}` (e.g., `DL-a3f2b8c1-7d4e-...`)
- **Where**: The format specification (prefix + structure) is a domain concern. The actual generation is delegated to the `ReferenceGenerator` outbound port.
- **Implementation**: `UuidReferenceGenerator` in `mmx-adapter-out-integration` generates a UUID and prepends the `DL-` prefix.

### ContractNumber Generation

- **Format**: `CN-{UUID}` (e.g., `CN-e9d1f4a2-8b3c-...`)
- **Where**: Same pattern as DealingReference. Domain defines the contract; adapter implements generation.
- **Implementation**: `UuidReferenceGenerator` generates a UUID and prepends the `CN-` prefix.

### What Belongs Where


| Concern                                             | Layer                              | Rationale              |
| --------------------------------------------------- | ---------------------------------- | ---------------------- |
| "A DealingReference must be generated at execution" | Domain (invariant)                 | Business rule          |
| "DealingReference format is DL-{UUID}"              | Domain (value object validation)   | Domain knowledge       |
| "Generate a new UUID"                               | Outbound port + adapter            | Infrastructure concern |
| "ExecutionTime is the system clock at confirmation" | Application service via Clock port | Testable time source   |


### ExecutionTime Sourcing

`ExecutionTime` is sourced from the `Clock` outbound port. In production, `SystemClock` returns `Instant.now()`. In tests, a `FixedClock` stub allows deterministic assertions.

## 9. Angular V1 Structure

### Routes


| Route              | Component                    | Description                               |
| ------------------ | ---------------------------- | ----------------------------------------- |
| `/term-orders`     | `TermOrderListComponent`     | Received Term orders                      |
| `/oncall-orders`   | `OnCallOrderListComponent`   | Received OnCall orders                    |
| `/assigned-orders` | `AssignedOrderListComponent` | Current Trader's assigned orders          |
| `/orders/:id`      | `OrderDetailsComponent`      | Full order detail with contextual actions |


### Feature-Based Structure

```text
frontend/src/app/
├── app.component.ts
├── app.routes.ts
├── core/
│   ├── api/
│   │   └── order-api.service.ts         ← typed HTTP client for all endpoints
│   ├── models/
│   │   ├── order.model.ts               ← OrderSummary, OrderDetails interfaces
│   │   ├── order-type.enum.ts
│   │   ├── order-action.enum.ts
│   │   └── order-status.enum.ts
│   └── interceptors/
│       └── error.interceptor.ts
├── features/
│   ├── term-orders/
│   │   ├── term-order-list.component.ts
│   │   ├── term-order-list.component.html
│   │   └── term-orders.routes.ts
│   ├── oncall-orders/
│   │   ├── oncall-order-list.component.ts
│   │   ├── oncall-order-list.component.html
│   │   └── oncall-orders.routes.ts
│   ├── assigned-orders/
│   │   ├── assigned-order-list.component.ts
│   │   ├── assigned-order-list.component.html
│   │   └── assigned-orders.routes.ts
│   └── order-details/
│       ├── order-details.component.ts
│       ├── order-details.component.html
│       ├── order-execution-form.component.ts
│       ├── order-execution-form.component.html
│       ├── order-update-form.component.ts
│       ├── order-update-form.component.html
│       └── order-details.routes.ts
└── shared/
    └── components/
        ├── order-table.component.ts     ← reusable table for order lists
        ├── status-badge.component.ts    ← status-colored badge
        └── confirm-dialog.component.ts  ← confirmation for destructive actions
```

### Actions by Status


| Status           | Available Actions         |
| ---------------- | ------------------------- |
| RECEIVED         | Assign, Cancel, Reject    |
| ASSIGNED (own)   | Unassign, Update, Execute |
| ASSIGNED (other) | View only                 |
| EXECUTED         | View only                 |
| CANCELLED        | View only                 |
| REJECTED         | View only                 |


### State Management

V1 uses Angular's built-in mechanisms: `HttpClient` calls in `OrderApiService`, component-local state, and `OnPush` change detection. No NgRx, no signals store, no state management library. Components call the API service directly and refresh data after mutations. This is intentionally simple per Constitution Principle IX.

## 10. Validation Strategy

### Layered Validation


| Layer                             | What is Validated                     | How                                                                                 |
| --------------------------------- | ------------------------------------- | ----------------------------------------------------------------------------------- |
| **REST adapter** (input boundary) | Field presence, format, type coercion | Bean Validation annotations on DTOs (`@NotBlank`, `@NotNull`, `@Positive`, `@Size`) |
| **Domain aggregate** (core)       | All business invariants               | Constructor and method guards in `MoneyMarketOrder` throwing domain exceptions      |


### Specific Rules


| Rule                                                 | REST Layer                       | Domain Layer                                                                 |
| ---------------------------------------------------- | -------------------------------- | ---------------------------------------------------------------------------- |
| Amount > 0                                           | `@Positive` on DTO               | `MoneyMarketOrder` constructor guard                                         |
| MinimumRate ≥ 0                                      | `@PositiveOrZero` on DTO         | Constructor guard                                                            |
| ValueDate ≥ today + 2                                | Not validated (date format only) | `MoneyMarketOrder.validateValueDate(today)`                                  |
| OrderType ∈ {TERM, ON_CALL}                          | Enum deserialization             | `OrderType` enum (invalid values fail JSON parsing)                          |
| OrderOperation valid for OrderType                   | Not validated                    | `MoneyMarketOrder.validateOrderOperationForType()`                           |
| Tenor ∈ allowed set                                  | Enum deserialization             | `Tenor` enum                                                                 |
| NoticePeriod ∈ allowed set                           | Enum deserialization             | `NoticePeriod` enum                                                          |
| Subscription requires specific fields                | `@NotNull` groups on DTO         | Constructor guard                                                            |
| Increase/Decrease/Redemption requires contractNumber | Not validated (nullable field)   | Constructor guard                                                            |
| Status transitions                                   | Not applicable                   | `OrderStatus.transitionTo(target)` throws `InvalidStatusTransitionException` |
| Only assigned Trader can update/execute/unassign     | Not applicable                   | Application service checks `order.isAssignedTo(traderId)`                    |


### Key Design Decision

The REST layer validates **syntactic correctness** (is the JSON well-formed? are required fields present?). The domain validates **semantic correctness** (is this OrderOperation allowed for this OrderType? is the ValueDate far enough in the future?). This prevents business rule duplication while giving API callers fast feedback on obviously malformed requests.

## 11. Test Strategy by Layer

### Domain Unit Tests (`mmx-domain`)

- **Framework**: JUnit 5 + AssertJ
- **No mocks**: Domain is pure logic; test directly against aggregate methods
- **Coverage targets**:
  - MoneyMarketOrder creation with all valid OrderType/OrderOperation combinations
  - Rejection of invalid OrderType/OrderOperation combinations
  - Tenor validation (valid set, rejected values)
  - NoticePeriod validation (valid set, rejected values)
  - ValueDate rule (≥ today + 2 days)
  - Amount and MinimumRate precision and bounds
  - All 5 status transitions (positive)
  - All invalid transitions (negative)
  - Assignment and unassignment rules
  - Subscription field requirements
  - Increase/Decrease/Redemption contractNumber requirement
  - ExecutionDetails immutability

### Application Tests (`mmx-application`)

- **Framework**: JUnit 5 + Mockito + AssertJ
- **Mocks**: All outbound ports (OrderRepository, ReferenceGenerator, AuditLogger, Clock)
- **Coverage targets**:
  - ReceiveOrderService: new order creation, idempotent duplicate handling
  - OrderQueryService: delegation to repository with correct filters
  - AssignmentService: assign success, unassign success, wrong Trader rejection
  - UpdateOrderService: valid update, unauthorized Trader, invalid status
  - ExecuteOrderService: full execution flow with generated references, missing data rejection
  - OrderLifecycleService: cancel from RECEIVED, reject from RECEIVED, invalid transitions
  - Audit event emission for all mutating use cases

### Persistence Integration Tests (`mmx-adapter-out-persistence`)

- **Framework**: JUnit 5 + Testcontainers (PostgreSQL) + Spring Boot Test
- **Coverage targets**:
  - OrderEntity ↔ MoneyMarketOrder mapping correctness
  - UNIQUE constraint on `external_order_reference`
  - Query by status + orderType
  - Query by assignedTraderId + status
  - AuditLogEntity persistence and retrieval
  - Flyway migrations run cleanly

### REST API Tests (`mmx-adapter-in-rest`)

- **Framework**: JUnit 5 + Spring MockMvc + AssertJ
- **Mocks**: Use case interfaces (application layer)
- **Coverage targets**:
  - All 11 endpoints: correct HTTP method, path, status codes
  - ReceiveOrder: 201 for new, 200 for duplicate
  - Validation error format (400 responses)
  - 404 for missing orders
  - 409 for invalid transitions
  - 403 for unauthorized Trader actions
  - DTO serialization/deserialization
  - OpenAPI contract compliance

### Angular Unit and Component Tests

- **Framework**: Jasmine + Karma (unit), Angular Testing Library (component)
- **Coverage targets**:
  - `OrderApiService`: HTTP call correctness (HttpClientTestingModule)
  - List components: render order table, handle empty state
  - Order details: show/hide action buttons based on status
  - Execution form: required field validation
  - Update form: field binding

### End-to-End Tests

- **Framework**: Cypress
- **Scope**: The most valuable Trader workflow
- **Scenarios**:
  1. Receive order → appears in Term/OnCall list → assign → appears in assigned list → execute → verify executed state
  2. Receive order → cancel → verify cancelled
  3. Receive order → assign → unassign → verify back in received list
  4. Duplicate receive → verify idempotent (same orderId returned)

## 12. Simplicity Decisions

### Why V1 Scope is Intentionally Narrow

This project serves as an introductory Spec-Driven Development exercise. The scope covers one bounded context (Order Processing), one aggregate (`MoneyMarketOrder`), one user role (Trader), and one integration point (REST API intake). This narrowness allows thorough exploration of Hexagonal Architecture, TDD, and specification-driven workflow without the distraction of distributed systems, complex authorization, or multiple bounded contexts.

### Explicit V1 Non-Goals


| Non-Goal                       | Rationale                                                                    |
| ------------------------------ | ---------------------------------------------------------------------------- |
| Manual order entry             | Keeps intake to a single channel; reduces UI complexity                      |
| Multiple user roles            | Single Trader role avoids RBAC framework overhead                            |
| Email notifications            | Outbound communication adds adapter complexity without Trader workflow value |
| Deposits integration execution | Modeled as port but no-op; avoids external system coupling                   |
| Event-driven architecture      | Synchronous request-response is sufficient for V1 volume                     |
| CQRS / read-model separation   | Single table with indexes is adequate                                        |
| Advanced search/filtering      | Basic list endpoints by status and type are sufficient                       |
| Batch operations               | Individual order processing; partial execution is forbidden anyway           |
| WebSocket real-time updates    | Polling or manual refresh is acceptable for V1                               |


### How the Design Avoids Overengineering

1. **No generic repository abstraction**: `OrderRepository` is a single, specific port—not a generic `Repository<T>`.
2. **No domain events bus**: Audit logging is a direct port call, not a pub/sub system.
3. **No DTO base classes or generic mappers**: Each mapper is explicit and purpose-built.
4. **No shared "common" or "utils" module**: Cross-cutting concerns live in the module that needs them.
5. **No custom annotation processors**: Standard Bean Validation suffices.
6. **No reactive stack**: Servlet-based Spring MVC is simpler and better documented.
7. **Single database, single schema**: No multi-tenancy, no schema-per-module.

## 13. Risks, Open Decisions, and Rejected Alternatives

### Assumptions for V1


| Assumption                                            | Impact if Wrong                                                           |
| ----------------------------------------------------- | ------------------------------------------------------------------------- |
| Single-digit concurrent Traders                       | If volume grows, queries may need optimization                            |
| Trader identity provided by HTTP header (X-Trader-Id) | Proper authentication (OAuth2/OIDC) deferred to V2                        |
| ValueDate "2 days in the future" means calendar days  | If business days are required, a business calendar service is needed      |
| Portfolio Management retries are infrequent           | If high-frequency retries occur, idempotency performance may need caching |


### V2 Extension Points


| Extension                           | How V1 Prepares                                                             |
| ----------------------------------- | --------------------------------------------------------------------------- |
| Manual order entry                  | `ReceiveOrderUseCase` already abstracts intake; a UI adapter can invoke it  |
| Outbound email                      | `DepositsGateway` pattern can be replicated for `NotificationGateway`       |
| Deposits integration                | `DepositsGateway` port exists; V2 implements a real adapter                 |
| Additional roles (Manager, Auditor) | Actor identity recorded in audit; authorization can be layered              |
| Advanced search                     | Persistence adapter can add query methods; use case layer remains stable    |
| Richer audit                        | `AuditLogger` port can be replaced with a more sophisticated implementation |


### Rejected Alternatives


| Alternative                        | Rejected Because                                                                                                                          |
| ---------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| Microservices                      | Adds network complexity, deployment overhead, and distributed tracing concerns that distract from learning SDD and Hexagonal Architecture |
| Frontend business rule duplication | Violates Constitution Principle VI; creates divergence risk                                                                               |
| Shared "common" module             | Becomes a dumping ground; violates Principle IX (simplicity)                                                                              |
| Event sourcing                     | Adds significant complexity for a V1 with simple state transitions                                                                        |
| Generic Repository pattern         | Obscures intent; a specific port per use case is clearer                                                                                  |
| Gradle                             | Groovy/Kotlin DSL adds learning overhead; Maven is more explicit for beginners (see [research.md](research.md))                           |
| Spring Security with OAuth2 in V1  | Premature for a learning project with a single Trader role; X-Trader-Id header suffices                                                   |


## 14. Phased Roadmap

### Phase 1: Core Domain, Intake, and Assignment

**Goal**: Orders can be received, listed, assigned, unassigned, cancelled, and rejected.

- Backend: domain model, ReceiveOrder use case, list use cases, assignment use cases, cancel/reject use cases
- Persistence: initial schema, Flyway migrations, JPA adapter
- REST: intake endpoint, management endpoints (assign, unassign, cancel, reject, lists)
- Frontend: Term order list, OnCall order list, assigned order list, assign/unassign/cancel/reject actions
- Tests: domain unit tests, application tests, persistence integration tests, REST tests

### Phase 2: Execution Workflow and Audit

**Goal**: Traders can update and execute orders. Audit trail is complete.

- Backend: UpdateAssignedOrder use case, ExecuteOrder use case, reference generation, audit logging
- Persistence: audit log table, reference columns
- REST: update and execute endpoints, order details endpoint
- Frontend: order detail view, update form, execution form
- Tests: execution flow domain tests, application tests, REST tests, initial e2e tests

### Phase 3 (V2): Extended Capabilities

**Goal**: Expand beyond V1 scope when the foundation is proven.

- Manual order entry (new inbound adapter/UI)
- Richer search and filtering (date ranges, counterparty, amounts)
- Real Deposits integration (implement `DepositsGateway` adapter)
- Outbound notification (new `NotificationGateway` port and adapter)
- Proper authentication (Spring Security with OAuth2/OIDC)
- Additional roles (Manager view, Auditor read-only access)
- Business calendar for ValueDate validation

## Complexity Tracking

No constitution violations detected. This table is intentionally empty.


| Violation | Why Needed | Simpler Alternative Rejected Because |
| --------- | ---------- | ------------------------------------ |
| —         | —          | —                                    |


