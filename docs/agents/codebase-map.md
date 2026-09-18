# Agent codebase map (mmx)

**Purpose:** Where to look in the repo — not domain definitions (see [CONTEXT.md](../../CONTEXT.md)).

**Read order before exploring code:**

0. `mem:core` (Serena memory — activate project `mmx` first) — cross-session agent notes; follow references as relevant (see [AGENTS.md](../../AGENTS.md) §Serena)
1. [CONTEXT.md](../../CONTEXT.md) — vocabulary (MMXUser, legal-entity scope, routing, delegated grants)
2. This file — locations
3. Canonical HTTP contracts under `contracts/<feature>/openapi.yaml` + `api-v1.md`
4. Cross-cutting behaviour in `openspec/specs/`
5. Architecture decisions in `docs/adr/` when tenancy, routing, or identity is involved

---

## Repository layout

```text
mmx/
├── CONTEXT.md                 ← glossary (agents)
├── docs/
│   ├── agents/                ← agent docs (this file, domain.md, triage-labels.md, …)
│   └── adr/                   ← architecture decision records
├── contracts/00N-<feature>/   ← canonical OpenAPI, AsyncAPI, JSON schemas
├── openspec/
│   ├── specs/                 ← capability specs (SDD, canonical behaviour)
│   └── changes/archive/       ← historical change designs (not canonical alone)
├── backend/                   ← Java / Maven hexagonal monolith (reactor root: backend/pom.xml)
├── frontend/
│   ├── src/app/               ← Angular 21 trader SPA
│   └── projects/order-creation-widget/  ← embeddable PM order-creation library
├── AGENTS.md                  ← agent entry points
└── scripts/                   ← ad-hoc ops scripts (schema registry, demo seed, BO callbacks)
```

---

## Canonical contracts (`contracts/`)

| Folder | Topic | Canonical HTTP contract |
|--------|--------|-------------------------|
| `001-mm-order-processing/` | Core order domain, intake, lifecycle | `openapi.yaml` (superseded for desk by 002) |
| `002-trader-orders-views/` | Desk queues, order detail, session scope, on-call rates API, order-creation options, async handoff | `openapi.yaml` + `api-v1.md` |
| `003-managed-currency-settings/` | Managed currency catalog | `openapi.yaml` |
| `004-institution-settings/` | Institution onboarding | `openapi.yaml` |
| `005-term-rate-settings/` | Term rate CSV upload & day view | `openapi.yaml` |
| `006-delegated-institution-grants/` | Hub → client delegated institution grants | `openapi.yaml` + `api-v1.md` |

**Active desk + session + on-call rates + order-creation options:** treat **`002`** OpenAPI as the primary product contract for orders, `/api/v1/session/scope`, and `OnCallRateSettings` paths.

**Async handoff:** `002-trader-orders-views/asyncapi.yaml` + JSON schemas under `schemas/` (`OrderExecutedV1`, `OnCallRateUpdatedV1`, `OnCallRateCanceledV1`).

**Codegen (backend):** `backend/mmx-adapter-in-rest/pom.xml` runs OpenAPI Generator per spec (002, 003, 004, 005, 006). Generated Java lives under `target/generated-sources/` after `mvn compile` — not committed. Packages are namespaced (`generated.model`, `generated.settings`, `generated.grants`, …).

**Codegen (frontend):** `frontend/package.json` script `generate:api` emits TypeScript types to `frontend/src/app/core/api/generated/` from contracts 002–006. Widget types: `projects/order-creation-widget/src/lib/generated/`.

**Prose mirror:** each feature's `api-v1.md` must stay aligned with `openapi.yaml`.

**Contract gap:** `GlobalAccountsController` (`/api/v1/settings/global-accounts`) is implemented ad-hoc — no `contracts/` folder yet. Treat as POC surface; material changes need a new contract per SDD.

---

## OpenSpec (`openspec/`)

Use when behaviour is specified as a **capability** rather than a single feature folder. Full list: `openspec/specs/*/spec.md`.

