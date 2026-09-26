# Backend core (Java / hexagonal)

## Modules (dependency inward-only, ArchUnit-enforced)
```
mmx-bootstrap → {adapter-in-rest, adapter-out-persistence, adapter-out-messaging, adapter-out-integration} → mmx-application → mmx-domain
```
- `mmx-domain` — entities, enums, policies, domain exceptions (`MoneyMarketOrder`, `Institution`, `ClientEnablement`/`EffectiveEnablement`, `OnCallRateSegment`, `TermRate`, `UserScope`, `DelegatedGrantKey`, `GlobalAccount`, `RoutedOrderLink`). No framework deps (Tier-1 rule).
- `mmx-application` — use cases `port/in`, ports `port/out`, services, commands. **Entry point for business logic — prefer over adapters.**
- `mmx-adapter-in-rest` — thin controllers implementing generated `*Api` interfaces; `GlobalExceptionHandler`; mappers. No business rules.
- `mmx-adapter-out-persistence` — JPA entities, Spring Data repos, `Jpa*Repository` adapters, `RequestScopeContextProvider`, Flyway-backed tables.
- `mmx-adapter-out-messaging` — outbox + handoff relay workers (back-office async publish, Kafka); institution export relay (`InstitutionExportRelayWorker` → `mmx.institution.{legalEntityCode}`).
- `mmx-adapter-out-integration` — `UuidReferenceGenerator`, external stubs.
- `mmx-bootstrap` — `*ModuleConfiguration` wiring, transactional use-case wrappers, `application.yml`, Flyway migrations.

Package root: `com.mmx.order` in ALL modules.

## Architecture tests (executable rules)
- Shared rule defs: `ArchitectureRules` in `mmx-domain/src/test/java/com/mmx/order/architecture/`.
- `DomainArchitectureTest` (mmx-domain, fast), `HexagonalArchitectureTest` (mmx-bootstrap, `@Tag("architecture")`).
- Tier 2: REST adapters must depend on `application.port.in` (not `.service`) and `application.exception` (not nested service exceptions). Generated `adapter.in.rest.generated` excluded from caller rules.

## Key controllers (`mmx-adapter-in-rest`)
`OrderIntakeController` (POST /api/v1/orders, PM intake with `legalEntityCode` in body), `OrderManagementController`, `SessionScopeController` (POST /api/v1/session/scope), `OrderCreationOptionsController`, `CurrencySettingsController` (003), `InstitutionSettingsController` (004), `TermRateSettingsController` (005), `DelegatedGrantsController` (006), `OnCallRateTraderController`, `GlobalAccountsController` (**no contract yet — POC surface**), `OnCallRateConfirmationCallbackController`, `BackOfficeAccountingCallbackController`. Contract folder 002 is primary for orders/session/on-call rates.

## Identity & tenancy
- `X-User-Id` header on trader/settings requests (MMXUser umbrella identity, replaced legacy `X-Trader-Id`).
- Active `(LegalEntity, role)` scope is session state: `ResolveUserScopeService`, `ReScopeService`, port `ScopeContextProvider`; `TraderContextService` on hub desk. Server never accepts free per-request entity choice.
- PM intake is the exception: `legalEntityCode` required in intake body (PM authenticated at org level).

