# Tasks: Trader order views and accounting handoff (User Story 1 scope)

**Input**: Design documents from `/specs/002-trader-orders-views/`  
**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [contracts/openapi.yaml](contracts/openapi.yaml), [research.md](research.md), [data-model.md](data-model.md)

**Scope**: This file was generated for **`/speckit-tasks US1`** — **User Story 1** (P1) only: Term vs OnCall workspace surfaces, default **OnCall**, workspace-scoped lists, no cross-type mixing. **User Stories 2–5** are listed under *Deferred* for a follow-up `/speckit.tasks` pass.

**Tests**: Not mandated as separate rows by [spec.md](spec.md); integration and service tests are included where they lock acceptance ([plan.md](plan.md) Constitution Check).

**Format**: `[ID] [P?] [Story] Description with file path`

---

## Phase 1: Setup (contract codegen)

**Purpose**: Point OpenAPI codegen at the v1.1.0 contract and regenerate server interfaces ([plan.md](plan.md)).

- [x] T001 Set `openapi-generator-maven-plugin` `inputSpec` to `specs/002-trader-orders-views/contracts/openapi.yaml` in `backend/mmx-adapter-in-rest/pom.xml` (replace `001-mm-order-processing` path).

- [x] T002 From `backend/`, run `mvn -pl mmx-adapter-in-rest -am compile` and fix any compile errors until generated `OrdersApi` / `IntakeApi` (under `target/generated-sources`) match controller implementations in `backend/mmx-adapter-in-rest/src/main/java/com/mmx/order/adapter/in/rest/`.

---

## Phase 2: Foundational (blocking — persistence port)

**Purpose**: Repository support for **Assigned ∧ OrderType** (assignee-scoped per [research.md](research.md) R-002). **Executed ∧ OrderType** can use existing `findByStatusAndOrderType` once `EXECUTED` lists are exposed.

**Checkpoint**: `OrderRepository` + JPA can answer workspace-scoped Assigned queries.

- [x] T003 Add `List<MoneyMarketOrder> findByAssignedTraderIdAndStatusAndOrderType(TraderId assignedTraderId, OrderStatus status, OrderType orderType)` to `backend/mmx-application/src/main/java/com/mmx/order/application/port/out/OrderRepository.java`.

- [x] T004 Add Spring Data finder `findByAssignedTraderIdAndStatusAndOrderType(String assignedTraderId, String status, String orderType)` to `backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/repository/SpringDataOrderRepository.java`.

- [x] T005 Implement the new method in `backend/mmx-adapter-out-persistence/src/main/java/com/mmx/order/adapter/out/persistence/JpaOrderRepository.java` (map enums to persisted strings like existing methods).

---

## Phase 3: User Story 1 — Term vs OnCall workspace (Priority: P1)

**Goal**: Two workspaces (**Term** / **OnCall**), each with **Received**, **Assigned**, and **Executed** sub-views; lists never mix `OrderType`; new session defaults to **OnCall** ([spec.md](spec.md) FR-001, Clarifications).

**Independent test**: [spec.md](spec.md) User Story 1 — default OnCall; switch workspace; no cross-type rows; new session resets to OnCall.

### Backend — application layer

- [x] T006 [US1] Add workspace-scoped assigned listing to `backend/mmx-application/src/main/java/com/mmx/order/application/service/AssignmentService.java` (delegate to `OrderRepository.findByAssignedTraderIdAndStatusAndOrderType` with `OrderStatus.ASSIGNED` and `OrderType.TERM` / `ON_CALL`; reuse pagination helper).

- [x] T007 [P] [US1] Add `listExecutedTermOrders` / `listExecutedOnCallOrders` to `backend/mmx-application/src/main/java/com/mmx/order/application/service/OrderQueryService.java` using existing `orderRepository.findByStatusAndOrderType(OrderStatus.EXECUTED, OrderType.TERM|ON_CALL)` and shared pagination.

- [x] T008 [US1] Add port interfaces if required by codegen (e.g. new use case types under `backend/mmx-application/src/main/java/com/mmx/order/application/port/in/`) and register beans in `backend/mmx-bootstrap/src/main/java/com/mmx/order/config/OrderModuleConfiguration.java` only if new services are split from existing ones.

### Backend — REST adapter

- [x] T009 [US1] Implement generated operations `listAssignedTermOrders`, `listAssignedOnCallOrders`, `listExecutedTermOrders`, `listExecutedOnCallOrders` on `backend/mmx-adapter-in-rest/src/main/java/com/mmx/order/adapter/in/rest/OrderManagementController.java`, delegating to `AssignmentService` / `OrderQueryService` and `OrderRestMapper` (same response shape as other list endpoints).

### Backend — automated tests

- [x] T010 [P] [US1] Extend `backend/mmx-application/src/test/java/com/mmx/order/application/service/AssignmentServiceTest.java` for Term vs OnCall assigned lists (no mixed types in page).

