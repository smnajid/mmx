## Context

The mmx backend is a Maven multi-module hexagonal monolith (`mmx-domain` → `mmx-application` → `adapter-*` → `mmx-bootstrap`). Module POMs already enforce compile-time dependency direction and document "CONSTITUTIONAL RULE" comments (zero framework deps in domain/application). Governance Principle I (`docs/governance.md`) states the same rules in prose.

However, Maven cannot enforce:

- Package-level coupling within a module (e.g. REST adapter → concrete `application.service`)
- Convention that controllers use `port.in` interfaces
- Placement of exception types and JPA entities

Known Tier 2 violations today:

| Location | Issue |
|----------|-------|
| `OrderManagementController` | Injects `AssignmentService` instead of `AssignOrderUseCase` / `UnassignOrderUseCase` |
| `GlobalExceptionHandler` | Catches nested exceptions from `ManageCurrencySettingsService`, `ManageInstitutionSettingsService`, `OnCallOrderCreationOptionsService` |

ArchUnit 1.4.2 supports Java 25 (project `java.version`). No product HTTP or domain behaviour changes are in scope.

## Goals / Non-Goals

**Goals:**

- Add executable ArchUnit rules (Tier 1 + **Tier 2 strict port boundaries**) that fail CI on architectural drift.
- Fix all existing violations so rules pass green on delivery.
- Provide fast feedback: domain-only rules in `mmx-domain`; full rules in `mmx-bootstrap`.
- Document test location and scoped Maven commands in `docs/agents/codebase-map.md`.

**Non-Goals:**

- New Maven Enforcer plugin rules (ArchUnit is sufficient for package-level checks).
- Dedicated `mmx-architecture-tests` module (bootstrap already assembles full classpath).
- Changing HTTP contracts, OpenAPI, or Trader-visible error payloads.
- Requiring application-layer DTOs instead of domain types in REST mappers (domain imports in mappers remain allowed).
- Enforcing that every `application.service` class implements a `port.in` interface (future hardening; not in this change).

## Decisions

### D1: ArchUnit dependency and version

Add `com.tngtech.archunit:archunit-junit5:1.4.2` in parent `dependencyManagement`; child modules declare it `test`-scoped.

**Rationale:** JUnit 5 integration matches existing test stack; 1.4.2 supports Java 25.

**Alternative considered:** Plain `archunit` core only — rejected; `archunit-junit5` gives `@AnalyzeClasses` and cleaner test structure.

### D2: Shared rules class in `mmx-domain` test sources

Create `com.mmx.order.architecture.ArchitectureRules` under `mmx-domain/src/test/java`. Both `mmx-domain` and `mmx-bootstrap` tests import it.

**Rationale:** Domain module is the innermost layer and already has minimal test deps; rules are plain Java static methods with no Spring. Bootstrap reuses the same definitions for consistency.

**Alternative considered:** Duplicate rules in bootstrap — rejected; single source of truth.

### D3: Two test entry points

| Test class | Module | Scope | Purpose |
|------------|--------|-------|---------|
| `DomainArchitectureTest` | `mmx-domain` | `ImportOption.DoNotIncludeTests` on `com.mmx.order` | Tier 1 domain isolation; runs in fast TDD loop |
| `HexagonalArchitectureTest` | `mmx-bootstrap` | Full `com.mmx.order` production code | Tier 1 + Tier 2 all layers |

Tag bootstrap test `@Tag("architecture")` for optional filtering; it still runs in default `mvn test`.

**Rationale:** Aligns with `openspec/config.yaml` guidance to scope tests during apply; domain developers get immediate feedback.

### D4: Tier 1 rules (framework isolation)

Enforce via `noClasses().that()...should().dependOnClassesThat()...`:

1. **Domain** (`..domain..`): must not depend on Spring, JPA/Hibernate, Jackson, or any `..application..` / `..adapter..` package.
2. **Application** (`..application..`): must not depend on Spring, JPA/Hibernate, or any `..adapter..` package.
3. **Outbound adapters** (`..adapter.out..`): must not depend on `..adapter.in..`.
4. **JPA entities**: classes annotated with `@Entity` must reside in `..adapter.out.persistence.entity..`.

Exclude `..adapter.in.rest.generated..` from rules checking forbidden dependencies on Jackson/Spring in *callers* (generated code lives in adapter-in and is allowed framework usage).

### D5: Tier 2 rules (strict port boundaries)

