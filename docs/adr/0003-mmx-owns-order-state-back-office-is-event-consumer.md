# MMX owns order state; the Deposits back office is an event consumer + callback caller

**Status:** accepted

MMX owns the order write model, order routing, the `GlobalAccountDirectory`, and the `Executed` / `Accounted` transitions. The legacy back-office application ("Deposits") integrates with MMX **only** by consuming `OrderExecutedV1` events (outbox/Kafka) and calling the `accounted` callback — it **never reads or writes the MMX order DB**. This replaces the legacy FiduTrader pattern where the Deposits app polled the order DB via Quartz and cross-wrote order rows/statuses between entity databases. We chose this so the order bounded context stays inside MMX and the back office's contract with MMX is events + callbacks rather than shared database access.

## Considered options

- **Keep the back office polling/writing the order DB** (legacy FiduTrader pattern) — rejected: tight coupling, no bounded context, MMX cannot evolve its schema independently, and it contradicts the hexagonal/outbox architecture MMX already has.
- **Back office as event consumer + callback caller, no order DB access (chosen).**

## Consequences

- For routed orders, the single hub-side `OrderExecutedV1` carries a routing-context block (`routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`) so the back office can build both the hub and client contracts without reading any MMX routing table.
- Contract reversal/replace are back-office-internal, correlated by `routingId` captured at contract creation; MMX order status is not affected and `Accounted` stays terminal.
- The client-side order's `Executed` transition is MMX-internal (PM-facing status only) and does not emit its own back-office event — a scoped exception to "one outbox row per EXECUTED order."
- Legacy "executed" (back-office contract validated) maps to MMX `Accounted`; MMX `Executed` is the trader-dealt moment the legacy client side conflated.
