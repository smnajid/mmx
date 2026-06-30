# A routed order is two linked records, not one shared record

**Status:** accepted

When a TradingClient places an order that routes to its TradingHub for execution, MMX creates **two linked order records** — a **client-side order** (in the client's LegalEntity scope, keyed by the client PM's portfolioNumber and ExternalOrderReference) and a **hub-side order** (in the hub's LegalEntity scope, keyed by the client's global account at the hub and a hub-side reference) — correlated by a deterministic **routing id**. We rejected a single shared record handed off between entities, because each LegalEntity has a different accounting reality and identity, and the per-LegalEntity scope rule (a session sees only its own entity's data) forbids one record carrying both entities' fields. The two-record model mirrors front-to-back trade mirroring and keeps each record cleanly within its entity scope.

## Considered options

- **One record handed off between entities** — rejected: violates per-LegalEntity scope, forces one record to carry foreign portfolio/counterparty data, and breaks idempotency (the client PM's ExternalOrderReference cannot serve as the hub's intake key).
- **Two linked records (chosen).**

## Consequences

- Status must be synchronised: hub trader execution synchronously sets the client-side order `Executed` in the same transaction; terminal outcomes propagate; hub-internal `Assigned` does not.
- Two idempotency keys: the client PM's `ExternalOrderReference` (client-side) and the deterministic routing id (hub-side).
- `Accounted` is reached independently on each side via separate accounted callbacks.