1. **REST controllers** — classes in `..adapter.in.rest..` annotated with `@RestController` must not depend on any class in `..application.service..`. Allowed dependency packages: `..application.port.in..`, `..application.command..`, `..application.port.in..` (pagination types), `..domain..`, `..adapter.in.rest..` (mappers, generated).

2. **REST advice** — classes annotated with `@RestControllerAdvice` must not depend on `..application.service..`. Allowed: `..application.exception..`, `..application.termrate..` (existing top-level ingest exceptions), `..domain.exception..`, `..adapter.in.rest..`.

3. **Layered architecture** (ArchUnit `layeredArchitecture()`):
   - Layer **Domain**: `com.mmx.order.domain..`
   - Layer **Application**: `com.mmx.order.application..`
   - Layer **AdapterIn**: `com.mmx.order.adapter.in..`
   - Layer **AdapterOut**: `com.mmx.order.adapter.out..`
   - Layer **Bootstrap**: `com.mmx.order.config..`
   - Rules: Domain ← Application ← {AdapterIn, AdapterOut}; Bootstrap may access all; AdapterIn and AdapterOut must not access each other.

**Rationale:** Codifies governance Principle I and existing desk-order-queries port seam. Stricter than Maven alone.

**Alternative considered:** Allow `application.service` in controllers if it implements a port — rejected for Tier 2; inject the port interface type instead (Spring resolves the same bean).

### D6: Refactor violations — assignment ports

Change `OrderManagementController` constructor to accept `AssignOrderUseCase` and `UnassignOrderUseCase` (two parameters or single type if combined — prefer two interfaces matching existing ports). Remove `AssignmentService` import.

Bootstrap wiring unchanged: `AssignmentService` bean already implements both interfaces.

### D7: Refactor violations — exception extraction

Move nested exceptions to top-level types in `com.mmx.order.application.exception`:

| From | To |
|------|-----|
| `ManageCurrencySettingsService.CurrencyNotFoundException` | `CurrencyNotFoundException` |
| `ManageInstitutionSettingsService.InstitutionNotFoundException` | `InstitutionNotFoundException` |
| `OnCallOrderCreationOptionsService.ContractNotFoundException` | `ContractNotFoundException` |

Services throw the new types; `GlobalExceptionHandler` imports from `application.exception`. Existing HTTP status codes and error bodies unchanged.

**Rationale:** Keeps "not found" semantics at application layer (resource lookup failures), distinct from domain invariant violations in `domain.exception`. Avoids REST → service coupling.

**Alternative considered:** Move to `domain.exception` — rejected; these are application orchestration outcomes, not domain invariants.

### D8: TDD approach for this change

Architecture tests are the specification: write failing `HexagonalArchitectureTest` first (red on current codebase), then refactor until green. No separate behaviour tests needed for controller wiring (existing controller tests cover assign/unassign).

Scoped command for red-green loop:

```bash
cd backend && mvn test -pl mmx-bootstrap -Dtest=HexagonalArchitectureTest
```

Domain rules loop:

```bash
cd backend && mvn test -pl mmx-domain -Dtest=DomainArchitectureTest
```

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| ArchUnit scans generated OpenAPI classes with Jackson deps | Exclude generated package from caller-side rules; do not assert "no Jackson in adapter-in" globally |
| Bootstrap architecture test classpath misses classes | `@AnalyzeClasses(packages = "com.mmx.order", importOptions = DoNotIncludeTests.class)`; bootstrap transitively depends on all adapter modules |
| False positives on mappers importing domain | Explicitly allow `..domain..` for adapter-in classes; rule targets `application.service` only |
| Future developers add new service exceptions as nested classes | Tier 2 `@RestControllerAdvice` rule fails CI; spec documents `application.exception` as the seam |
| ArchUnit test in bootstrap runs alongside slow Testcontainers | Test is pure bytecode analysis (~seconds); no Spring context |

## Migration Plan

1. Add ArchUnit dependency and shared `ArchitectureRules`.
2. Write `HexagonalArchitectureTest` — expect failure listing current violations.
3. Refactor assignment injection and extract exceptions.
4. Re-run until architecture tests green; then run existing REST/controller tests scoped to touched modules.
5. **Final verification:** full `cd backend && mvn test`.
6. No deployment migration; no Flyway; no rollback beyond reverting the commit.

## Open Questions

None — Tier 2 strict boundaries and exception placement are decided in this design.
