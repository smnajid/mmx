# Data model: Trader order views (002)

**Normative domain model** remains the **Money Market Order** aggregate and related types from **001** ([specs/001-mm-order-processing/data-model.md](../001-mm-order-processing/data-model.md)). This feature **does not** introduce new persisted tables for US1.

## Discriminator: workspace vs `OrderType`

| UX term   | Maps to domain |
|----------|----------------|
| **Term workspace** | `OrderType.TERM` |
| **OnCall workspace** | `OrderType.ON_CALL` |

Every list row is still a `MoneyMarketOrder`; workspace routing selects which **API operation** and **filter** apply so that only one `OrderType` appears on a given workspace surface.

## List cohorts by sub-view (US1)

| Sub-view   | Domain filter |
|-----------|----------------|
| Received  | `OrderStatus.RECEIVED` ∧ `OrderType` matches workspace |
| Assigned  | `OrderStatus.ASSIGNED` ∧ `OrderType` matches workspace — **desk-wide** (all assignees) per User Story 2 / [research.md](research.md) R-006 |
| Executed  | `OrderStatus.EXECUTED` ∧ `OrderType` matches workspace (shell until US4 narrows cohort) |

## Session / navigation (non-persisted)

| Concept | Storage |
|--------|---------|
| Active workspace (Term vs OnCall) | **UI routing state only** — default **OnCall** for new sessions ([spec.md](spec.md) FR-001, Clarifications) |
| Workspace preference across logins | **None** |

## Future concepts (not US1)

- **Accounted** vs execution-only — User Stories 4–5; may justify new fields or projections when introduced.
- **Handoff / transmit state** — US4; optional read models or flags beyond raw `OrderStatus`.
