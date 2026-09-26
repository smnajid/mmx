# test-feedback-loop Specification

## Purpose
Keep the agent (and human) test feedback loop fast and honest. This capability defines the backend test taxonomy — `fast`, `integration`, `e2e`, `architecture`, exactly one tag per test class, enforced by a meta-test — and the selectable `-Dgroups` loops built on it, so verification can be scoped to the narrowest loop that can fail for the right reason instead of always paying the full reactor.
## Requirements
### Requirement: Every backend test class carries exactly one category tag

Every automated backend test class SHALL be tagged with exactly one JUnit 5 category tag: `fast` (pure JVM, no Spring context, no containers, no broker), `integration` (Spring context + relational test container), `e2e` (full stack including a Kafka broker), or `architecture` (architectural rule enforcement). A test class tagged with none or with more than one category SHALL fail the build via a tag-completeness meta-test.

#### Scenario: Untagged bootstrap test class fails the build

- **WHEN** a test class under any `src/test/java` of `backend/` carries no category tag
- **THEN** the tag-completeness meta-test fails, naming the offending class

#### Scenario: Multi-tagged test class fails the build

- **WHEN** a test class carries both `fast` and `integration` category tags
- **THEN** the tag-completeness meta-test fails, naming the offending class

#### Scenario: Correctly tagged class passes

- **WHEN** a pure-JUnit domain test class carries `@Tag("fast")`
- **THEN** the tag-completeness meta-test passes for that class

### Requirement: Test categories are selectable at build time

The Maven build SHALL support selecting test categories through JUnit 5 tag expressions (`-Dgroups`, `-DexcludedGroups`) uniformly across all backend modules. An unqualified `mvn test` SHALL run all categories. Selection SHALL be opt-in and SHALL NOT change the default full-suite behaviour.

#### Scenario: Fast loop runs without containers

- **WHEN** `cd backend && mvn test -Dgroups=fast` is executed
- **THEN** only `fast`-tagged tests run, no Testcontainers container is started, and the run completes without Docker dependency

#### Scenario: Pre-merge selection composes categories

- **WHEN** `cd backend && mvn test -Dgroups='fast|integration'` is executed
- **THEN** all `fast` and `integration` tests run and `e2e` and `architecture` tests are skipped

#### Scenario: Default run is unchanged

- **WHEN** `cd backend && mvn test` is executed without `-Dgroups`
- **THEN** every category runs, matching the pre-change full suite

### Requirement: Integration tests share one relational container per JVM

All `integration`-tagged test classes requiring PostgreSQL SHALL obtain it from a shared singleton container started once per JVM, with schema state reset between classes. No `integration`-tagged class SHALL start its own PostgreSQL container.

#### Scenario: Container starts once for many classes

- **WHEN** multiple `integration`-tagged test classes run in the same JVM
- **THEN** exactly one PostgreSQL container is started for the whole run and classes receive a reset schema

#### Scenario: Class-level container declaration is absent

- **WHEN** an `integration`-tagged class is inspected
- **THEN** it declares no per-class PostgreSQL Testcontainers definition

### Requirement: Kafka end-to-end acceptance coverage stays singular per capability

For each asynchronous messaging capability, exactly one `e2e`-tagged test class SHALL exercise the full broker round-trip as acceptance. Additional behavioural assertions for the same capability SHALL live as transactional-outbox persistence assertions and listener/relay unit tests.

#### Scenario: Outbox contract asserted without broker

- **WHEN** a messaging use case commits its transaction in a persistence-level test
- **THEN** the committed outbox row is asserted directly, without a Kafka broker

#### Scenario: One e2e acceptance per messaging capability

- **WHEN** the `e2e`-tagged classes are enumerated
- **THEN** each asynchronous messaging capability appears at most once

### Requirement: Business rules are asserted at the application layer

Assertions of business rules (lifecycle transitions, validation, grant enforcement, tenancy filtering) SHALL live in `fast`-tagged application-layer tests against in-memory port fakes. REST-level `integration` tests SHALL assert contract concerns only (route reachability, status codes, JSON shape consistent with the canonical `openapi.yaml`, error-mapping wiring).

#### Scenario: Lifecycle rule fails at application layer

- **WHEN** a lifecycle transition rule is violated
- **THEN** a `fast`-tagged application-layer test fails without Spring context or containers

#### Scenario: Integration class carries no duplicated business rule

- **WHEN** an `integration`-tagged REST test class is inspected
- **THEN** its assertions cover contract concerns and do not duplicate an application-layer business-rule assertion

### Requirement: The agent test-loop policy is documented and matches the taxonomy

`AGENTS.md` SHALL document which verification loop to run per change type, expressed in the category taxonomy and module-scoped commands, and `docs/agents/codebase-map.md` SHALL list the corresponding build commands. The policy SHALL keep the single full-reactor run reserved for final verification.

#### Scenario: Policy names the fast loop for domain changes

- **WHEN** an agent changes `mmx-domain` production code
- **THEN** the documented policy directs a module-scoped `fast` command, not a full-reactor run

#### Scenario: Policy reserves the full run

- **WHEN** the documented policy is read
- **THEN** the full-reactor `mvn test` appears exactly as the final-verification step

