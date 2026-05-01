# Research: Money Market Order Processing

## R1: Build Tool — Maven vs Gradle

**Decision:** Maven (multi-module)

**Rationale:**

- XML-based POM is explicit and self-documenting — every dependency relationship visible at a glance
- Multi-module project support is mature and well-documented for the Hexagonal Architecture module structure (6 modules)
- Convention-over-configuration reduces decision fatigue for beginners
- The overwhelming majority of Spring Boot tutorials, blog posts, and Stack Overflow answers use Maven
- Enforcing module dependency constraints (e.g., `mmx-domain` must not depend on Spring) is straightforward — simply don't declare Spring dependencies in that module's POM
- Maven Wrapper (`mvnw`) included for reproducible builds

**Alternatives considered:**

- **Gradle with Kotlin DSL:** faster incremental builds, more concise, but adds a second language (Kotlin) to learn alongside Java; Groovy DSL has looser syntax that can confuse beginners
- Rejected because build performance is not a bottleneck for a learning project, and explicitness is prioritized per Constitution Principle IX

---

## R2: Angular State Management

**Decision:** No state management library. Use Angular's built-in `HttpClient` + component-local state + `OnPush` change detection.

**Rationale:**

- V1 has ~4 routes, ~11 API calls, and no complex shared state across features
- Each list view fetches fresh data from the API on navigation; mutation operations (assign, execute, etc.) trigger a data refresh
- Adding NgRx or a signals-based store introduces boilerplate and concepts (reducers, effects, selectors) that provide no value at this scale
- Aligns with Constitution Principle IX: prefer explicit and simple designs

**Alternatives considered:**

- **NgRx:** powerful but introduces significant boilerplate; overkill for V1 scope
- **Angular Signals with a custom store service:** viable for V2 when cross-component state sharing becomes necessary
- **RxJS-based service with BehaviorSubject:** reasonable lightweight option but adds reactive complexity; deferred to V2 if needed

---

## R3: Reference Generation Strategy

**Decision:** UUID-based references with domain-defined prefixes, generated in an outbound adapter.

**Rationale:**

- DealingReference format: `DL-{UUID}` (e.g., `DL-a3f2b8c1-7d4e-4a9f-b123-...`)
- ContractNumber format: `CN-{UUID}` (e.g., `CN-e9d1f4a2-8b3c-4d5e-a678-...`)
- UUID guarantees uniqueness without a database sequence or coordination
- The prefix (`DL-`, `CN-`) is domain knowledge validated in the value object record’s compact constructor
- The actual UUID generation is an infrastructure concern delegated to the `ReferenceGenerator` outbound port
- `UuidReferenceGenerator` adapter uses `java.util.UUID.randomUUID()`
- In tests, a deterministic stub can return predictable references

**Alternatives considered:**

- **Database sequence-based:** requires DB access in the generation path; couples domain logic to persistence timing
- **Formatted with date components** (e.g., `DL-20260428-001`): requires sequence coordination; adds complexity
- Rejected both because UUID is simpler, globally unique, and fully testable without infrastructure

---

## R4: Time Abstraction for ExecutionTime

**Decision:** Custom `Clock` outbound port in the application layer, implemented by `SystemClock` adapter.

**Rationale:**

- ExecutionTime must be recorded by the system (per Constitution), not supplied by the client
- Directly calling `Instant.now()` in a service makes tests non-deterministic
- A `Clock` port interface with a single method `Instant now()` allows production code to use the system clock and tests to inject a `FixedClock` returning a known instant
- This is a common pattern in financial applications where timestamps matter for audit trails

**Alternatives considered:**

- **Java's `java.time.Clock`:** could be injected directly, but it's an infrastructure class in the application layer; wrapping it in a port keeps the application layer framework-agnostic
- **No abstraction (just `Instant.now()`):** rejected because it makes application-layer tests flaky or requires PowerMock-style hacks

---

## R5: Trader Identity in V1

**Decision:** Use `X-Trader-Id` HTTP header. No authentication framework in V1.

