## Context

Order intake today is smeared across two application services (`ReceiveOrderService` for TradingHub-native intake, `RouteOrderService` for TradingClient routed intake) and a REST controller (`OrderIntakeController`) that owns the TradingClient-vs-TradingHub branch by reaching into the `LegalEntityRepository` out-port. The two services duplicate ~70% of intake validation and the routing path silently skips a spec-mandated validation (the lifecycle-institution-match check), so a TradingClient OnCall lifecycle order can be accepted with an `institutionCode` that does not match its source contract — a violation of `order-institution-constraints`'s "Lifecycle intake institution must match source contract" requirement, which is unconditional and explicitly binds TradingClient intake (L51).

Constraints: hexagonal inward-only dependency (`mmx-adapter-in-rest` → `mmx-application` → `mmx-domain`); contract-first REST (the intake path is `POST /api/v1/orders` in `contracts/001-mm-order-processing/openapi.yaml` and `contracts/002-trader-orders-views/openapi.yaml`, response `ReceiveOrderResponse` requires `[orderId, status, legalEntityCode]`); ADR-0002 mandates two linked records created synchronously in one transaction at routed intake; ArchUnit `HexagonalArchitectureTest` enforces that REST adapters depend on `port.in` (not `application.service`) and not on out-ports.

Full grilling decisions and current/target diagrams live in `.scratch/architecture-deepening/01-unified-intake-architecture.md`.

## Goals / Non-Goals

**Goals:**
- One deep application intake module (`IntakeUseCase`) owning both TradingHub and TradingClient intake, with a single external seam.
- Close the lifecycle-institution-match spec violation on the routed path (red-green TDD).
- Make `OrderIntakeController` genuinely thin: no `LegalEntityRepository`, no branch.
- One transactional boundary covering both paths; remove the duplicated validation and the shallow `OrderRoutingFieldMappingPolicy`.
- No REST contract change.