| Area | Specs (examples) | Typical code touchpoints |
|------|------------------|---------------------------|
| Architecture | `backend-hexagonal-architecture` | `ArchitectureRules`, `DomainArchitectureTest`, `HexagonalArchitectureTest` |
| Identity & tenancy | `user-identity-and-scoping`, `legal-entity-tenancy` | `SessionScopeController`, `ReScopeService`, `ScopeContextProvider`, `TraderContextService`, `X-User-Id` header |
| Order lifecycle & routing | `money-market-order-lifecycle`, `order-routing`, `execution-contract-number` | `ReceiveOrderService`, `RouteOrderService`, `RoutedOrderLink`, routing columns on orders |
| Desk UX | `trader-desk-navigation`, `trader-order-detail-actions`, `desk-order-queries`, `trader-received-queue`, `trader-executed-queue` | `app.routes.ts`, `trader-desk.guard.ts`, `features/order-details/`, `DeskOrderQueryService` |
| Settings & reference data | `managed-currency-settings`, `institution-onboarding`, `term-rate-daily-upload`, `delegated-institution-grants` | settings controllers + `features/*-settings/`, `features/delegated-grants/` |
| Settings UI | `currency-settings-ui`, `term-rate-settings-ui`, `oncall-rate-settings-ui` | matching `features/` folders |
| On-call rates | `oncall-rate-curve-management` | `OnCallRateSegment`, `AddOnCallRateService`, on-call handoff outbox |
| PM intake widget | `pm-order-creation-widget`, `pm-order-creation-options` | `projects/order-creation-widget/`, `OrderCreationOptionsController`, `features/widget-playground/` |
| Back office | `back-office-outbound-messaging`, `back-office-accounting-handoff` | `mmx-adapter-out-messaging`, callback controllers, outbox tables |
| Constraints | `order-currency-constraints`, `order-institution-constraints` | intake validation in `ReceiveOrderService`, creation-options services |
| Contract governance | `frontend-contract-codegen`, `async-schema-registry-governance`, `order-adapter-mapping` | `npm run generate:api`, `scripts/register-schemas.sh` |

Project defaults: [openspec/config.yaml](../../openspec/config.yaml).

---

## Backend (hexagonal)

### Module dependency (inward only)

```text
mmx-bootstrap
  ├── mmx-adapter-in-rest
  ├── mmx-adapter-out-persistence
  ├── mmx-adapter-out-messaging
  └── mmx-adapter-out-integration
        └── mmx-application → mmx-domain
```

| Module | Role | Start here for… |
|--------|------|------------------|
| `mmx-domain` | Entities, enums, policies, domain exceptions | Status rules, `MoneyMarketOrder`, `OnCallRateSegment`, `TermRate`, `UserScope`, `DelegatedGrantKey`, `GlobalAccount`, `RoutedOrderLink` |
| `mmx-application` | Use cases (`port/in`), ports (`port/out`), services, commands | Business orchestration — **prefer over adapters** |
| `mmx-adapter-in-rest` | REST controllers, mappers, `GlobalExceptionHandler` | HTTP surface (thin — no business rules) |
| `mmx-adapter-out-persistence` | JPA entities, Spring Data repos, `Jpa*Repository` adapters, `RequestScopeContextProvider` | DB mapping, Flyway-backed tables, tenancy-scoped queries |
| `mmx-adapter-out-messaging` | Outbox, handoff relay workers, payload mappers | Async back-office publish |
| `mmx-adapter-out-integration` | `UuidReferenceGenerator`, external stubs | Reference generation, external APIs |
| `mmx-bootstrap` | `*ModuleConfiguration`, transactional use-case wrappers, `application.yml` | Wiring beans, Flyway migrations |

**Package root:** `com.mmx.order` in all modules.

### Architecture tests (ArchUnit)

Executable hexagonal rules live in shared test sources and run in CI via `mvn test`.

| Class | Module | Scope |
|-------|--------|-------|
| `ArchitectureRules` | `mmx-domain/src/test/java/com/mmx/order/architecture/` | Tier 1 + Tier 2 rule definitions (shared) |
| `DomainArchitectureTest` | `mmx-domain` | Domain framework isolation (fast TDD loop) |
| `HexagonalArchitectureTest` | `mmx-bootstrap` | Full cross-module rules (`@Tag("architecture")`) |

Scoped commands during development:

```bash
cd backend && mvn test -pl mmx-domain -Dtest=DomainArchitectureTest
cd backend && mvn test -pl mmx-bootstrap -am -Dtest=HexagonalArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
```

Tier 2 enforces REST adapters depend on `application.port.in` (not `application.service`) and `application.exception` (not nested service exceptions). OpenAPI-generated code under `adapter.in.rest.generated` is excluded from caller-side rules.

### REST controllers (`mmx-adapter-in-rest`)

| Controller | Area | Contract |
|------------|------|----------|
| `OrderIntakeController` | PM intake `POST /api/v1/orders` | 002 `IntakeApi` |
| `OrderManagementController` | Desk lists, assign, execute, detail | 002 `OrdersApi` |
| `SessionScopeController` | `POST /api/v1/session/scope` (re-scope active legal entity + role) | 002 `SessionApi` |
| `OrderCreationOptionsController` | PM widget option endpoints under `/api/v1/order-creation/*` | 002 `OrderCreationApi` |
| `CurrencySettingsController` | `/api/v1/settings/currencies` | 003 |
| `InstitutionSettingsController` | `/api/v1/settings/institutions` | 004 |
| `TermRateSettingsController` | `/api/v1/settings/term-rates` | 005 |
| `OnCallRateTraderController` | Trader on-call rate curves | 002 |
| `DelegatedGrantsController` | `/api/v1/settings/delegated-grants` | 006 `DelegatedGrantsApi` |
| `GlobalAccountsController` | `/api/v1/settings/global-accounts` | **no contract yet** |
| `OnCallRateConfirmationCallbackController` | Back-office rate confirm callback | 002 (callback path) |
| `BackOfficeAccountingCallbackController` | Order accounted callback | 002 (callback path) |

