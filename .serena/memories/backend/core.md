# Backend core (Java / hexagonal)

## Modules (dependency inward-only, ArchUnit-enforced)
```
mmx-bootstrap → {adapter-in-rest, adapter-out-persistence, adapter-out-messaging, adapter-out-integration} → mmx-application → mmx-domain
```
- `mmx-domain` — entities, enums, policies, domain exceptions (`MoneyMarketOrder`, `OnCallRateSegment`, `TermRate`, `UserScope`, `DelegatedGrantKey`, `GlobalAccount`, `RoutedOrderLink`). No framework deps (Tier-1 rule).
- `mmx-application` — use cases `port/in`, ports `port/out`, services, commands. **Entry point for business logic — prefer over adapters.**
- `mmx-adapter-in-rest` — thin controllers implementing generated `*Api` interfaces; `GlobalExceptionHandler`; mappers. No business rules.
- `mmx-adapter-out-persistence` — JPA entities, Spring Data repos, `Jpa*Repository` adapters, `RequestScopeContextProvider`, Flyway-backed tables.
- `mmx-adapter-out-messaging` — outbox + handoff relay workers (back-office async publish, Kafka).
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
- Flyway: `mmx-bootstrap/src/main/resources/db/migration/V*.sql` (V1–V4 orders/audit, V7–V8 handoff+outbox, V9–V12 currency/institution/term_rate, V13–V14 on-call segments, V18 tenancy tables `organisation`/`legal_entity`/`mmx_user`, V20 delegated grants, V21 routing columns, V22 `global_account`).
- Adapters: `Jpa*Repository` implementing `port/out`.

## Test placement
- domain/application unit tests with in-memory fakes (`fast`); REST integration in `mmx-bootstrap/src/test/` (`*RestApiIntegrationTest`, `integration`); Kafka e2e under `mmx-bootstrap/src/test/e2e/`; ArchUnit `architecture`.

## Routing touchpoints
`ReceiveOrderService`, `RouteOrderService`, `RoutedOrderLink`, `HubLocalityResolver`, `AcceptRoutedHubOrderUseCase`, `ApplyRemoteOrderOutcomeUseCase`, ports `ExternalIdentityGateway`, `RemoteRoutingGateway` (`ResilientRemoteRoutingGateway` retry/CB), `GlobalAccountDirectory`. Invariants: silence is never terminal; routing id deterministic from client-side order id; hub dedupes on `(originatingLegalEntityCode, routingId)`.
