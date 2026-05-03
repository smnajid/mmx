<!--
## Sync Impact Report

- **Version change**: 1.3.0 → 1.4.0 (MINOR — REST controllers MUST implement OpenAPI-generated API interfaces)
- **Modified principles**:
  - I. Hexagonal Architecture and Modular Monolith — generated REST surface now explicitly requires implementing generated server API interfaces from the OpenAPI contract
- **Templates requiring updates**:
  - `.specify/templates/plan-template.md` — ✅ OpenAPI gate mentions generated `*Api` interfaces
  - `.specify/templates/spec-template.md` — ✅ REST comment references controllers implement generated `*Api`
  - `.specify/templates/tasks-template.md` — ✅ codegen note references controller implements generated API
- **Follow-up TODOs**: None (principle codifies existing `mmx-adapter-in-rest` practice). Active feature plans under `specs/*/plan.md` MUST stay aligned with constitution checks when principles change.
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
- The backend is the single source of truth for all business rules. A frontend MAY perform user-experience validations (e.g., field format hints) but MUST NOT duplicate or replace backend business rule enforcement.
- **Contract-first REST (OpenAPI)**: The HTTP API between clients and the backend MUST be defined **first** in a **machine-readable OpenAPI 3** document (YAML or JSON) versioned with the feature (e.g. under `specs/<feature>/contracts/`). That document is the **canonical** contract for paths, operations, request/response schemas, security requirements at the transport layer, and documented error response shapes. Prose documentation (e.g. `api-v1.md`) MAY accompany the spec but MUST NOT be the only source of truth.
- **Generated REST surface**: Inbound REST adapters MUST **generate** API-facing artifacts from the OpenAPI document (e.g. DTOs, server interfaces, or stubs using OpenAPI Generator or an equivalent tool integrated in the build). Controllers and request/response types MUST **not** be hand-written in ways that **diverge** from the generated contract. **Every REST controller class MUST `implement` the corresponding generated server API interface(s)** from that contract (e.g. Spring-style `*Api` interfaces produced per OpenAPI tag or operation group). Routing and operation signatures MUST come from those interfaces (inherited `@RequestMapping` / `@*Mapping` and method parameters); controllers MUST NOT expose parallel hand-maintained HTTP mappings that duplicate the OpenAPI operations without implementing the generated interface. Workflow: change the OpenAPI file → regenerate → implement or update controllers against the generated interfaces → map to application use cases only. If generated code is unsuitable for a small subset, the exception MUST be documented in the plan with rationale and MUST still be validated against the same OpenAPI operations (no ad-hoc routes or DTOs).
- The shared OpenAPI document SHOULD be used (where tooling allows) to generate or validate **frontend** API clients so browser and server stay aligned.
- The system MUST be implemented as a Modular Monolith. Microservice decomposition is explicitly out of scope.

**Rationale**: Hexagonal Architecture enforces a clean separation between business logic and technical infrastructure, making the domain testable in isolation and portable across frameworks. Designating the backend as the single enforcement point for business rules prevents divergence between API and UI. **OpenAPI as the single contract with codegen** prevents silent drift between documentation, DTOs, and runtime behavior, supports automated contract tests, and scales with team changes. **Requiring controllers to implement generated API interfaces** ties compile-time Java types to the same artifact that defines HTTP paths and payloads, so refactors of the spec surface as compilation errors instead of silent behavioral drift. The Modular Monolith constraint avoids premature distributed-system complexity for a learning-focused product.

### II. Domain Integrity

All business rules MUST be enforced within the domain core.

- Immutable domain **value objects** (typed wrappers and small composites modeled in the domain, excluding aggregate roots and excluding Java `enum` types) MUST be implemented as Java `record` types. Validation and normalization MUST run in compact constructors (or in a small static factory that delegates to the canonical record constructor). Accessors MUST follow Java record conventions (component accessor methods).
- The domain MUST validate that every `OrderOperation` is allowed for its `OrderType`. Invalid combinations MUST be rejected at creation time.
- The domain MUST enforce that allowed values for constrained fields (e.g., `Tenor`, `NoticePeriod`) are drawn from a closed set defined in the domain model. Any value outside the set MUST be rejected.
- The domain MUST enforce mandatory field requirements per `OrderOperation`. An order MUST NOT be created if required fields are missing or if fields exclusive to another `OrderType` are provided.
- Lifecycle operations on an existing contract (e.g., Increase, Decrease, Redemption) MUST reference a valid `ContractNumber`.
- `ValueDate` MUST be validated against a minimum lead-time rule defined by the domain. Orders that violate the lead-time MUST be rejected.
- Orders are processed all-or-nothing. Partial execution is forbidden.
- Monetary values and rate values MUST use exact decimal handling appropriate for financial applications (e.g., `BigDecimal` in Java). Floating-point types (`float`, `double`) MUST NOT be used for monetary or rate calculations.

