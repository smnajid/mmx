# Implementation Plan: Trader order views and accounting handoff (User Story 1)

**Branch**: `002-trader-orders-views` | **Date**: 2026-05-09 | **Spec**: [spec.md](spec.md)  
**Input**: [spec.md](spec.md) — **scope of this plan pass**: **User Story 1** (P1) — Term vs OnCall workspace structure, default **OnCall**, no cross-session workspace persistence

## Summary

Deliver a **desk layout** where traders work in one of two parallel **workspaces** — **Term** and **OnCall** — each exposing the same three **sub-views**: **Received**, **Assigned**, and **Executed**. List APIs and UI routes MUST filter by domain `OrderType` so Term and OnCall orders **never** appear mixed on the same workspace surface ([FR-001](spec.md)). A **new authenticated session** MUST land on **OnCall** until the user switches workspace ([spec.md](spec.md) Clarifications). Backend lists that today mix types (notably flat `GET /api/v1/orders/assigned`) are **replaced or deprecated** in favor of workspace-scoped GETs defined in [contracts/openapi.yaml](contracts/openapi.yaml) v1.1.0.

**Does not cover in this iteration**: desk-wide Assigned visibility (User Story 2), Received horizon / show-all (US3), accounting handoff and executed-not-accounted cohort (US4–US5) — those stories build on the routing and API shape established here.

**Artifacts produced with this command**: [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/openapi.yaml), [contracts/api-v1.md](contracts/api-v1.md), [quickstart.md](quickstart.md). **Not** produced here: `tasks.md` (use `/speckit.tasks`).

## Technical Context

**Language/Version**: Java 25; TypeScript / Angular 21 (existing monorepo)  
**Primary Dependencies**: Spring Boot 4.x (adapters, bootstrap); Angular standalone components  
**API contract**: OpenAPI 3 — **canonical file** `specs/002-trader-orders-views/contracts/openapi.yaml` (v1.1.0 superset of 001 + workspace list operations); Maven OpenAPI Generator must use this path after migration (see below)  
**Storage**: PostgreSQL + Flyway (unchanged); **no** schema change for US1  
**Testing**: JUnit 5 + Spring tests; Jasmine/Karma + Cypress for SPA  
**Target Platform**: JVM server + modern browser  
**Project Type**: Web application (Modular Monolith — same as 001)  
**Build tool**: Maven (multi-module backend), npm (frontend)  
**Performance / scale**: Internal desk; no new numeric targets for US1  

## Constitution Check

*GATE: Must pass before implementation. Re-check after contract and code changes.*

| # | Principle | Status | Evidence |
|---|-----------|--------|----------|
| I | Hexagonal Architecture, Modular Monolith, contract-first REST + codegen | ✅ PASS | New list operations are use cases behind `OrderRepository` / services; REST controllers implement generated `*Api` after codegen; [openapi.yaml](contracts/openapi.yaml) is canonical |
| II | Domain Integrity | ✅ PASS | No new domain rules for US1; filtering uses existing `OrderType` / `OrderStatus` |
| III | Idempotency & Integration Boundaries | ✅ PASS | US1 read-only list additions; intake unchanged |
| IV | Testing Discipline | ✅ PASS | Application tests for new query methods; adapter tests for new GETs; SPA e2e for default **OnCall** route and no cross-type mixing |
| V | Auditability & Security | ✅ PASS | No new mutating endpoints in US1 lists |
| VI | Simplicity, **Spec–code parity** | ✅ PASS | [spec.md](spec.md), OpenAPI, [api-v1.md](contracts/api-v1.md), [data-model.md](data-model.md) updated in same delivery as code |

**OpenAPI codegen migration (required for implementation)**: Update `backend/mmx-adapter-in-rest/pom.xml` `openapi-generator-maven-plugin` **`inputSpec`** from `specs/001-mm-order-processing/contracts/openapi.yaml` to **`specs/002-trader-orders-views/contracts/openapi.yaml`**, regenerate, then implement any new interface methods on existing controllers (`OrderManagementController` implements generated `OrdersApi`). Intake controller continues to implement the same generated operations; verify no drift.