## Persistence
- Flyway: `mmx-bootstrap/src/main/resources/db/migration/V*.sql` (V1–V4 orders/audit, V7–V8 handoff+outbox, V9–V12 currency/institution/term_rate, V13–V14 on-call segments, V18 tenancy tables `organisation`/`legal_entity`/`mmx_user`, V20 delegated grants, V21 routing columns, V22 `global_account`, V25 routing-outcome outbox, V27 institution counterparty accounts/`version` + order account snapshots + `client_institution_enablement` + `institution_export_outbox` (drops the V20 hub-link FKs: a remote client holds no hub rows), V26 cross-org demo topology seed (CGD@CGEG → LOC@LODH, ON CONFLICT DO NOTHING — hub needs CGD registered for leg-A membership; client needs the LOC row for the connectedHub FK).
- Adapters: `Jpa*Repository` implementing `port/out`.

## Test placement
- domain/application unit tests with in-memory fakes (`fast`); REST integration in `mmx-bootstrap/src/test/` (`*RestApiIntegrationTest`, `integration`); Kafka e2e under `mmx-bootstrap/src/test/e2e/`; ArchUnit `architecture`.

## Routing touchpoints
`IntakeService`, `RoutedOrderIntake` (local), `RemoteRoutedOrderIntake` (client side of leg A), `AcceptRoutedHubOrderService` (hub side), `RoutedOrderLink`, `HubLocalityResolver`, `AcceptRoutedHubOrderUseCase`, `ApplyRemoteOrderOutcomeUseCase`, ports `ExternalIdentityGateway`, `RemoteRoutingGateway` (`ResilientRemoteRoutingGateway` retry/CB), `GlobalAccountDirectory`. Invariants: silence is never terminal; routing id deterministic from client-side order id; hub dedupes on `(originatingLegalEntityCode, routingId)`.

## Institutions: onboarded, closed to new business, accounts
- One `Institution` aggregate: hub-native (no link) or client-onboarded (`HubInstitutionLink` to `(hubLE, hubCode)`, display name frozen "{hub name} via {hubLE}"). `ThinProxyInstitution` is gone; "proxy" must not reappear in code/UI/errors.
- Every deployment's `InstitutionRepository` is local JPA — the remote (cross-Org) client STORES its onboarded institutions. Only grants/currencies/rates stay live hub reads; hub display names come via `HubInstitutionCatalog` (remote on CGEG, in-process otherwise).
- `active=false` == **closed to new business** (hub: deactivated; client: offboarded). Operation-aware: `NewBusinessPolicy` refuses SUBSCRIPTION/INCREASE only; DECREASE/REDEMPTION pass even on a revoked grant (`DelegatedGrantDirectory.resolve*(…, op)` default methods). Single rule entry point: `OrderAgainstInstitutionPolicy.refusal(...)` / `validateExecute` — reuse it, don't re-implement at a call site.
- Counterparty account for the OrderType is required on every operation (client and hub side); snapshot stamped at execute (`counterparty_account`), client's copied to the hub-side order at routing (`client_counterparty_account`).
- Client enablement is stored per `(institution, currency)` and intersected with the grant at read time (never pruned). Remote client checks only its half; the hub's leg-A grant check is the other half.
- A propagated client-side execution keeps the client's own onboarded institution code (not the hub code) — lifecycle match depends on it.
- Settings mutations write the export outbox in the same tx (`Propagation.MANDATORY` adapter) — call through the `Transactional*UseCase` beans.

## Hub-pair locality dispatch (terminal transitions)
- `RoutedPairLocalityResolver` (port `application/port/out`; bean in `OrderModuleConfiguration` = originating LE's org == deployment org, unregistered LE → REMOTE).
- Hub-side execute/cancel/reject: REMOTE pair → `RoutingOutcomeOutbox.schedule*` leg-B row ONLY (no client-side lookup — spec `order-routing` §Remote outcome propagation); LOCAL pair → `RoutedOrderOutcomePropagationService` unchanged (throw-and-rollback on broken pair).
- Remote pair's `OrderExecutedV1` carries `routingId` + `originatingLegalEntityCode` only; client fields omitted (`OrderExecutedV1PayloadMapper` null-guards — schema marks them optional; BO correlates on the pair).

## Role-scoped deployment gotchas
- `role=hub` context needs `java.time.Clock` + `RoutingOutcomeOutbox` beans — both unconditional in `CrossOrgRoutingModuleConfiguration` (lifecycle services take the port on every deployment; `RoutingOutcomeRelayWorker` stays hub-only).
- `rest-test` profile seeders are hub-shaped `ApplicationRunner`s that run at context startup → client-role context tests must NOT activate `rest-test`; datasource from `SharedPostgresTestBase` dynamic props + disable `mmx.backoffice.outbox.relay-enabled` / `mmx.oncall.outbox.relay-enabled` / `mmx.institution.outbox.relay-enabled` (else relay polls race the Flyway clean+migrate).
- Desk/settings default scope is resolution-order dependent (`demo-trader` sorts to CGD after V26) — scripts/tests must pin scope via `POST /api/v1/session/scope`.

## Cross-org local dev stack
`./mmx-cross-org-start.sh` boots BOTH deployments: LODH hub :8080 (DB `mmx`, profile `application-lodh.yml`) + CGEG client :8082 (DB `mmx_cgeg`, profile `application-cgeg.yml`, `reference-data-remote: true` — grants/rates read live from the hub; onboarded institutions stored locally) + identity stub :8090 (`scripts/identity-stub.py`). `--frontend` adds LODH UI :4200 + CGEG UI :4201 (`proxy-cgeg.conf.json`). Verify with `./scripts/cross-org-smoke.sh` (idempotent; exercises grant + LOC accounts → CGD onboarding with accounts + client enablement → intake on the onboarded code/leg A → hub execute → leg B EXECUTED). Docker Hub may 429 on `redpandadata/console`; `docker compose up -d postgres redpanda` is enough for the backends.