**Non-Goals:**
- Deepening the client↔hub link model (candidate #3 — replace the `originatingLegalEntityCode` null sentinel / use `RoutedOrderLink`). Deferred to its own change.
- Decomposing the `OrderRepository` port (candidate #8 — split CRUD / routing lookup / contract catalog). Deferred.
- Scoping `findExecutedSubscriptionByContractNumber` by `legalEntityCode` (defense-in-depth). `ContractNumber`s are UUID-globally-unique today so the unscoped lookup is correct; scoping is a separate hardening change.
- Any change to the `OrderExecutedV1` outbox / routing-context behaviour, propagation (`ExecuteOrderService` / `OrderLifecycleService`), or accounting handoff.
- Any frontend change.

## Decisions

### D1 — One unified `IntakeUseCase` replaces `ReceiveOrderUseCase` + `RouteOrderUseCase`
The two current use-cases are not real seams — one implementation each, ~70% shared logic. Per "one adapter = hypothetical seam, two adapters = real seam," two port-in interfaces with one impl each are not earning their keep. The TradingClient-vs-TradingHub decision moves into the application layer (which already owns `LegalEntityRepository` as an out-port), and `RoutedOrderIntake` becomes an **internal** collaborator behind the single external seam. The REST adapter becomes genuinely thin (map → call → respond) and stops injecting an out-port.
- *Alternative considered:* keep both use-cases as thin facades over a shared `IntakeCore`. Rejected: the controller would still branch (or need a dispatcher use-case) and the two interfaces still aren't real seams; it preserves surface area that isn't earning its keep.

### D2 — Fix the spec-violation gap first (TDD red-green on current code), then behaviour-preserving refactor
A failing test is written against the current `RouteOrderUseCase` proving a routed OnCall INCREASE with a mismatched `institutionCode` is accepted today (should throw `InvalidOrderException` and create no order). The smallest green step adds `validateLifecycleInstitutionMatchesContract` to `RouteOrderService`. The unification refactor then lands as a separate, behaviour-preserving step (the test migrates from `RouteOrderUseCase` to `IntakeUseCase` and stays green).
- *Alternative considered:* refactor to `IntakeUseCase` first, then close the gap against the new interface. Rejected: it mingles "move code" with "change behaviour" in one step, which is harder to review and bug-introducing.

### D3 — One `TransactionalIntakeUseCase` wrapper in `mmx-bootstrap`
Matches the existing `Transactional*UseCase` pattern (`TransactionalExecuteOrderUseCase`, `TransactionalRouteOrderUseCase`, `Transactional{Add,Cancel,Confirm}OnCallRateUseCase`). Keeps `@Transactional` out of `mmx-application` (the point of the wrapper pattern; the hexagonal Tier-2 rules keep Spring annotations out of the application module). The routed path's two-record write (client `ROUTED` + hub `RECEIVED`) gets one atomic transaction per ADR-0002; the hub path gains a transaction it currently lacks. `TransactionalRouteOrderUseCase` is removed.
- *Alternative considered:* annotate `IntakeService.receive` with `@Transactional` directly. Rejected: breaks the established wrapper pattern and puts a Spring annotation in `mmx-application`.

### D4 — `RoutedOrderIntake` is a concrete class, not a port
A concrete class (no port interface), `@Bean`-wired in `OrderModuleConfiguration`, injected into `IntakeService`. It groups the routing-specific out-ports (`ProxyInstitutionRepository`, `DelegatedGrantDirectory`, `GlobalAccountDirectory`) behind one collaborator and owns routing-specific creation: `resolveProxy`, `resolveGrant`, `resolveGlobalAccount`, `createClientOrder`, the hub-side draft, `markRouted`. This is composition, not a seam — the two-adapter trigger is not met (only one routed implementation exists), so introducing a port would be a hypothetical seam.
- *Alternative considered:* a port interface in `port/out` or `port/in` with one adapter. Rejected by the two-adapter rule.
- *Alternative considered:* inline routing creation as private methods in `IntakeService`. Rejected: loses locality of the part most likely to grow with routing rules and bloats `IntakeService`.

### D5 — Fold `OrderRoutingFieldMappingPolicy` into `RoutedOrderIntake`; delete the policy
`OrderRoutingFieldMappingPolicy.mapToHubSide` is a pure field-copy into `RoutedHubOrderDraft` with no rules. The spec'd *decisions* (counterparty → hub native institution, `portfolioNumber` → global account) are made by the caller choosing which `globalAccount` and hub-native institution to pass; the policy just packs fields. The spec anchor is a test on `RoutedOrderIntake` asserting `MoneyMarketOrder.createHubSideFromRouting` produces an order with the global-account `portfolioNumber` and hub-native institution — not a class name. `RoutedHubOrderDraft` stays in `mmx-domain` because it is the domain factory's input contract.
- *Alternative considered:* keep the policy as a 1:1 spec→class-name anchor (`order-routing` L90 enumerates the mappings). Rejected: spec-by-prose is weaker than spec-by-test; the class is a shallow pass-through (deletion test: deleting it just moves the field-copy inline).

### D6 — Minimal scope; do not fold in candidate #3 or #8
The unification works on today's correlation mechanism unchanged (`findHubOrderByRoutingId`, `markRouted(routingId, …)`). The link model (#3) and `OrderRepository` decomposition (#8) are independently shippable changes with their own design trees; folding them in mingles refactors. Surgical changes per AGENTS.md.
- *Alternative considered:* fold in #3 while already restructuring intake. Rejected: bigger diff, harder review, two design trees in one PR.

### D7 — `IntakeUseCase.Result(UUID orderId, OrderStatus status, boolean newlyCreated)`
`hubOrderId` is dropped entirely. It is dead today — the controller is the only consumer of the use-case `Result` and discards the field; `ReceiveOrderResponse` has no `hubOrderId` field; the audit log records only the client id. The hub-side order's id is re-derivable any time via `findHubOrderByRoutingId(routingId)`. The `Result` maps 1:1 to `ReceiveOrderResponse`.
- *Alternative considered:* carry `Optional<UUID> hubOrderId` for future use. Rejected: YAGNI — carrying an optional nobody reads is unused flexibility.

### D8 — Routed validation ordering: institution-constraint checks first (throw, no order), then routing-failure checks (rejectAtIntake, REJECTED order)
The spec distinguishes two failure classes. `order-institution-constraints` L99 says a lifecycle-institution mismatch "SHALL be rejected … and no order SHALL be created." `order-routing` L47/L52 says grant-violation and unresolved-global-account rejections create a client-side `REJECTED` order ("the client-side order is `REJECTED`, and no hub-side order is created"). The routed path therefore groups checks as:
1. `resolveProxy` (institution exists) → `InvalidOrderException`, no order
2. `validateLifecycleInstitutionMatchesContract` (the gap fix) → `InvalidOrderException`, no order
3. `currencyPolicy.validateReceive` → `InvalidOrderException`, no order
4. `resolveGrant` → `rejectAtIntake` (client `REJECTED` order, no hub-side order)
5. `resolveGlobalAccount` → `rejectAtIntake` (client `REJECTED` order, no hub-side order)

This matches the hub path's institution-constraint handling and the spec distinction.
- *Alternative considered:* run the lifecycle-institution check after grant/global-account and route its failure through `rejectAtIntake`. Rejected: contradicts `order-institution-constraints` L99 ("no order SHALL be created").

## Adapter placement & dependency direction

- **`mmx-domain`**: `MoneyMarketOrder.create` / `createHubSideFromRouting` and `RoutedHubOrderDraft` unchanged. `OrderRoutingFieldMappingPolicy` deleted. No Spring, no framework.
- **`mmx-application`** (inward dependency from REST): new `port/in/IntakeUseCase` (single external seam); new `IntakeService` (shared intake core + TradingClient/TradingHub dispatch); new `RoutedOrderIntake` (concrete class, injected into `IntakeService`). Retire `port/in/ReceiveOrderUseCase`, `port/in/RouteOrderUseCase`, `ReceiveOrderService`, `RouteOrderService`. The shared validation (`intakeSourceContractNumber`, PM-org + legal-entity checks, `currencyPolicy`, `validateLifecycleInstitutionMatchesContract`, audit constants) consolidates here.
- **`mmx-adapter-in-rest`**: `OrderIntakeController` implements the generated `IntakeApi` (contract `002`, path `POST /api/v1/orders`). It maps `ReceiveOrderRequest` → `ReceiveOrderCommand` via `OrderRestMapper`, calls `IntakeUseCase.receive`, maps `Result` → `ReceiveOrderResponse`. `LegalEntityRepository` injection and the `isTradingClient()` branch are removed. No business rules.
- **`mmx-bootstrap`**: `TransactionalIntakeUseCase` (`@Transactional`, delegates to `IntakeService`); `IntakeService` and `RoutedOrderIntake` `@Bean` wiring in `OrderModuleConfiguration` (and `OrderRoutingModuleConfiguration` removed/merged). Composition only.
- **HTTP / codegen**: no `openapi.yaml` change. The controller continues to implement the generated `IntakeApi` interface from `contracts/002-trader-orders-views` (regenerated server stubs unchanged for this path).

## Test placement

- **Primary seam — `IntakeUseCase`** (application-level, `mmx-application/src/test/.../IntakeServiceTest`): mocks out-ports (`OrderRepository`, `ManagedCurrencyRepository`, `InstitutionRepository`, `ProxyInstitutionRepository`, `DelegatedGrantDirectory`, `GlobalAccountDirectory`, `OpenPositionPort`, `OrganisationRepository`, `LegalEntityRepository`, `AuditLogger`, `Clock`); asserts on `Result`, saved orders, and thrown `InvalidOrderException`. Migrates `ReceiveOrderServiceTest` + `RouteOrderServiceTest` + `IntakeLegalEntityValidationTest`. The red-first test for the routed lifecycle-institution-mismatch rejection lives here.
- **Verification guard — REST integration** (`mmx-bootstrap`): `OrderRoutingIntakeIntegrationTest` and `OrderRestApiIntegrationTest` stay green; they verify the thinned controller wiring against the unchanged contract (Testcontainers).
- **Layering guard — ArchUnit** (`mmx-bootstrap`): `HexagonalArchitectureTest` asserts the controller no longer injects an out-port and depends only on `port.in`.

## Risks / Trade-offs

- **Retiring two port-in interfaces is moderately hard to reverse** → A future `/improve-codebase-architecture` pass might re-suggest splitting intake. Mitigation: this design.md records the rationale (D1); elevate to ADR-0005 after implementation if it still feels load-bearing.
- **Migrating tests from two services to one is a chunk of the diff** → Behaviour-preserving if done test-first per D2; the red-first gap test proves the bug, the refactor keeps the rest green. Mitigation: run the existing `ReceiveOrderServiceTest` / `RouteOrderServiceTest` suites against the migrated `IntakeServiceTest` shape before deleting the old services.
- **The unscoped `findExecutedSubscriptionByContractNumber` is correct only while `ContractNumber`s are UUID-globally-unique** → A future non-UUID generator would break the institution-match lookup across legal entities. Mitigation: noted as a deferred hardening item (scope-by-`legalEntityCode`); out of scope here.
- **Two failure styles remain on the routed path (throw vs `rejectAtIntake`)** → This is spec-mandated, not a defect, but it's a subtle invariant. Mitigation: D8 documents the ordering and the spec cites; `IntakeServiceTest` covers both failure styles.

## Migration Plan

1. **Red**: add a failing test to `RouteOrderServiceTest` — routed OnCall INCREASE with mismatched proxy `institutionCode` is accepted today; assert it throws `InvalidOrderException` and creates no order. `mvn test -pl mmx-application -Dtest=RouteOrderServiceTest` → red.
2. **Green**: add `validateLifecycleInstitutionMatchesContract` to `RouteOrderService` after `resolveProxy`, before `resolveGrant` (reusing the existing `findExecutedSubscriptionByContractNumber`; proxy-vs-proxy match). Re-run the same `-Dtest` → green.
3. **Refactor (behaviour-preserving)**: introduce `IntakeUseCase` + `IntakeService` + `RoutedOrderIntake`; fold `mapToHubSide` into `RoutedOrderIntake`; delete `OrderRoutingFieldMappingPolicy`; add `TransactionalIntakeUseCase`; thin `OrderIntakeController`; rewire `OrderModuleConfiguration` / `OrderRoutingModuleConfiguration`; migrate tests to `IntakeServiceTest`. Keep `OrderRoutingIntakeIntegrationTest` and `HexagonalArchitectureTest` green.
4. **Final verification**: full reactor `mvn test` from `backend/` (including `mmx-bootstrap` Testcontainers) once the refactor is green.

Rollback: each step is a separate commit; revert step 3 to restore the two-service shape (the gap fix from step 2 can stay or revert independently).

## Open Questions

None — all load-bearing decisions were resolved during grilling (`.scratch/architecture-deepening/01-unified-intake-architecture.md` §7).
