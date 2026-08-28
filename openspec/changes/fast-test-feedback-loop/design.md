# Design — fast-test-feedback-loop

## Context

Measured baseline (2026-08-27, surefire reports):

- ~117 test classes / ~670 methods across 7 backend modules.
- 11 Testcontainers classes in `mmx-bootstrap` each pay individual PostgreSQL (and Kafka) cold-start.
- Slowest classes: `JpaOrderRepositoryTest` 32s, `TraderWorkflowE2ETest` 14.6s, `HexagonalArchitectureTest` 12.6s, Kafka e2e 7–9s, REST integration ~4–5s each.
- Only one tag repo-wide (`architecture`); surefire at defaults (no `groups` wiring, no parallelism).

Goals: an agent can get a meaningful red/green signal in seconds-to-a-couple-minutes for most changes; the full suite remains the merge authority.

Non-goals: CI pipeline restructuring (a natural follow-up, out of scope); frontend loop (already fast); changing any production behaviour; deleting coverage (net coverage stays equivalent — assertions move, they don't disappear).

## Decision 1 — JUnit 5 tags + surefire `-Dgroups`, not Maven profiles

**Choice:** express categories as JUnit 5 tags (`fast`, `integration`, `e2e`, `architecture`) and wire surefire to the standard `groups` / `excludedGroups` properties in `backend/pom.xml`.

**Alternatives rejected:**

- *Maven profiles per category* (`-Pfast-tests`): duplicates inclusion/exclusion filters per module; tag expressions are simpler, per-module-agnostic, and compose (`-Dgroups='fast|integration'`).
- *Module-based selection only* (`-pl mmx-domain,mmx-application`): works today but conflates location with nature — `mmx-bootstrap` mixes fast support tests with container tests, and `mmx-adapter-out-persistence` mixes both too. Tags keep the source of truth on the test itself.

**Default behaviour unchanged:** `mvn test` with no `-Dgroups` runs everything, exactly as today. Selection is opt-in — zero risk to existing CI and to agents that ignore it.

## Decision 2 — Category definitions (one per class)

| Tag | Definition | Where they live today |
|---|---|---|
| `fast` | Pure JVM: no Spring context, no containers, no broker. In-memory fakes allowed. | `mmx-domain` (all), `mmx-application` (all), adapter unit tests without `@SpringBootTest` |
| `integration` | Spring context + shared PostgreSQL Testcontainer; HTTP-surface or persistence-surface assertions | `mmx-bootstrap/src/test/.../rest/*`, `*RestApiIntegrationTest`, persistence integration tests, migration tests |
| `e2e` | Full stack incl. Kafka broker; asynchronous outbox relay consumed | `mmx-bootstrap/src/test/.../e2e/*` |
| `architecture` | ArchUnit rule enforcement (existing tag) | `DomainArchitectureTest`, `HexagonalArchitectureTest` |

One tag per class (a class needing two natures is a class to split — same lens as Phase C's `JpaOrderRepositoryTest` split). Tag completeness is enforced by a red-first meta-test that scans test classes and fails on a missing category tag, so the taxonomy cannot silently rot.

## Decision 3 — Singleton containers + context caching (Phase C)

- A shared test-support base class (in `mmx-bootstrap/src/test/.../support/`) starts **one** PostgreSQL container per JVM (static, started once, `DependsOn`/ordered schema reset between classes). Integration classes extend it instead of declaring their own container.
- Kafka e2e classes share **one** broker lifecycle across the e2e suite (topic-per-test isolation instead of broker-per-class).
- Integration classes converge on one `@SpringBootTest` configuration (same properties, same test slices) so Spring's context cache is actually hit — today's per-class bespoke configs force context restarts.
- `JpaOrderRepositoryTest` is split by concern (desk queries / tenancy scoping / routed-hub idempotency / index behaviour), each split class inheriting the shared container.
- JUnit-platform parallelism is enabled **only** for `mmx-domain` and `mmx-application` (annotations or `junit-platform.properties` scoped there) — pure-unit modules with no shared state.

## Decision 4 — Pyramid placement rules (Phase B)

- Business rules (lifecycle transitions, validation, grant enforcement, tenancy filtering logic) are asserted in `mmx-application` tests against in-memory `port/out` fakes. Those tests are `fast`.
- REST integration tests keep **contract concerns only**: route reachable, status code, JSON shape consistent with the canonical `openapi.yaml`, error mapping wiring. One smoke per endpoint.
- Migration method per rule (red-first in the TDD sense of *writing the test first against existing behaviour*): (1) write the application-level test capturing the same rule; (2) run it green against current production code; (3) delete the duplicated assertion from the integration class; (4) integration class keeps only its contract smoke. No production code changes are expected — if one is needed, that is a signal the rule lived only in wiring and gets flagged.
- Messaging: assert the transactional outbox row at persistence level (same-tx commit is the real contract, per `back-office-outbound-messaging`); unit-test relay/listener mapping; keep exactly one Kafka e2e per messaging capability as the acceptance lock (`ExecutionHandoffKafkaIntegrationTest`, `RoutedExecutionHandoffKafkaIntegrationTest`).

## Decision 5 — Where things live (module placement)

| Artifact | Module / file |
|---|---|
| Tag wiring, parallelism config | `backend/pom.xml` (surefire) |
| Tag-completeness meta-test | `mmx-bootstrap/src/test/java/com/mmx/order/architecture/TestCategoryTaggingTest.java` (`architecture` tag — it enforces taxonomy) |
| Singleton-container base + reset convention | `mmx-bootstrap/src/test/java/com/mmx/order/support/` |
| Migrated business-rule tests | `mmx-application/src/test/java/...` alongside the service under test |
| Outbox assertion tests | `mmx-adapter-out-persistence/src/test/java/...` |
| Agent loop policy | `AGENTS.md` (new "Test feedback loop" section) + `docs/agents/codebase-map.md` build commands |

No new production modules, ports, or adapters. Dependency direction untouched.

## Risks / trade-offs

- **Shared-container state bleed** → schema-reset convention between classes; e2e keeps topic isolation.
- **Parallelism flakes** in unit modules → opt-in module scope; trivially revertible.
- **Coverage drift during Phase B migration** → each migration is a single tasks.md step pairing the new application test with the integration de-duplication in the same step; a migration step may not delete an assertion without its replacement landing first.

---

## Measurements — baseline (captured 2026-08-27)

Source: `cd backend && mvn test` full-reactor run, per-class durations from `target/surefire-reports/*.txt`. This is the before/after comparison source for Task 4.1.

> Note: the repo was mid cross-org WIP when measured; a minimal pre-existing compile fix
> (`OrderPersistenceMapper.toDomain` passing a `null` `RejectionOrigin` arg) was required to get a
> clean full run. The taxonomy meta-test (`TestCategoryTaggingTest`) was present for this run and
> intentionally fails (red-first, Task 1.1); it is trivial (~0.5s) and excluded from totals below.

**Slowest classes by module (s):**

| Module | Class | Time (s) |
|--------|-------|----------|
| mmx-bootstrap | `RestScopeIntegrationTest` | 35.19 |
| mmx-bootstrap | `RoleScopedSettingsRestApiIntegrationTest` | 39.81 |
| mmx-bootstrap | `OrderRoutingIntakeIntegrationTest` | 34.23 |
| mmx-bootstrap | `DelegatedGrantDirectorySmokeTest` | 34.58 |
| mmx-bootstrap | `DelegatedGrantsRestApiIntegrationTest` | 35.47 |
| mmx-bootstrap | `CrossOrgRoutingPartialUniqueIndexTest` | 34.18 |
| mmx-bootstrap | `TraderWorkflowE2ETest` | 36.13 |
| mmx-bootstrap | `ExecutionHandoffKafkaIntegrationTest` | 37.22 |
| mmx-bootstrap | `RoutedExecutionHandoffKafkaIntegrationTest` | 42.98 |
| mmx-bootstrap | `GlobalAccountsRestApiIntegrationTest` | 8.62 |
| mmx-bootstrap | `OrderRestApiIntegrationTest` | 5.52 |
| mmx-bootstrap | `HexagonalArchitectureTest` | 11.36 |
| mmx-adapter-out-persistence | `JpaOrderRepositoryTest` | 19.96 |
| mmx-adapter-in-rest | `OrderUpdateControllerTest` | 6.49 |
| mmx-domain | `DomainArchitectureTest` | 3.50 |

### Per-class durations (all backend modules, sorted by time)

| Test class | Time (s) |
|-----------|----------|
| com.mmx.order.e2e.RoutedExecutionHandoffKafkaIntegrationTest | 42.98 |
| com.mmx.order.rest.RoleScopedSettingsRestApiIntegrationTest | 39.81 |
| com.mmx.order.e2e.ExecutionHandoffKafkaIntegrationTest | 37.22 |
| com.mmx.order.e2e.TraderWorkflowE2ETest | 36.13 |
| com.mmx.order.rest.DelegatedGrantsRestApiIntegrationTest | 35.47 |
| com.mmx.order.rest.RestScopeIntegrationTest | 35.19 |
| com.mmx.order.rest.DelegatedGrantDirectorySmokeTest | 34.58 |
| com.mmx.order.rest.OrderRoutingIntakeIntegrationTest | 34.23 |
| com.mmx.order.migration.CrossOrgRoutingPartialUniqueIndexTest | 34.18 |
| com.mmx.order.adapter.out.persistence.JpaOrderRepositoryTest | 19.96 |
| com.mmx.order.architecture.HexagonalArchitectureTest | 11.36 |
| com.mmx.order.rest.GlobalAccountsRestApiIntegrationTest | 8.621 |
| com.mmx.order.adapter.in.rest.OrderUpdateControllerTest | 6.493 |
| com.mmx.order.rest.OrderRestApiIntegrationTest | 5.523 |
| com.mmx.order.config.TransactionalOrderLifecycleAtomicityIntegrationTest | 5.459 |
| com.mmx.order.architecture.DomainArchitectureTest | 3.497 |
| com.mmx.order.config.TransactionalOrderLifecyclePropagationFailureIntegrationTest | 3.036 |
| com.mmx.order.application.service.DeskQueryScopeTest | 2.643 |
| com.mmx.order.adapter.out.messaging.RoutingOutcomeConsumerTest | 2.630 |
| com.mmx.order.adapter.out.integration.RemoteReferenceDataAdapterTest | 2.519 |
| com.mmx.order.adapter.in.rest.CurrencySettingsControllerTest | 2.031 |
| com.mmx.order.adapter.out.messaging.BackOfficeOutboxRelayWorkerTest | 1.915 |
| com.mmx.order.adapter.out.persistence.JpaOrderRepositoryRoutedHubIdempotencyTest | 1.015 |
| com.mmx.order.adapter.in.rest.OrderCreationOptionsControllerTest | 0.957 |
| com.mmx.order.adapter.in.rest.OrderLifecycleControllerTest | 0.941 |
| com.mmx.order.rest.OrderCreationOptionsRestApiIntegrationTest | 0.820 |
| com.mmx.order.adapter.in.rest.TermRateSettingsControllerTest | 0.722 |
| com.mmx.order.domain.model.MoneyMarketOrderLifecycleTest | 0.707 |
| com.mmx.order.adapter.in.rest.crossorg.CrossOrgGatewaySecurityTest | 0.695 |
| com.mmx.order.adapter.in.rest.OrderAssignmentControllerTest | 0.642 |
| com.mmx.order.application.service.IntakeServiceRemoteDispatchTest | 0.626 |
| com.mmx.order.adapter.in.rest.OrderExecutionControllerTest | 0.594 |
| com.mmx.order.adapter.in.rest.OnCallRateTraderControllerTest | 0.574 |
| com.mmx.order.adapter.in.rest.crossorg.CrossOrgReferenceDataControllerTest | 0.533 |
| com.mmx.order.application.service.OnCallOrderCreationOptionsServiceTest | 0.518 |
| com.mmx.order.application.service.ApplyRemoteOrderOutcomeUseCaseTest | 0.514 |
| com.mmx.order.adapter.out.persistence.currency.JpaManagedCurrencyRepositoryTest | 0.490 |
| com.mmx.order.adapter.out.persistence.termrate.TermRateRepositoryQueryTest | 0.450 |
| com.mmx.order.adapter.out.messaging.RoutingOutcomeRelayWorkerTest | 0.444 |
| com.mmx.order.adapter.out.persistence.oncall.OnCallRateSegmentJpaAdapterTest | 0.428 |
| com.mmx.order.application.service.ExecuteOrderServiceTest | 0.399 |
| com.mmx.order.adapter.out.persistence.OrderRepositoryLiveContractsTest | 0.395 |
| com.mmx.order.adapter.out.persistence.grant.JpaDelegatedGrantDirectoryIntegrationTest | 0.384 |
| com.mmx.order.application.service.IntakeServiceTest | 0.376 |
| com.mmx.order.adapter.out.persistence.institution.JpaInstitutionRepositoryTest | 0.376 |
| com.mmx.order.rest.TermRateRestApiIntegrationTest | 0.335 |
| com.mmx.order.application.service.ManageCurrencySettingsServiceTest | 0.315 |
| com.mmx.order.adapter.out.persistence.termrate.JpaTermRateRepositoryTest | 0.306 |
| com.mmx.order.adapter.out.persistence.oncall.OnCallRateRepositoryQueryTest | 0.298 |
| com.mmx.order.adapter.out.integration.RemoteRoutingGatewayRestAdapterTest | 0.264 |
| com.mmx.order.domain.model.MoneyMarketOrderCreationTest | 0.256 |
| com.mmx.order.adapter.in.rest.RoutedOrderAcceptControllerTest | 0.253 |
| com.mmx.order.adapter.out.persistence.globalaccount.JpaGlobalAccountDirectoryIntegrationTest | 0.220 |
| com.mmx.order.adapter.out.messaging.OrderExecutedV1PayloadMapperTest | 0.220 |
| com.mmx.order.adapter.out.persistence.OrderRepositoryContractLookupTest | 0.213 |
| com.mmx.order.application.service.UploadTermRatesServiceTest | 0.207 |
| com.mmx.order.contract.ContractSyncVerificationTest | 0.199 |
| com.mmx.order.application.service.UpdateOrderServiceTest | 0.196 |
| com.mmx.order.adapter.in.rest.OnCallRateConfirmationCallbackControllerTest | 0.194 |
| com.mmx.order.application.service.ReScopeUseCaseTest | 0.176 |
| com.mmx.order.application.service.CancelOnCallRateServiceTest | 0.172 |
| com.mmx.order.adapter.out.messaging.RoutingOutcomeOutboxTest | 0.169 |
| com.mmx.order.application.service.ExecuteOrderServiceRateOnlyTest | 0.154 |
| com.mmx.order.application.service.AcceptRoutedHubOrderIdempotencyTest | 0.153 |
| com.mmx.order.domain.model.RejectionOriginTest | 0.149 |
| com.mmx.order.domain.model.OrderStatusTest | 0.147 |
| com.mmx.order.application.service.RemoteRoutedOrderIntakeTest | 0.143 |
| com.mmx.order.application.service.RemotePairPropagationBypassTest | 0.128 |
| com.mmx.order.domain.policy.DelegatedGrantSubsetPolicyTest | 0.124 |
| com.mmx.order.application.service.TermOrderCreationOptionsServiceTest | 0.123 |
| com.mmx.order.application.service.OrderLifecycleServiceTest | 0.122 |
| com.mmx.order.application.service.MarkOrderAccountedServiceTest | 0.110 |
| com.mmx.order.application.service.DeskOrderQueryServiceTest | 0.109 |
| com.mmx.order.application.service.AcceptRoutedHubOrderUseCaseTest | 0.106 |
| com.mmx.order.application.service.ManageDelegatedGrantsServiceTest | 0.100 |
| com.mmx.order.adapter.out.integration.ExternalIdentityGatewayAdapterTest | 0.099 |
| com.mmx.order.application.service.AddOnCallRateServiceTest | 0.096 |
| com.mmx.order.application.service.RoutedOrderOutcomePropagationServiceTest | 0.095 |
| com.mmx.order.domain.model.RemoteOrderLifecycleTest | 0.093 |
| com.mmx.order.domain.model.LegalEntityTenancyTest | 0.086 |
| com.mmx.order.domain.model.OrderRoutingLifecycleTest | 0.081 |
| com.mmx.order.domain.model.DelegatedInstitutionGrantTest | 0.081 |
| com.mmx.order.application.service.ListLiveContractsServiceTest | 0.081 |
| com.mmx.order.domain.policy.TermRateIngestPolicyTest | 0.075 |
| com.mmx.order.application.service.OnboardInstitutionServiceTest | 0.075 |
| com.mmx.order.domain.model.LegalEntityCrossOrgTenancyTest | 0.074 |
| com.mmx.order.application.service.ManageInstitutionSettingsServiceTest | 0.073 |
| com.mmx.order.adapter.in.rest.mapper.OrderRestMapperTest | 0.068 |
| com.mmx.order.application.service.ReferenceDataOwnershipGuardTest | 0.067 |
| com.mmx.order.application.service.RemoteRoutingGatewayRetryTest | 0.065 |
| com.mmx.order.application.port.out.ExternalIdentityGatewayContractTest | 0.058 |
| com.mmx.order.domain.model.DelegatedGrantProspectiveTest | 0.057 |
| com.mmx.order.domain.policy.OrderAgainstCurrencyPolicyTest | 0.053 |
| com.mmx.order.domain.policy.ContractLivenessPolicyTest | 0.053 |
| com.mmx.order.domain.model.MmxUserIdentityTest | 0.053 |
| com.mmx.order.adapter.out.integration.PositionApiOpenPositionAdapterTest | 0.051 |
| com.mmx.order.adapter.out.persistence.mapper.OrderPersistenceMapperTest | 0.048 |
| com.mmx.order.domain.model.MoneyMarketOrderTest | 0.046 |
| com.mmx.order.application.service.AssignmentServiceTest | 0.045 |
| com.mmx.order.domain.model.OrderLegalEntityScopeTest | 0.044 |
| com.mmx.order.application.termrate.TermRateCsvParserTest | 0.044 |
| com.mmx.order.application.termrate.SampleTermRateCsvGeneratorTest | 0.044 |
| com.mmx.order.application.service.ListInstitutionsServiceTest | 0.043 |
| com.mmx.order.domain.policy.OnCallRateCurvePolicyTest | 0.038 |
| com.mmx.order.domain.model.ManagedCurrencyTest | 0.036 |
| com.mmx.order.application.port.out.DelegatedGrantDirectoryTest | 0.035 |
| com.mmx.order.application.port.out.RemoteRoutingGatewayContractTest | 0.031 |
| com.mmx.order.adapter.out.messaging.OnCallRateUpdatedV1PayloadMapperTest | 0.030 |
| com.mmx.order.domain.model.ThinProxyInstitutionTest | 0.029 |
| com.mmx.order.application.service.ConfirmOnCallRateServiceTest | 0.029 |
| com.mmx.order.domain.service.InstitutionCodeAcronymTest | 0.025 |
| com.mmx.order.domain.policy.OrderAgainstInstitutionPolicyTest | 0.025 |
| com.mmx.order.domain.model.OnCallRateTransitionTest | 0.024 |
| com.mmx.order.domain.model.RoutingIdTest | 0.018 |
| com.mmx.order.application.port.out.GlobalAccountDirectoryTest | 0.017 |
| com.mmx.order.domain.model.OnCallRateSegmentTest | 0.014 |
| com.mmx.order.domain.model.TenorTest | 0.011 |
