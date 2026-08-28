## Why

The agent feedback loop on this repo is slow because the test suite has **no selectable categories**: only one tag exists repo-wide (`@Tag("architecture")`), so an agent's safest verification is full-reactor `mvn test`, which pays 11 individual Testcontainers cold-starts (PostgreSQL, Kafka), a 32s `JpaOrderRepositoryTest`, and business-rule assertions that live in expensive REST integration classes instead of the fast application layer. Measured on the current suite (~117 classes / ~670 methods): domain+application units are sub-second-per-class, while each bootstrap integration/e2e class costs 4–15s **plus** per-class container startup. The hexagonal architecture already gives us in-memory port fakes in `mmx-application` — the cheapest place to assert business rules — but nothing codifies which loop an agent (or human) should run for which kind of change.

## What Changes

Delivered in three phases, in this order (each phase is independently valuable; later phases build on the vocabulary of earlier ones):

### Phase A — Test taxonomy and selectable loops

- **Category tags on every backend test class**: `fast` (pure JVM — domain, application with in-memory fakes, adapter units without Spring context/containers), `integration` (Spring context + Testcontainers, `mmx-bootstrap/src/test/rest/*` + persistence integration), `e2e` (Kafka Testcontainers end-to-end), and the existing `architecture`. Exactly one category per test class.
- **Selection mechanism**: JUnit 5 tag expressions via surefire `-Dgroups` / `-DexcludedGroups` property wiring in `backend/pom.xml` (plus documented `-Dtest=` module-scoped commands). Default `mvn test` continues to run everything — selection is opt-in, no CI behaviour change.
- **Tag completeness is enforced** by a red-first meta-test (architecture-style class scan): any test class in `mmx-bootstrap` (and later other modules) missing a category tag fails the build.
- **Agent loop policy codified in AGENTS.md** (+ `docs/agents/codebase-map.md` build-commands section): which loop to run per change type — domain change → `mmx-domain` `-Dtest`; application change → `mmx-application`; REST contract change → `mmx-adapter-in-rest` + the one matching integration class; final verification → single full run. This aligns the documented policy with the existing `openspec/config.yaml` "fast loop" guidance, which today has no executable tag vocabulary to point at.

### Phase C — Shared test infrastructure

- **Singleton PostgreSQL container** for all `integration`-tagged bootstrap tests: one container per JVM (shared static base class in test support), schema reset between classes instead of container restart.
- **Reusable Kafka broker** for `e2e`-tagged tests (shared across the Kafka e2e classes; single-broker lifecycle).
- **Spring context caching**: align integration-test `@SpringBootTest`/test-slice configuration so classes share cached contexts rather than restarting the context per class.
- **De-fatten `JpaOrderRepositoryTest`** (32s): split by concern (native desk queries / tenancy scoping / routed-hub idempotency / indexes) and enable JUnit-platform parallelism for the pure-unit modules (`mmx-domain`, `mmx-application`) where tests are trivially parallel-safe.

### Phase B — Push assertions down the test pyramid

- **Migrate business-rule assertions** from `mmx-bootstrap/src/test/rest/*IntegrationTest` classes to `mmx-application` tests against in-memory port fakes — migrated red-first (application test written and confirmed green against current behaviour, then the duplicated assertion is removed from the integration class).
- **Thin contract smoke remains at REST level**: one smoke per endpoint asserting status codes / JSON shape / wiring only (per the existing contract-first OpenAPI alignment), not lifecycle rules.
- **Kafka e2e → outbox assertion**: for each messaging capability, assert the transactional outbox row at persistence level (cheap) + unit-test the listener/relay; keep exactly **one** true Kafka e2e per capability as the acceptance lock (`ExecutionHandoffKafkaIntegrationTest`, `RoutedExecutionHandoffKafkaIntegrationTest` remain as `e2e` acceptance).

## Capabilities

### New Capabilities

- `test-feedback-loop`: tag taxonomy, selectable loops, shared test infrastructure, pyramid placement rules, and the agent loop policy.

### Modified Capabilities

- `backend-hexagonal-architecture`: no requirement text changes — the existing "Architecture tests run in CI" requirement keeps holding; architecture tests simply gain the `architecture` category consistently (already tagged today).

## Impact

- **Stack assumptions**: Spring Boot 4 backend (Maven multi-module reactor rooted at `backend/`), JUnit 5 + Surefire, Testcontainers (PostgreSQL, Kafka), Angular 21 frontend (Vitest/Cypress — untouched; frontend loop is already fast at 41 specs).
- **Hexagonal boundaries**: no production code moves. `mmx-domain` and `mmx-application` untouched except for new test classes in `mmx-application/src/test` (Phase B migrations land there — business rules already exist in production code; only their assertion location changes). `mmx-bootstrap` test support gains the singleton-container base class; `backend/pom.xml` gains surefire tag-expression wiring and parallelism config for pure-unit modules. No adapter placement changes, no new ports.
- **No product REST surface changes** — contract-first OpenAPI governance is not triggered; canonical `contracts/` are untouched.
- **TDD**: this is test-infrastructure work; the behaviour-changing pieces are themselves delivered red-first — the tag-completeness meta-test (fails before tags exist) and each Phase B migration (application test written first, then integration duplicate removed). Mechanical steps (container sharing, context alignment, class splitting) are refactor-nature and exempt from red-first, noted as such in tasks.
- **SDD parity**: `AGENTS.md` and `docs/agents/codebase-map.md` build-commands section updated in the same delivery as Phase A (documentation is part of the change, not a follow-up).
- **Risks**: tag mis-classification (mitigated by the meta-test and review), shared-container state leakage between classes (mitigated by schema reset convention), parallelism in unit modules (mitigated by starting with module-scoped opt-in).