Implements generated `*Api` interfaces from OpenAPI (under `adapter/in/rest/generated/` after build).

**Identity header:** requests use **`X-User-Id`** (umbrella MMXUser identity). Active `(LegalEntity, role)` is session-scoped via `/api/v1/session/scope`, not a per-request header.

### Application layer patterns

- **Orders:** `DeskOrderQueryService`, `AssignmentService`, `ExecuteOrderService`, `OrderLifecycleService`, `UpdateOrderService`, `RouteOrderService`, `ListLiveContractsService`, …
- **Identity:** `ResolveUserScopeService`, `ReScopeService`; port `ScopeContextProvider`
- **On-call rates:** `AddOnCallRateService`, `ConfirmOnCallRateService`, `ListOnCallRateSegmentsService`, `CancelOnCallRateService`, …
- **Term rates:** `UploadTermRatesService`, `ListTermRatesForDayService`, … + `application/termrate/` CSV parsing
- **Settings:** `ManageCurrencySettingsService`, `ManageInstitutionSettingsService`, `OnboardInstitutionService`, `ManageDelegatedGrantsService`, `ManageGlobalAccountsService`, …
- **PM widget options:** `TermOrderCreationOptionsService`, `OnCallOrderCreationOptionsService`

### Persistence & schema

- **Flyway:** `backend/mmx-bootstrap/src/main/resources/db/migration/V*.sql`
- **JPA entities:** `mmx-adapter-out-persistence/.../entity/`
- **Adapters:** `Jpa*Repository.java` implementing `port/out` interfaces

| Migration (examples) | Table / concern |
|----------------------|-----------------|
| V1–V4 | `money_market_order`, audit, indexes |
| V7–V8 | `handoff_status`, back-office outbox |
| V9–V10 | `managed_currency` |
| V11 | `institution` |
| V12 | `term_rate` |
| V13–V14 | `oncall_rate_segment`, on-call handoff outbox |
| V16 | live-contract query indexes |
| V18 | `organisation`, `legal_entity`, `mmx_user`, tenancy columns |
| V19 | demo user seed data |
| V20 | `delegated_institution_grant`, proxy institution columns |
| V21 | order routing columns (`routing_id`, originating entity/ref) |
| V22 | `global_account` |

### Tests (backend)

| Location | Level |
|----------|--------|
| `mmx-domain/src/test/` | Domain unit tests |
| `mmx-application/src/test/` | Use case / service tests |
| `mmx-adapter-*/src/test/` | Adapter unit tests (+ some persistence integration tests) |
| `mmx-bootstrap/src/test/` | REST integration (`*RestApiIntegrationTest`, `*IntegrationTest`), Kafka e2e under `e2e/` |

---

## Frontend (Angular)

**Root routes:** [frontend/src/app/app.routes.ts](../../frontend/src/app/app.routes.ts)

| Route prefix | Feature folder | Notes |
|--------------|----------------|-------|
| `/oncall/{received\|assigned\|executed}` | `features/oncall-orders/`, shared `assigned-orders/`, `received-orders/` | Default entry: `/oncall/received`; guarded by `traderDeskGuard` |
| `/term/{…}` | `features/term-orders/` | Same queue pattern; guarded by `traderDeskGuard` |
| `/orders/:id` | `features/order-details/` | Optional `?ws=&queue=` for desk highlight + back |
| `/settings/*` | `features/settings/settings-shell` + child routes | ClientRepresentative entry; desk users can navigate here |
| `/dev/widget-playground` | `features/widget-playground/` | Dev-only PM widget harness (`isDevMode()`) |

**App shell:** [frontend/src/app/app.ts](../../frontend/src/app/app.ts) — desk nav, session user picker, scope switcher (`SessionApiService` + `TraderContextService`). Desk nav hidden for ClientRepresentative and on `/settings`.

**Settings routes:** [frontend/src/app/features/settings/settings.routes.ts](../../frontend/src/app/features/settings/settings.routes.ts)

