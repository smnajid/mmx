# Agent codebase map (mmx)

**Purpose:** Where to look in the repo — not domain definitions (see [CONTEXT.md](../../CONTEXT.md)).

**Read order before exploring code:**

1. [CONTEXT.md](../../CONTEXT.md) — vocabulary
2. This file — locations
3. Canonical HTTP contracts under `contracts/<feature>/openapi.yaml` + `api-v1.md`
4. Cross-cutting behaviour in `openspec/specs/`

---

## Repository layout

```text
mmx/
├── CONTEXT.md                 ← glossary (agents)
├── docs/agents/               ← agent docs (this file, domain.md, issue-tracker.md)
├── contracts/00N-<feature>/   ← canonical OpenAPI, AsyncAPI, JSON schemas
├── openspec/specs/            ← capability specs (SDD)
├── openspec/changes/archive/  ← historical change designs (not canonical alone)
├── backend/                   ← Java / Maven hexagonal monolith
├── frontend/src/app/          ← Angular 21 trader SPA
├── AGENTS.md / CLAUDE.md      ← agent entry points
└── scripts/                   ← ad-hoc ops scripts (not product code)
```

---

## Canonical contracts (`contracts/`)

| Folder | Topic | Canonical HTTP contract |
|--------|--------|-------------------------|
| `001-mm-order-processing/` | Core order domain, intake, lifecycle | `openapi.yaml` (superseded for desk by 002) |
| `002-trader-orders-views/` | Desk queues, order detail, on-call rates API, async handoff | `openapi.yaml` + `api-v1.md` |
| `003-managed-currency-settings/` | Managed currency catalog | `openapi.yaml` |
| `004-institution-settings/` | Institution onboarding | `openapi.yaml` |
| `005-term-rate-settings/` | Term rate CSV upload & day view | `openapi.yaml` |

**Active desk + on-call rates:** treat **`002`** OpenAPI as the primary product contract for orders and `OnCallRateSettings` paths.

**Codegen:** `backend/mmx-adapter-in-rest/pom.xml` runs OpenAPI Generator per spec (002, 003, 004, 005). Generated Java lives under `target/generated-sources/` after `mvn compile` — not committed.

**Prose mirror:** each feature's `api-v1.md` must stay aligned with `openapi.yaml`.

---

## OpenSpec (`openspec/`)

Use when behaviour is specified as a **capability** rather than a single feature folder:

| Spec | Typical code touchpoints |
|------|---------------------------|
| `backend-hexagonal-architecture` | `ArchitectureRules`, `DomainArchitectureTest`, `HexagonalArchitectureTest` |
| `trader-desk-navigation` | `frontend/…/app.routes.ts`, desk shell, header |
| `trader-order-detail-actions` | `features/order-details/` |
| `desk-order-queries` | `DeskOrderQueries`, `DeskOrderQueryService` |
| `oncall-rate-curve-management` | `OnCallRateSegment`, on-call use cases |
| `back-office-outbound-messaging` | `mmx-adapter-out-messaging`, outbox tables |
| `term-rate-daily-upload` / `term-rate-settings-ui` | term rate feature + settings UI |
| `managed-currency-settings` / `currency-settings-ui` | currency settings |
| `institution-onboarding` | institution feature |

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
| `mmx-domain` | Entities, enums, policies, domain exceptions | Status rules, `MoneyMarketOrder`, `OnCallRateSegment`, `TermRate` |
| `mmx-application` | Use cases (`port/in`), ports (`port/out`), services, commands | Business orchestration — **prefer over adapters** |
| `mmx-adapter-in-rest` | REST controllers, mappers, `GlobalExceptionHandler` | HTTP surface (thin — no business rules) |
| `mmx-adapter-out-persistence` | JPA entities, Spring Data repos, `Jpa*Repository` adapters | DB mapping, Flyway-backed tables |
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

| Controller | Area |
|------------|------|
| `OrderIntakeController` | PM intake `POST /api/v1/orders` |
| `OrderManagementController` | Desk lists, assign, execute, detail |
| `CurrencySettingsController` | `/api/v1/settings/currencies` |
| `InstitutionSettingsController` | `/api/v1/settings/institutions` |
| `TermRateSettingsController` | `/api/v1/settings/term-rates` |
| `OnCallRateTraderController` | Trader on-call rate curves |
| `OnCallRateConfirmationCallbackController` | Back-office rate confirm callback |
| `BackOfficeAccountingCallbackController` | Order accounted callback |