**Rationale**: Encoding structural invariants in the domain core ensures correctness regardless of which adapter or entry point triggers the operation. Records remove boilerplate for immutable carriers of validated data while keeping semantics explicit in one place. The specific allowed values (which tenors, which notice periods, which operations per type) are defined in the domain model and may evolve across versions, but the principle that the domain validates and rejects invalid combinations is non-negotiable. Financial precision rules prevent rounding errors that could have regulatory or monetary impact.

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
- Execution MUST be an explicit, complete use case. It MUST NOT succeed if any mandatory execution data is missing. System-generated identifiers and timestamps MUST originate from the system, not from client input.
- The workflow MUST remain simple and deterministic in V1.

**Rationale**: An explicit state machine prevents invalid lifecycle transitions. Requiring execution to be complete and system-timestamped ensures reliable audit trails and prevents clients from fabricating traceability data.

### IV. Idempotency and Integration Boundaries

- The `ReceiveOrder` use case MUST be idempotent using `ExternalOrderReference` supplied by the Portfolio Management system. Receiving the same `ExternalOrderReference` twice MUST NOT create a duplicate order.
- The application MUST prevent duplicate business creation for the same `ExternalOrderReference`.
- V1 order intake happens only through the REST API. Manual order entry is explicitly out of scope.
- Integration with the downstream **Deposits** application MUST be modeled as a clear boundary (outbound port), even though Deposits processing itself remains out of scope in V1.
- Future outbound email communication to counterparties is out of scope for V1 and MUST NOT complicate the current domain model.

**Rationale**: Idempotency is critical in an integration context where the upstream Portfolio Management system may retry failed calls. Modeling integration boundaries as ports prepares the system for future connectivity without coupling the domain to external systems.

### V. Testing Discipline

Test-Driven Development (TDD) is mandatory for Domain and Application logic.

- **Domain tests** MUST cover: invariants, allowed combinations of `OrderType` and `OrderOperation`, `ValueDate` rules, status transitions, assignment rules, idempotency rules, `ContractNumber` constraints, and monetary precision.
- **Application tests** MUST cover: use-case orchestration and port interactions (using test doubles for outbound ports).
- **Adapter tests** MUST cover: REST contract compliance (including **OpenAPI** document vs runtime), persistence mappings, and external integration boundary behavior.
- **End-to-end tests** MUST cover the most valuable Trader workflows (receive → assign → execute).
- No feature is considered complete without automated tests that exercise the feature's acceptance criteria.
- Tests MUST be written before production code for domain and application layers (Red-Green-Refactor).

**Rationale**: TDD ensures that business invariants are codified and regression-proof. Layered test coverage aligns with Hexagonal Architecture boundaries, catching defects at the appropriate level.

### VI. Auditability and Security

Important business actions MUST be auditable.

- At minimum, the following events MUST produce audit records: order received, duplicate receive handled idempotently, order assigned, order unassigned, order updated, order executed, order cancelled, order rejected.
- Logs MUST NOT expose sensitive data unnecessarily (e.g., full account numbers, personal data).
- Mutating business actions MUST leave traceable records including who performed the action and when.
- Even with a single Trader role in V1, the system MUST preserve accountability by recording the identity of the actor for every mutating operation.

**Rationale**: Auditability is a regulatory expectation in banking. Recording actor identity from V1 avoids costly retrofitting and establishes a culture of accountability from the start.

### VII. Simplicity and Learning Focus

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
| `OrderOperation` | The action: Subscription, Increase, Decrease, Redemption |
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
| `DesiredCounterpartyComment` | Optional free-text preference for counterparty at reception only; Traders MUST NOT change it after intake |
| `Currency` | ISO currency code for the order |
| `Amount` | Monetary amount of the order |
| `ValueDate` | Settlement date for the order |
| `NoticePeriod` | Required notice for OnCall orders (24H, 48H) |
| `Tenor` | Duration for Term orders (1W, 2W, 1M, 3M, 6M, 1Y) |
| `MinimumRate` | Optional Portfolio Manager indication of the minimum acceptable executed rate when supplied at intake; execution must meet or exceed it when present; Traders MUST NOT alter it after reception — if the floor cannot be met, reject the order rather than negotiating the rate through the application |
| `ExecutedRate` | The actual rate obtained during market dealing |
| `DealingReference` | System-generated reference for the executed deal |
| `ExecutionTime` | Timestamp of execution confirmation in the application |
| `OrderStatus` | Lifecycle state: Received, Assigned, Executed, Cancelled, Rejected |

New domain terms MUST be proposed, reviewed, and added to this table before use in code or specifications. Synonyms and abbreviations MUST NOT be introduced without updating this section.

## Amendment 1.4.1 (2026-05-03)

**Rationale**: Align ubiquitous language with the feature spec: `MinimumRate` is an optional Portfolio Manager execution-floor indication at intake only; Traders do not negotiate it in-app; if the floor cannot be met, reject rather than execute below it.

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

**Version**: 1.4.1 | **Ratified**: 2026-04-28 | **Last Amended**: 2026-05-03
