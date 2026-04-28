<!--
## Sync Impact Report

- **Version change**: 0.0.0 (template) → 1.0.0 (initial ratification)
- **Modified principles**: N/A (first ratification)
- **Added sections**:
  - Core Principles: I through IX (9 principles)
  - Ubiquitous Language (dedicated section)
  - Governance (fully defined)
- **Removed sections**: All template placeholders replaced
- **Templates requiring updates**:
  - `.specify/templates/plan-template.md` — ✅ no update needed (Constitution Check section is generic and will be filled at plan time)
  - `.specify/templates/spec-template.md` — ✅ no update needed (template structure compatible)
  - `.specify/templates/tasks-template.md` — ✅ no update needed (template structure compatible)
- **Follow-up TODOs**: None
-->

# Money Market Order Processing Constitution

## Core Principles

### I. Hexagonal Architecture and Modular Monolith

The application MUST follow strict Hexagonal Architecture (Ports & Adapters).

- The Domain layer and the Application layer MUST be framework-agnostic. They MUST NOT depend on Spring, Angular, JPA, SQL, HTTP, messaging, or any infrastructure library.
- Dependency direction MUST always point inward toward the domain core. No outward dependency from core to infrastructure is permitted.
- Inbound adapters (REST controllers, CLI handlers, UI components) MUST invoke application use cases only. They MUST NOT contain business logic.
- Outbound adapters (persistence, messaging, external APIs) MUST implement ports owned by the domain or application core. The core MUST NOT reference adapter implementations.
- No business rule may reside in controllers, persistence entities, JPA repositories, Angular components, or infrastructure mappers.
- The system MUST be implemented as a Modular Monolith. Microservice decomposition is explicitly out of scope.

**Rationale**: Hexagonal Architecture enforces a clean separation between business logic and technical infrastructure, making the domain testable in isolation and portable across frameworks. The Modular Monolith constraint avoids premature distributed-system complexity for a learning-focused product.

### II. Domain Integrity

All business rules MUST be enforced within the domain core.

- Only two `OrderType` values are allowed: **Term** and **OnCall**.
- **Term** orders allow only `OrderAction` = **Subscription**.
- **OnCall** orders allow only `OrderAction` = **Subscription**, **Increase**, **Decrease**, **Redemption**.
- Allowed Term tenors are exactly: **1W**, **2W**, **1M**, **3M**, **6M**, **1Y**. Any other value MUST be rejected.
- Allowed OnCall notice periods are exactly: **24H**, **48H**. Any other value MUST be rejected.
- A **Subscription** order MUST contain: `PortfolioNumber`, `ExternalOrderReference`, `OrderType`, `Currency`, `Amount`, `ValueDate`, `MinimumRate`, and either `NoticePeriod` (for OnCall) or `Tenor` (for Term).
- **Increase**, **Decrease**, and **Redemption** MUST reference an existing `ContractNumber`.
- `DesiredCounterpartyComment` MAY be provided at order reception.
- `Counterparty` is NOT fixed at order reception in V1; it is assigned by the Trader during execution.
- `ValueDate` MUST be at least two business days in the future. Orders with a past or same-day `ValueDate` MUST be rejected.
- Orders are processed all-or-nothing. Partial execution is forbidden.
- Monetary values and rate values MUST use exact decimal handling appropriate for financial applications (e.g., `BigDecimal` in Java). Floating-point types (`float`, `double`) MUST NOT be used for monetary or rate calculations.

**Rationale**: Encoding every business invariant in the domain core ensures correctness is guaranteed regardless of which adapter or entry point triggers the operation. Financial precision rules prevent rounding errors that could have regulatory or monetary impact.

### III. Workflow Discipline

The order lifecycle MUST follow an explicit, deterministic state machine.

- The only allowed `OrderStatus` values are: **Received**, **Assigned**, **Executed**, **Cancelled**, **Rejected**.
- An order MAY be assigned to only one Trader at a time.
- An order MAY be unassigned before execution, returning it to **Received** status.
- Only the assigned Trader MAY update an order, and only while the order is NOT in **Executed** status.
- **Cancelled** and **Rejected** are allowed only from **Received** status.
- **Executed** is allowed only through the explicit execution use case.
- All status transitions MUST be explicit, validated in the domain, and covered by automated tests.
- The allowed transitions are:
  - **Received** → **Assigned** (assign)
  - **Received** → **Cancelled** (cancel)
  - **Received** → **Rejected** (reject)
  - **Assigned** → **Received** (unassign)
  - **Assigned** → **Executed** (execute)
