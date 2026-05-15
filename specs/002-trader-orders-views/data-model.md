# Data model: Trader order views (002)

**Normative domain model** remains the **Money Market Order** aggregate and related types from **001** ([specs/001-mm-order-processing/data-model.md](../001-mm-order-processing/data-model.md)). **Persisted extensions for async handoff**: column **`handoff_status`** on `money_market_order` (`PENDING` \| `PUBLISHED` \| `FAILED`, nullable — meaningful when `status = EXECUTED`), and table **`back_office_outbox`** (transactional outbox payload + relay state keyed by `order_id`). Wire formats: REST summaries use OpenAPI `handoffStatus`; Kafka uses [contracts/asyncapi.yaml](contracts/asyncapi.yaml) `OrderExecutedV1`.

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

## Handoff / transmit state (EXECUTED rows)

| Persisted / API | Role |
|-----------------|------|
| `money_market_order.handoff_status` | Trader-visible integration state on **`EXECUTED`** orders; distinct from lifecycle `OrderStatus`. |
| `back_office_outbox` | Same-transaction intent + frozen JSON payload for Kafka relay. |

OpenAPI `OrderSummaryResponse.handoffStatus` mirrors `handoff_status` on executed-list endpoints only (omit-null when not applicable).