| Path | Feature |
|------|---------|
| `/settings/currencies` | `currency-settings/` |
| `/settings/institutions` | `institution-settings/` |
| `/settings/term-rates` | `term-rate-settings/` |
| `/settings/oncall-rates` | `oncall-rate-settings/` |
| `/settings/delegated-grants` | `delegated-grants/` |
| `/settings/global-accounts` | `global-accounts/` |

**PM order-creation widget (library):** `frontend/projects/order-creation-widget/` — built with `npm run build:widget`; consumed by host apps or exercised via widget playground.

**Shared layers:**

| Path | Role |
|------|------|
| `core/api/generated/*.ts` | OpenAPI-derived types (regenerate via `npm run generate:api`) |
| `core/api/*-api.service.ts` | HTTP clients (orders, settings, session, delegated grants, global accounts, order-creation options) |
| `core/api/user-api-headers.ts` | `X-User-Id` header helper |
| `core/models/` | TS enums/models (`order-status`, `handoff-status`, …) |
| `core/trader/` | `TraderContextService`, `DeskReturnService`, `trader-desk.guard.ts`, `received-view-mode.service.ts` |
| `shared/components/` | `order-table`, `status-badge`, `confirm-dialog` |
| `shared/amount-input/` | Amount parsing directive (also mirrored in widget project) |

**Tests:** `*.spec.ts` beside components (Vitest via `ng test`); Cypress e2e: `frontend/cypress/e2e/trader-workflow.cy.ts`.

---

## Exploration strategy (agents)

1. **Contract first** — path and schema in `contracts/<feature>/openapi.yaml`, then controller, then use case, then domain.
2. **Hexagonal inward** — domain → application → adapter; avoid starting from JPA unless persistence-only.
3. **Tenancy & role** — read `CONTEXT.md` + `docs/adr/0001`–`0004`; grep `ScopeContextProvider`, `X-User-Id`, `LegalEntityCode` when behaviour differs by hub vs client.
4. **Grep tips**
   - API paths: `/api/v1/orders`, `/api/v1/session/scope`, `/settings/delegated-grants`
   - Java enum `ON_CALL` vs URL `oncall` vs UI label `ON-CALL` — same OnCall product
   - Generated code: search `target/generated-sources` only after `cd backend && mvn -pl mmx-adapter-in-rest -am compile -DskipTests`
5. **Semantic search** (Cursor index): "where is pending on-call segment enforced" before broad `grep PENDING`
6. **Material changes** — update matching `openspec/specs/` capability specs and `contracts/` per SDD governance in `openspec/config.yaml`

---

## Build commands (local context)

Maven reactor root is **`backend/`** (no root `pom.xml` at repo top).

```bash
# Backend compile + codegen
cd backend && mvn -q -pl mmx-adapter-in-rest -am compile -DskipTests

# Category-selected tests (see AGENTS.md "Test feedback loop" for the loop-by-change-type policy)
# Every backend test class carries exactly one tag: fast | integration | e2e | architecture.
cd backend && mvn test -Dgroups=fast                      # unit loop only — no containers (all modules)
cd backend && mvn test -Dgroups='fast|integration'        # compose categories
cd backend && mvn test -Dgroups=integration               # PostgreSQL integration loop
cd backend && mvn test -Dgroups=e2e                       # Kafka / full-workflow acceptance
cd backend && mvn test -pl mmx-domain -Dtest=<Class>      # one module-scoped fast class
cd backend && mvn test -pl mmx-application -Dtest=<Class> # one application fast class
cd backend && mvn test -pl mmx-adapter-in-rest -Dtest=<Class>  # one REST-controller unit
cd backend && mvn test -pl mmx-adapter-out-persistence -am -Dtest='<Jpa...Test>'  # persistence integration

# Domain / application unit tests (example)
cd backend && mvn -q -pl mmx-domain,mmx-application test

# Frontend unit tests
cd frontend && npm test

# Cypress e2e (app must be running)
cd frontend && npm run e2e

# Cross-org two-deployment stack (CGD@CGEG client :8082 → LOC@LODH hub :8080, identity stub :8090)
./mmx-cross-org-start.sh [--frontend]        # profiles: application-lodh.yml / application-cgeg.yml
./scripts/cross-org-smoke.sh                 # end-to-end order-flow check (intake → leg A → execute → leg B)
```

---

## Related docs

- [domain.md](domain.md) — how skills use CONTEXT and ADRs
- [issue-tracker.md](issue-tracker.md) — `.scratch/` issues
- [triage-labels.md](triage-labels.md) — issue status labels
- [docs/adr/](../adr/) — tenancy, routing, identity, back-office boundaries
- [openspec/specs/money-market-order-lifecycle/spec.md](../../openspec/specs/money-market-order-lifecycle/spec.md) — core order lifecycle rules
- [openspec/config.yaml](../../openspec/config.yaml) — OpenSpec project context and SDD defaults
