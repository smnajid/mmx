# Project governance (mmx)

Supreme governance for the Money Market Order Processing project. All OpenSpec
capability specs, change artifacts, and implementation decisions MUST align with
these principles. Ubiquitous language lives in [CONTEXT.md](../CONTEXT.md); canonical
requirements live under [`openspec/specs/`](../openspec/specs); HTTP/event contracts
live under [`contracts/`](../contracts).

> History: these principles were previously maintained as a Spec Kit "constitution"
> (`.specify/memory/constitution.md`, last v1.6.0). They are preserved here verbatim
> in intent, with paths repointed to the OpenSpec layout, after consolidating on
> OpenSpec as the sole SDD framework.

## I. Hexagonal architecture and modular monolith

- Domain and Application layers MUST be framework-agnostic (no Spring, Angular, JPA,
  SQL, HTTP, messaging). Dependencies point **inward** toward the domain core.
- Inbound adapters (REST controllers, UI) invoke application use cases only and MUST
  NOT contain business logic. Outbound adapters implement ports owned by the core.
- The **backend is the single source of truth** for business rules. The frontend MAY
  do UX validation but MUST NOT replace backend enforcement.
- **Contract-first REST (OpenAPI)**: the HTTP API MUST be defined first in a
  machine-readable OpenAPI 3 document under `contracts/<feature>/openapi.yaml`. That
  document is canonical for paths, operations, schemas, and error shapes; prose
  (`api-v1.md`) accompanies but MUST NOT be the sole source of truth.
- **Generated REST surface**: inbound REST adapters MUST generate API artifacts from
  the OpenAPI document, and every controller MUST `implement` the corresponding
  generated server API interface. Workflow: change OpenAPI → regenerate → implement
  controllers against generated interfaces → map to use cases. No ad-hoc routes/DTOs
  that diverge from the contract.
- The system is a **Modular Monolith**; microservice decomposition is out of scope.

## II. Domain integrity

- Immutable domain value objects MUST be Java `record` types validating in compact
  constructors. Closed sets (e.g. `Tenor`, `NoticePeriod`) MUST be enforced in the
  domain. Mandatory fields per `OrderOperation` MUST be enforced at creation.
- Lifecycle operations (Increase, Decrease, Redemption) MUST reference a valid
  `ContractNumber`. `ValueDate` MUST satisfy the minimum lead-time rule.
- Orders are all-or-nothing; partial execution is forbidden.
- Monetary and rate values MUST use exact decimal handling (`BigDecimal`); floating
  point MUST NOT be used for money or rates.

## III. Idempotency and integration boundaries

- `ReceiveOrder` MUST be idempotent on `ExternalOrderReference`; duplicates MUST NOT
  create a second order. V1 intake is REST-only.
- Downstream **Deposits** integration MUST be modeled as an outbound port boundary
  even though Deposits processing is out of V1 scope.

## IV. Testing discipline (TDD)

- TDD is mandatory for Domain and Application logic: failing test first (red),
  smallest change to pass (green), refactor. Tests precede production code for those
  layers.
- Cover invariants and orchestration at the right layer: domain (invariants,
  transitions, idempotency, precision), application (use cases via test doubles),
  adapters (REST/OpenAPI compliance, persistence mapping), and e2e for the core
  Trader workflow (receive → assign → execute).
- No feature is complete without automated tests exercising its acceptance criteria.

## V. Auditability and security

- Mutating actions (received, idempotent-duplicate, assigned, unassigned, updated,
  executed, cancelled, rejected) MUST produce audit records capturing **who** and
  **when**. Logs MUST NOT expose sensitive data unnecessarily.

## VI. Simplicity, learning focus, and spec–code parity

- Prefer explicit, simple designs; avoid speculative extensibility. Document any
  exception with rationale, trade-offs, and risks in the relevant artifact.
- **Spec–code parity (blocking)**: implementation work MUST NOT materially change
  behavior, contracts, persisted shape, or Trader-visible semantics without updating
  the matching specification artifacts **in the same delivery** (no intermediate
  spec-empty revision). "Material" includes REST paths/headers/schemas, domain
  validation or lifecycle rules, Flyway/DB columns, ubiquitous-language terms in UX
  or errors, queue/list/detail fields, and acceptance criteria.
- Keep aligned at minimum: the relevant `openspec/specs/<capability>/spec.md`,
  `contracts/<feature>/openapi.yaml` (and prose mirror `api-v1.md`, which MUST NOT
  contradict OpenAPI), plus async contracts and JSON schemas when events change.
- Purely internal refactors with no observable effect are exempt. When in doubt,
  update the spec. Missing spec updates for a material code change are a **blocking
  defect**, not a follow-up.

## Governance procedure

- **Compliance**: every OpenSpec change (`/opsx:propose`, `/opsx:apply`,
  `/opsx:archive`) MUST verify alignment with these principles, especially VI
  (spec–code parity).
- **Rejection**: designs violating hexagonal boundaries, the ubiquitous language,
  idempotency, or the order lifecycle defined in
  [`openspec/specs/money-market-order-lifecycle/spec.md`](../openspec/specs/money-market-order-lifecycle/spec.md)
  MUST be revised before proceeding.
- **Priority**: clarity, traceability, and correctness over optimization.