**Complexity tracking**: None — no constitutional violations.

## Project Structure (this feature vs code)

### Documentation

```text
specs/002-trader-orders-views/
├── spec.md
├── plan.md              ← this file
├── research.md
├── data-model.md
├── quickstart.md
└── contracts/
    ├── openapi.yaml     ← v1.1.0 canonical for codegen (after pom migration)
    └── api-v1.md
```

### Source (repository — unchanged top-level from 001)

```text
mmx/
├── backend/             ← mmx-domain, mmx-application, mmx-adapter-in-rest, …
├── frontend/src/app/    ← routes, features, core/api
└── specs/
```

**Structure decision**: Same modular layout as [001 plan](../001-mm-order-processing/plan.md); 002 adds **workspace-parent** Angular routes and **OrderRepository** query methods + use cases for workspace-scoped lists.

## User Story 1 — Implementation outline

### Backend (application + ports)

1. **`OrderRepository`** (`backend/mmx-application/.../OrderRepository.java`): add queries needed for filtered lists, e.g. combine `OrderStatus` + `OrderType` + assignee where required — at minimum methods to support:
   - ASSIGNED + `TERM` / `ON_CALL` scoped to current assignee (per [research.md](research.md) R-002)
   - EXECUTED + `TERM` / `ON_CALL`
2. **JPA** (`SpringDataOrderRepository`, `JpaOrderRepository`): implement the new finder signatures.
3. **Use cases / services**: extend `AssignmentService` or add a small dedicated query service so `OrderManagementController` delegates to typed list methods — **no** business rules in controllers.
4. **REST**: after codegen, implement `listAssignedTermOrders`, `listAssignedOnCallOrders`, `listExecutedTermOrders`, `listExecutedOnCallOrders` on `OrderManagementController` (paths must match [openapi.yaml](contracts/openapi.yaml)).

### Frontend

1. **Routing**: Introduce a parent **desk** or **workspace** route with children:
   - `:workspace` `oncall` | `term` (default redirect **`oncall`** from root — replaces current redirect to `term-orders`).
   - Child segments: `received` | `assigned` | `executed` calling the matching API base path for that workspace.
2. **Navigation**: Primary chrome shows **workspace** toggle + **sub-view** tabs; only one `OrderType` per surface.
3. **API client** (`frontend/src/app/core/api/order-api.service.ts`): add methods for new endpoints; stop using deprecated flat `/assigned` once migrated.
4. **Order details / deep links**: Preserve ability to open `orders/:id` from any workspace; details already expose `orderType` for UX consistency.

### Independent test criteria (from spec)

- New session → UI default **OnCall**; lists for Received / Assigned / Executed in that workspace show **only** OnCall rows.
- Switch to Term → only Term rows; switch back → no stale cross-type rows in the same session.
- New browser session → **OnCall** again (no restored Term).

See [quickstart.md](quickstart.md).

## Phase 0 & 1 (this `/speckit.plan` run)

| Phase | Output | Location |
|-------|--------|----------|
| 0 | Research decisions (API ownership, US1 vs US2 scope, default route) | [research.md](research.md) |
| 1 | Data model notes (no new persistence for US1) | [data-model.md](data-model.md) |
| 1 | OpenAPI v1.1.0 + prose mirror | [contracts/openapi.yaml](contracts/openapi.yaml), [contracts/api-v1.md](contracts/api-v1.md) |
| 1 | Manual validation steps | [quickstart.md](quickstart.md) |

## Out of scope (later stories / follow-up plans)

- **US2**: Desk-wide Assigned list ([FR-002](spec.md)) — will likely change assignee filter semantics; OpenAPI paths may stay if query or role semantics evolve (plan update then).
- **US3–US5**: Received window, accounting, executed-not-accounted — separate plan sections when scheduled.

## Dependencies & Execution Order

1. Land OpenAPI + pom `inputSpec` migration + regenerate.
2. Domain/repository/use case → REST → Angular routes and nav.
3. Run quickstart checks; update Cypress if base URL or nav labels change materially.

## Suggested next command

`/speckit.tasks` — optionally pass **User Story 1** in the argument line to mirror this scope in `tasks.md`.