**Rationale:**

- V1 is an internal tool with single-digit Traders; full OAuth2/OIDC would add significant infrastructure complexity
- The header approach allows every REST endpoint to know which Trader is acting, enabling assignment enforcement and audit logging
- Constitution Principle VIII requires actor identity on every mutating action; the header provides this
- The application layer receives `TraderId` as a value object, decoupled from how it's extracted at the HTTP level
- V2 can replace the header extraction with a Spring Security authentication context without changing the application or domain layers

**Alternatives considered:**

- **Spring Security with in-memory users:** adds security filter chain configuration, login page, session management — too much infrastructure for V1
- **JWT tokens from an external IdP:** proper solution for production but requires IdP setup; deferred to V2
- **No identity at all:** violates Constitution Principle VIII (auditability)

---

## R6: Persistence Mapping Strategy

**Decision:** Manual mapper classes between domain model and JPA entities.

**Rationale:**

- JPA entities (`OrderEntity`) live in `mmx-adapter-out-persistence` and are never exposed to domain or application layers
- `OrderPersistenceMapper` converts between `MoneyMarketOrder` (domain) and `OrderEntity` (JPA)
- Manual mapping makes every field transformation explicit and easy to debug
- Avoids magic from mapping frameworks (MapStruct, ModelMapper) that can silently drop or mismap fields — critical in a financial application

**Alternatives considered:**

- **MapStruct:** type-safe generated mappers, but adds annotation processing and a build-time dependency; mappings are less visible to beginners
- **ModelMapper:** reflection-based; can silently succeed with incorrect mappings
- **Direct JPA on domain entities:** violates Constitution Principle I (domain must be framework-agnostic)
- Rejected automated mappers because explicitness is prioritized per Principle IX

---

## R7: API Documentation

**Decision:** OpenAPI 3.0 with springdoc-openapi for auto-generated documentation.

**Rationale:**

- springdoc-openapi scans Spring MVC controllers and generates OpenAPI spec at runtime
- Swagger UI available at `/swagger-ui.html` for interactive testing during development
- The generated spec can be used by the Angular frontend to verify contract alignment
- No need to maintain a separate OpenAPI YAML file manually

**Alternatives considered:**

- **Hand-written OpenAPI YAML:** ensures spec-first design but adds maintenance burden; for V1, code-first with springdoc is sufficient
- **No API documentation:** rejected because Constitution requires the REST API contract to be the authoritative interface

---

## R8: Audit Implementation

**Decision:** Database-backed audit log via `AuditLogger` outbound port, implemented by `JpaAuditLogger`.

**Rationale:**

- `order_audit_log` table stores `event_type`, `actor_id`, `event_time`, and optional JSONB `details`
- The `AuditLogger` port is called by application services after each mutating operation
- JSONB details field captures context-specific data (e.g., changed fields for updates, rejection reason for rejects)
- Simple and queryable; no external audit service or event bus needed

**Alternatives considered:**

- **Structured logging only (SLF4J):** not queryable, not persisted reliably, harder to correlate with orders
- **Domain events with an event store:** architecturally clean but adds significant complexity (event bus, event handler registration); deferred to V2 if needed
- **Database triggers:** opaque, hard to test, violates the principle that business logic lives in the domain/application layers

---

## R9: Frontend Testing Strategy

**Decision:** Jasmine/Karma for unit/component tests, Cypress for e2e tests.

**Rationale:**

- Jasmine/Karma is Angular's default testing framework — zero additional setup
- Angular Testing Library for component tests provides user-centric testing patterns
- Cypress for e2e tests: well-documented, runs against the full stack, supports API stubbing for isolated frontend e2e tests or full-stack tests with Testcontainers-backed backend

**Alternatives considered:**

- **Jest:** popular but requires ejecting from Angular's default test runner; adds configuration overhead
- **Playwright:** excellent but less Angular-specific documentation than Cypress
- **No e2e tests:** rejected because Constitution Principle VII requires e2e coverage of core Trader workflows
