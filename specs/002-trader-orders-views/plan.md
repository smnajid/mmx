# Implementation Plan: Trader order views and accounting handoff (User Stories 1–2)

**Branch**: `002-trader-orders-views` | **Date**: 2026-05-09 | **Spec**: [spec.md](spec.md)  
**Input**: [spec.md](spec.md)

## Summary

**User Story 1 (done)**: **Desk layout** — **Term** and **OnCall** workspaces, each with **Received**, **Assigned**, **Executed**; no mixed `OrderType` on a surface; default **OnCall**; workspace-scoped REST paths in [contracts/openapi.yaml](contracts/openapi.yaml).

**User Story 2 (this increment)**: **Desk-wide Assigned** — `GET /api/v1/orders/{term|oncall}/assigned` returns **all** orders in **ASSIGNED** status for that workspace type for **every** entitled trader ([FR-002](spec.md)). `X-Trader-Id` remains required on the contract for actor identity consistency; list results **do not** filter by assignee. Execution and unassign remain **assignee-only** (existing domain rules). SPA Assigned copy and **Unassign** affordance MUST align (Unassign only for rows assigned to the current trader).

**SPA — order details & navigation**: List rows link to `/orders/:id` with optional query parameters `ws` and `queue` (`term|oncall` × `received|assigned|executed`) so the shell **workspace + sub-nav** stay highlighted on the details screen and **Back to …** returns to `/{ws}/{queue}`. Without those parameters (cold deep link), highlights may be absent until the user picks a queue; back falls back to **OnCall** > **Received**.

**Still out of scope**: Received horizon / show-all (US3), accounting handoff cohort (US4–US5).

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

## User Story 2 — Implementation outline

### Backend

- **`AssignmentService.listAssignedTermOrders` / `listAssignedOnCallOrders`**: delegate to `OrderRepository.findByStatusAndOrderType(OrderStatus.ASSIGNED, OrderType.TERM|ON_CALL)` — **no** assignee filter ([research.md](research.md) R-006).
- **REST**: Same paths; update OpenAPI prose (`summary` / `description`) and regenerate if interface signatures unchanged.

### Frontend

- Assigned queue **copy**: desk-wide visibility, not “assigned to you” only.
- **Unassign**: show only when `orderSummary.assignedTraderId` matches current `X-Trader-Id` context (avoid misleading failures for peers).

### Tests

- Application test: workspace assigned lists use status+type query, not assignee+type.
- Integration: two distinct trader headers receive the **same** assigned row for a workspace after one assigns.

## Out of scope (later stories / follow-up plans)

- **US3–US5**: Received window, accounting, executed-not-accounted — separate plan sections when scheduled.

## Dependencies & Execution Order

1. Land OpenAPI + pom `inputSpec` migration + regenerate.
2. Domain/repository/use case → REST → Angular routes and nav.
3. Run quickstart checks; update Cypress if base URL or nav labels change materially.

## Suggested next command

`/speckit.tasks` — optionally pass **User Story 1** in the argument line to mirror this scope in `tasks.md`.