- Any transition not listed above MUST be rejected by the domain.
- The workflow MUST remain simple and deterministic in V1.

**Rationale**: An explicit state machine prevents invalid lifecycle transitions. Restricting cancel/reject to Received and execution to Assigned keeps the V1 workflow simple and auditable.

### IV. Idempotency and Integration Boundaries

- The `ReceiveOrder` use case MUST be idempotent using `ExternalOrderReference` supplied by the Portfolio Management system. Receiving the same `ExternalOrderReference` twice MUST NOT create a duplicate order.
- The application MUST prevent duplicate business creation for the same `ExternalOrderReference`.
- V1 order intake happens only through the REST API. Manual order entry is explicitly out of scope.
- Integration with the downstream **Deposits** application MUST be modeled as a clear boundary (outbound port), even though Deposits processing itself remains out of scope in V1.
- Future outbound email communication to counterparties is out of scope for V1 and MUST NOT complicate the current domain model.

**Rationale**: Idempotency is critical in an integration context where the upstream Portfolio Management system may retry failed calls. Modeling integration boundaries as ports prepares the system for future connectivity without coupling the domain to external systems.

### V. Execution Rules

In V1, execution means the Trader records the final execution details inside the application after market dealing happened outside the system.

- Executing an order MUST capture `ExecutedRate`. Execution MUST fail if `ExecutedRate` is missing.
- `ExecutionTime` is the timestamp at which the Trader confirms execution in the application. It MUST be recorded by the system, not supplied by the client.
- `DealingReference` MUST be generated by the system during execution.
- `ContractNumber` MUST be generated by the system during execution so that downstream integration with Deposits is possible.
- `Counterparty` MUST be assigned during execution. Execution MUST fail if `Counterparty` is missing.
- Execution MUST fail if any mandatory execution data (`ExecutedRate`, `Counterparty`) is missing.
- Only orders in **Assigned** status MAY be executed.

**Rationale**: Capturing execution data within the application creates a reliable audit trail and generates the identifiers required for downstream processing, even though actual market dealing happens externally.

### VI. API and UI Consistency

The backend is the single source of truth for all business rules.

- The frontend MAY perform user-experience validations (e.g., field format, required-field hints) but MUST NOT duplicate or replace backend business rule enforcement.
- V1 user experience MUST focus exclusively on the Trader workflow:
  - Receive external orders (via REST API from Portfolio Management)
  - List Received Term orders
  - List Received OnCall orders
  - Assign an order to a Trader
  - Unassign an order
  - List assigned orders
  - Update an assigned order
  - Execute an order
  - Cancel an order
  - Reject an order
- Term and OnCall orders MUST be visible in separate operational views.
- The REST API contract MUST be the authoritative interface between frontend and backend.

**Rationale**: Centralizing business rules in the backend prevents divergence between API and UI validation and ensures a single enforcement point for correctness.

### VII. Testing Discipline

Test-Driven Development (TDD) is mandatory for Domain and Application logic.

- **Domain tests** MUST cover: invariants, allowed combinations of `OrderType` and `OrderAction`, `ValueDate` rules, status transitions, assignment rules, idempotency rules, `ContractNumber` constraints, and monetary precision.
- **Application tests** MUST cover: use-case orchestration and port interactions (using test doubles for outbound ports).
- **Adapter tests** MUST cover: REST contract compliance, persistence mappings, and external integration boundary behavior.
- **End-to-end tests** MUST cover the most valuable Trader workflows (receive → assign → execute).
- No feature is considered complete without automated tests that exercise the feature's acceptance criteria.
- Tests MUST be written before production code for domain and application layers (Red-Green-Refactor).

**Rationale**: TDD ensures that business invariants are codified and regression-proof. Layered test coverage aligns with Hexagonal Architecture boundaries, catching defects at the appropriate level.

### VIII. Auditability and Security

Important business actions MUST be auditable.

- At minimum, the following events MUST produce audit records: order received, duplicate receive handled idempotently, order assigned, order unassigned, order updated, order executed, order cancelled, order rejected.
- Logs MUST NOT expose sensitive data unnecessarily (e.g., full account numbers, personal data).
- Mutating business actions MUST leave traceable records including who performed the action and when.
- Even with a single Trader role in V1, the system MUST preserve accountability by recording the identity of the actor for every mutating operation.