Implements generated `*Api` interfaces from OpenAPI (under `adapter/in/rest/generated/` after build).

### Application layer patterns

- **Orders:** `DeskOrderQueries`, `AssignOrderUseCase`, `ExecuteOrderService`, `OrderLifecycleService`, …
- **On-call rates:** `AddOnCallRateUseCase`, `ConfirmOnCallRateUseCase`, `ListOnCallRateSegmentsUseCase`, …
- **Term rates:** `UploadTermRatesUseCase`, `ListTermRatesForDayUseCase`, … + `application/termrate/` CSV parsing
- **Settings:** `ManageCurrencySettingsService`, `ManageInstitutionSettingsService`, …

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

### Tests (backend)

| Location | Level |
|----------|--------|
| `mmx-domain/src/test/` | Domain unit tests |
| `mmx-application/src/test/` | Use case / service tests |
| `mmx-adapter-*/src/test/` | Adapter unit tests |
| `mmx-bootstrap/src/test/` | REST integration (`*IntegrationTest`, `*RestApiIntegrationTest`) |

---

## Frontend (Angular)

**Root routes:** [frontend/src/app/app.routes.ts](../../frontend/src/app/app.routes.ts)

| Route prefix | Feature folder | Notes |
|--------------|----------------|-------|
| `/oncall/{received\|assigned\|executed}` | `features/oncall-orders/`, shared `assigned-orders/`, `received-orders/` | Default entry: `/oncall/received` |
| `/term/{…}` | `features/term-orders/` | Same queue pattern |
| `/orders/:id` | `features/order-details/` | Optional `?ws=&queue=` for desk highlight + back |
| `/settings/*` | `features/settings/settings-shell` + child routes | See below |

**Settings routes:** [frontend/src/app/features/settings/settings.routes.ts](../../frontend/src/app/features/settings/settings.routes.ts)

| Path | Feature |
|------|---------|
| `/settings/currencies` | `currency-settings/` |
| `/settings/institutions` | `institution-settings/` |
| `/settings/term-rates` | `term-rate-settings/` |
| `/settings/oncall-rates` | `oncall-rate-settings/` |

**Shared layers:**

| Path | Role |
|------|------|
| `core/api/*-api.service.ts` | HTTP clients aligned to OpenAPI |
| `core/models/` | TS enums/models (`order-status`, `handoff-status`, …) |
| `core/trader/` | `TraderContextService`, `DeskReturnService` |
| `shared/components/` | `order-table`, `status-badge`, dialogs |

**Tests:** `*.spec.ts` beside components; Cypress e2e under `frontend/` if present for the flow.

---

## Exploration strategy (agents)

1. **Contract first** — path and schema in `contracts/<feature>/openapi.yaml`, then controller, then use case, then domain.
2. **Hexagonal inward** — domain → application → adapter; avoid starting from JPA unless persistence-only.
3. **Grep tips**
   - API paths: `/api/v1/orders`, `/settings/institutions`
   - Java enum `ON_CALL` vs URL `oncall` vs UI label `ON-CALL` — same OnCall product
   - Generated code: search `target/generated-sources` only after `mvn -pl backend/mmx-adapter-in-rest -am compile -DskipTests`
4. **Semantic search** (Cursor index): "where is pending on-call segment enforced" before broad `grep PENDING`
5. **Material changes** — update matching `openspec/specs/` capability specs and `contracts/` per SDD governance in `openspec/config.yaml`

---

## Build commands (local context)

```bash
# Backend compile + codegen
mvn -q -pl backend/mmx-adapter-in-rest -am compile -DskipTests

# Frontend deps
cd frontend && npm ci

# Domain / application unit tests (example)
mvn -q -pl backend/mmx-domain,backend/mmx-application test
```

---

## Related docs

- [domain.md](domain.md) — how skills use CONTEXT and ADRs
- [issue-tracker.md](issue-tracker.md) — `.scratch/` issues
- [openspec/specs/money-market-order-lifecycle/spec.md](../openspec/specs/money-market-order-lifecycle/spec.md) — core order lifecycle rules
- [openspec/config.yaml](../openspec/config.yaml) — OpenSpec project context and SDD defaults
