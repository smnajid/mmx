## Context

- **Formal product spec**: Feature 002 User Story 3, FR-004, FR-005, Assumptions (default window semantics) in `specs/002-trader-orders-views/spec.md`.
- **Current code**: `OrderQueryService.listReceivedTermOrders` / `listReceivedOnCallOrders` load all `RECEIVED` ∪ type then paginate in memory — no horizon filter (`backend/mmx-application/src/main/java/com/mmx/order/application/service/OrderQueryService.java`).
- **Scheduling field**: Use existing persisted **`valueDate`** on `MoneyMarketOrder` as the “scheduling date” for the window (aligned with FR intake rules and list DTO `valueDate`).

## Goals / Non-Goals

**Goals:**

- Server-enforced filtering so pagination and counts match the visible cohort (default vs all).
- Contract-first query shape on existing Received paths; regenerate Java API interfaces.
- SPA toggle bound to session-only state (in-memory service or component tree), passed on each list request.

**Non-Goals:**

- Persisting show-all as a user preference across sessions.
- Changing Assigned/Executed behaviour.
- Introducing ACCOUNITED / executed-not-accounted (later stories).

## Decisions

| Topic | Decision | Rationale |
|-------|-----------|-----------|
| API shape | Add optional query parameter on both Received GETs (e.g. `view` = `near_term` \| `all`, default `near_term`) | Keeps single resource per workspace; codegen-friendly enum or string enum in OpenAPI. |
| Clock / zone | Inject `Clock`; compute “today” in **`Europe/Paris`** (or reuse existing zone constant if already defined for valueDate validation) | Spec calls for single business TZ; spike: grep `ZoneId` / validation in intake. |
| Repository | Prefer DB-level predicate on `valueDate` + `status` + `order_type` over in-memory pagination | Correct performance once data grows; may replace current `findByStatusAndOrderType` usage for Received with a dedicated finder. |
| Edge cases | Midnight / DST handled by **`LocalDate` (business TZ)** comparisons, not UTC instant truncation | Matches “calendar day” wording in spec assumptions. |

## Risks / Trade-offs

- **Breaking clients**: Default parameter value must preserve **narrow** view; omitting param == near term.
- **In-memory pagination today**: Removing it for Received avoids loading full tables — may require reordering totals if moving to Spring Data Page.