**Rationale**: Auditability is a regulatory expectation in banking. Recording actor identity from V1 avoids costly retrofitting and establishes a culture of accountability from the start.

### IX. Simplicity and Learning Focus

This project is intended as a disciplined introduction to Spec-Driven Development.

- Prefer explicit and simple designs over clever abstractions.
- Avoid premature event-driven complexity, unnecessary generic frameworks, or speculative extensibility.
- Every design decision MUST favor clarity and maintainability over premature optimization or sophistication.
- Any exception to this constitution MUST be documented with rationale, trade-offs, and risks in the relevant specification or plan artifact.

**Rationale**: The project's educational purpose requires that every architectural choice be understandable and justifiable. Simplicity reduces cognitive load and makes the codebase accessible to developers learning Hexagonal Architecture and Spec-Driven Development.

## Ubiquitous Language

All business concepts, domain terms, API names, DTOs, code identifiers, test names, error codes, and technical documentation inside the project MUST be written in English, even if discussions happen in French.

The project MUST preserve a strict ubiquitous language across backend, frontend, tests, and specifications. The following core terms are approved and MUST be used consistently:

| Term | Description |
|------|-------------|
| `MoneyMarketOrder` | The central aggregate representing an order |
| `OrderType` | Discriminator: Term or OnCall |
| `OrderAction` | The action: Subscription, Increase, Decrease, Redemption |
| `TermOrder` | A MoneyMarketOrder with OrderType = Term |
| `OnCallOrder` | A MoneyMarketOrder with OrderType = OnCall |
| `Subscription` | New order creation action |
| `Increase` | Action to increase an existing contract amount |
| `Decrease` | Action to decrease an existing contract amount |
| `Redemption` | Action to redeem (close) an existing contract |
| `Trader` | The internal user who manages order assignment and execution |
| `Assignment` | The act of assigning an order to a Trader |
| `PortfolioNumber` | Identifier of the portfolio from the external PM system |
| `ExternalOrderReference` | Unique reference from the Portfolio Management system |
| `ContractNumber` | System-generated identifier for downstream Deposits integration |
| `Counterparty` | The financial institution or entity on the other side of the deal |
| `DesiredCounterpartyComment` | Optional free-text preference for counterparty at reception |
| `Currency` | ISO currency code for the order |
| `Amount` | Monetary amount of the order |
| `ValueDate` | Settlement date for the order |
| `NoticePeriod` | Required notice for OnCall orders (24H, 48H) |
| `Tenor` | Duration for Term orders (1W, 2W, 1M, 3M, 6M, 1Y) |
| `MinimumRate` | The minimum acceptable rate requested by the portfolio manager |
| `ExecutedRate` | The actual rate obtained during market dealing |
| `DealingReference` | System-generated reference for the executed deal |
| `ExecutionTime` | Timestamp of execution confirmation in the application |
| `OrderStatus` | Lifecycle state: Received, Assigned, Executed, Cancelled, Rejected |

New domain terms MUST be proposed, reviewed, and added to this table before use in code or specifications. Synonyms and abbreviations MUST NOT be introduced without updating this section.

## Governance

This constitution is the supreme governance document for the Money Market Order Processing project. All specifications, plans, task lists, and implementation decisions MUST be checked against this constitution.

- **Compliance**: Every `/speckit.specify`, `/speckit.plan`, `/speckit.tasks`, and `/speckit.implement` execution MUST verify alignment with the principles defined herein.
- **Rejection**: If a design violates Hexagonal Architecture, the ubiquitous language, idempotency requirements, workflow rules, or any other principle in this constitution, it MUST be rejected or explicitly revised before proceeding.
- **Priority**: Clarity, traceability, and correctness take priority over optimization in all decisions.
- **Amendment procedure**: Any change to this constitution MUST be documented with rationale, trade-offs, and a version bump. Amendments follow semantic versioning:
  - **MAJOR**: Backward-incompatible governance or principle removals/redefinitions.
  - **MINOR**: New principle or section added, or materially expanded guidance.
  - **PATCH**: Clarifications, wording, typo fixes, non-semantic refinements.
- **Exception process**: Any exception to a constitutional principle MUST be documented in the relevant artifact with rationale, trade-offs, and risks. Undocumented exceptions are violations.

**Version**: 1.0.0 | **Ratified**: 2026-04-28 | **Last Amended**: 2026-04-28
