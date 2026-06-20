## Why

The backend already follows hexagonal layering via Maven module boundaries and documented governance (`docs/governance.md` Principle I), but compile-time module deps do not prevent adapter-to-service coupling or other in-module drift. Known violations today include REST controllers injecting concrete `application.service` classes instead of `port.in` interfaces, and nested exception types inside services that force the REST adapter to depend on implementation details. Introducing ArchUnit with **Tier 2 strict port boundaries** makes architectural rules executable in CI and prevents gradual erosion as the codebase grows.

## What Changes

- Add **ArchUnit 1.4.2** (`archunit-junit5`) to the backend Maven parent and wire architecture tests in `mmx-domain` (fast domain rules) and `mmx-bootstrap` (full cross-module rules).
- Introduce a shared `ArchitectureRules` helper defining Tier 1 (framework isolation) and **Tier 2 (strict port boundaries)** rules from day one.
- **Refactor existing violations** so Tier 2 rules pass green on delivery:
  - `OrderManagementController` injects `AssignOrderUseCase` / `UnassignOrderUseCase` instead of `AssignmentService`.
  - Nested service exceptions (`CurrencyNotFoundException`, `InstitutionNotFoundException`, `ContractNotFoundException`) move to top-level types under `com.mmx.order.application.exception` (or domain where appropriate); `GlobalExceptionHandler` updated accordingly.
  - REST controllers and `@RestControllerAdvice` classes must not depend on `..application.service..` packages.
- Exclude OpenAPI-generated code (`..adapter.in.rest.generated..`) from rules that would false-positive on Jackson/Spring annotations.
- Document the enforcement point in `docs/agents/codebase-map.md` (architecture test location and scoped Maven commands).

No HTTP contract, domain lifecycle, or Trader-visible behaviour changes.

## Capabilities

### New Capabilities

- `backend-hexagonal-architecture`: Executable ArchUnit rules enforcing hexagonal dependency direction, framework isolation in domain/application, and strict inbound-port boundaries for REST adapters.

### Modified Capabilities

<!-- No product capability requirements change; this is infrastructure/governance only. -->

## Impact

- **Backend modules**: `backend/pom.xml` (dependencyManagement), `mmx-domain`, `mmx-bootstrap`, `mmx-adapter-in-rest`, `mmx-application`.
- **Dependencies**: new test-scoped `com.tngtech.archunit:archunit-junit5:1.4.2`.
- **APIs / contracts**: none.
- **Frontend**: none.
- **CI**: architecture tests run as part of normal `mvn test`; domain-scoped rules also run during fast TDD loops on `mmx-domain`.
