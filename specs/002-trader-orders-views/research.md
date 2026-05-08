# Research: Trader order views (002)

**Feature**: [spec.md](spec.md) · **Scope for this document**: decisions that unblock **User Story 1** (Term vs OnCall workspace structure).

## R-001 — Canonical OpenAPI location

**Decision**: Treat `specs/002-trader-orders-views/contracts/openapi.yaml` as the **single** OpenAPI document for codegen after migration (v1.1.0: baseline 001 operations + workspace list paths + deprecation of flat `/assigned`).

**Rationale**: Constitution Principle I requires contract-first REST versioned with the feature; 002 owns the trader-surface evolution.

**Alternatives considered**: (a) Patch only `001/.../openapi.yaml` — rejects feature-scoped versioning; (b) Maintain two OpenAPI files and merge at build — unnecessary complexity for this monorepo.

## R-002 — Assigned list semantics for US1 vs US2

**Decision**: For **US1**, `GET .../term/assigned` and `GET .../oncall/assigned` return **the same assignee scope as today** (`X-Trader-Id`): ASSIGNED orders **for that trader**, further filtered by `OrderType`. Desk-wide Assigned visibility is **User Story 2** ([FR-002](spec.md)); this plan does not implement FR-002.

**Rationale**: Avoid prematurely implementing P2 visibility rules while still satisfying FR-001 / US1 (“no mixed types on the same workspace surface”).

**Alternatives considered**: Implement desk-wide lists in US1 — would duplicate US2 acceptance work and blur story boundaries.

## R-003 — Executed workspace list for US1

**Decision**: Expose `GET /api/v1/orders/{term|oncall}/executed` returning orders in domain status **EXECUTED** for that `OrderType`. Narrowing to “executed not yet accounted” and handoff states is deferred to **User Story 4**; US1 only requires the **shell** of the third sub-view per workspace.

**Rationale**: Domain already has `EXECUTED`; US1 needs three labels (Received / Assigned / Executed) without blocking on accounting model work.

## R-004 — Default workspace (OnCall) without persistence

**Decision**: Implement **default active workspace = OnCall** via **client routing** only (e.g. default child route under `/desk/oncall/...` or equivalent). **No** REST preference API and **no** browser `localStorage` requirement beyond ordinary Angular session; a full page reload returns to the default route mapping.

**Rationale**: Matches [Clarifications](spec.md#clarifications) Session 2026-05-09 (Option D).

## R-005 — Angular routing shape

**Decision**: Use a **parent layout** route for the desk with two workspace segments (`term` | `oncall`) and three child routes (`received` | `assigned` | `executed`), each lazy-loading or reusing list/detail components bound to the correct API path for that workspace + sub-view.

**Rationale**: Mirrors the mental model in US1 and keeps URLs bookmarkable per workspace.

**Alternatives considered**: Flat legacy URLs (`/term-orders`, `/assigned-orders`) with global nav only — fails to nest Assigned/Executed under each workspace cleanly.