- [x] T011 [P] [US1] Extend or add tests under `backend/mmx-application/src/test/java/com/mmx/order/application/service/` for executed workspace lists via `OrderQueryService`.

- [x] T012 [US1] Extend `backend/mmx-adapter-in-rest/src/test/java/com/mmx/order/adapter/in/rest/` REST tests for the four new GET paths (or extend existing controller IT class) asserting `orderType` in each response page.

- [x] T013 [US1] Extend `backend/mmx-bootstrap/src/test/java/com/mmx/order/rest/OrderRestApiIntegrationTest.java` (or `OrderRestApiIntegrationTest.java` path per repo) for end-to-end JSON assertions on `/api/v1/orders/term/assigned`, `/oncall/assigned`, `/term/executed`, `/oncall/executed`.

### Frontend — routing and API

- [x] T014 [US1] Restructure `frontend/src/app/app.routes.ts`: parent workspace segment (`oncall` | `term`) with child routes `received`, `assigned`, `executed`; default redirect to **OnCall** `received` (replace root redirect from `term-orders`).

- [x] T015 [US1] Update `frontend/src/app/app.html` primary navigation: workspace selector (Term / OnCall) plus sub-links for Received / Assigned / Executed consistent with [spec.md](spec.md) FR-001.

- [x] T016 [US1] Add methods for `GET .../term/assigned`, `.../oncall/assigned`, `.../term/executed`, `.../oncall/executed` in `frontend/src/app/core/api/order-api.service.ts`; migrate callers off deprecated `GET .../assigned`.

- [x] T017 [US1] Refactor feature modules under `frontend/src/app/features/term-orders/`, `frontend/src/app/features/oncall-orders/`, `frontend/src/app/features/assigned-orders/` (and add executed list components as needed) so each sub-view loads only the matching workspace type.

- [x] T018 [US1] Ensure order deep link `frontend/src/app/features/order-details/order-details.component.ts` remains reachable from all workspace routes (e.g. `orders/:id` unchanged).

### Frontend — e2e

- [x] T019 [US1] Update `frontend/cypress/e2e/trader-workflow.cy.ts` (and any path helpers) so the first screen is **OnCall** workspace and navigation covers Term ↔ OnCall without mixed-type lists.

---

## Phase 4: Polish & US1 validation

**Purpose**: Cross-cutting checks for this increment only.

- [x] T020 Run manual checks in `specs/002-trader-orders-views/quickstart.md` and align behavior if gaps are found (update [spec.md](spec.md) / contracts only if material).

- [x] T021 [P] Confirm spec–code parity: [contracts/openapi.yaml](contracts/openapi.yaml), [contracts/api-v1.md](contracts/api-v1.md), and runtime paths match ([AGENTS.md](../../../AGENTS.md) SDD gate).

---

## Deferred (not in this tasks file — future `/speckit.tasks`)

| Story | Topic |
|-------|--------|
| US2 | Desk-wide Assigned visibility ([FR-002](spec.md)) |
| US3 | Received near-term window + session “show all” |
| US4–US5 | Executed-not-accounted, accounting confirmation, handoff |

---

## Dependencies & execution order

### Phase dependencies

- **Phase 1** → **Phase 2** → **Phase 3** → **Phase 4** sequentially.
- **Phase 3 backend**: T003–T005 before T006–T009; T006–T009 before T010–T013.
- **Phase 3 frontend**: T014–T016 before T017–T019 (routes and API before wiring lists).

### User story dependency

- **US1** depends on Phases 1–2 only; no dependency on US2–US5.

### Parallel opportunities

- After T005: **T006** and **T007** can proceed in parallel (different services).
- **T010** and **T011** in parallel after application methods exist.
- **T020** and **T021** in parallel at end.

---

## Parallel example: User Story 1 (backend)

```bash
# After T005:
# Developer A: T006 AssignmentService workspace assigned lists
# Developer B: T007 OrderQueryService executed workspace lists

# After T009:
# Developer A: T010 AssignmentService tests
# Developer B: T011 OrderQueryService executed tests
```

---

## Implementation strategy (MVP = US1)

1. Complete Phase 1–2 (codegen + repository).
2. Complete Phase 3 backend, then Phase 3 frontend.
3. **Stop and validate** against User Story 1 acceptance scenarios in [spec.md](spec.md).
4. Run `/speckit.tasks` again (without US1-only filter) when starting US2.

---

## Extension hooks

**Optional pre-hook**: `/speckit.git.commit` — commit before task generation.

**Optional post-hook**: `/speckit.git.commit` — commit after `tasks.md` generation.

---

## Task summary

| Metric | Value |
|--------|------:|
| Total tasks | 21 |
| US1 tasks (Phase 3–4) | 14 |
| Parallel-eligible | T007, T010, T011, T021 |

**Suggested MVP scope**: Complete through **T019** (US1 e2e), then **T020–T021**.
