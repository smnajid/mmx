# A routed order is two linked records, not one shared record

**Status:** accepted

When a TradingClient places an order that routes to its TradingHub for execution, MMX creates **two linked order records** — a **client-side order** (in the client's LegalEntity scope, keyed by the client PM's portfolioNumber and ExternalOrderReference) and a **hub-side order** (in the hub's LegalEntity scope, keyed by the client's global account at the hub and a hub-side reference) — correlated by a deterministic **routing id**. We rejected a single shared record handed off between entities, because each LegalEntity has a different accounting reality and identity, and the per-LegalEntity scope rule (a session sees only its own entity's data) forbids one record carrying both entities' fields. The two-record model mirrors front-to-back trade mirroring and keeps each record cleanly within its entity scope.

## Cross-org relaxation (ADR 0006 / 0007)

In **same-Organisation** routing (local), the two records are created atomically in the intake transaction — pair-integrity is **prevented** by throwing + rolling back if either write fails. The client-side order never exists without its hub-side counterpart.

In **cross-Organisation** routing (Leg A / Leg B), the two records live in **separate databases** across deployments. Atomic cross-database transactions are not possible. Pair-integrity is therefore **relaxed** from "prevent" to "detect + alert":

- The client-side order may exist in `Received` without a hub-side order (Leg A in flight or hub unreachable). This is expected — **silence is never terminal** (ADR 0006).
- The hub-side order is created in the hub's `AcceptRoutedHubOrderUseCase` transaction, which also commits a Leg B `ACCEPTED` outbox row. If the HTTP response is lost (network failure after commit), the hub-side order exists but the client does not yet know — the Leg B Kafka event eventually reconciles this.
- A **routing-outcome relay** (`RoutingOutcomeRelay`) drains the outbox and publishes Leg B events. A stuck outbox row triggers an ops alert — the pair is eventually consistent, not atomically guaranteed.
- The hub dedupes on `(originatingLegalEntityCode, routingId)` so a Leg A retry after a network blip resolves to the same hub-side order — no duplicate.

This relaxation does not change the two-record model — it changes only the consistency guarantee from atomic to eventual. The routing id remains the sole correlation key across deployments.

## Considered options

- **One record handed off between entities** — rejected: violates per-LegalEntity scope, forces one record to carry foreign portfolio/counterparty data, and breaks idempotency (the client PM's ExternalOrderReference cannot serve as the hub's intake key).
- **Two linked records (chosen).**

## Consequences

- Status must be synchronised: in local routing, hub trader execution synchronously sets the client-side order `Executed` in the same transaction; in cross-org routing, terminal outcomes propagate asynchronously via Leg B Kafka events (ADR 0006). Terminal outcomes propagate; hub-internal `Assigned` does not.
- Two idempotency keys: the client PM's `ExternalOrderReference` (client-side) and the deterministic routing id (hub-side). In cross-org routing, the hub additionally keys on `(originatingLegalEntityCode, routingId)` to reject cross-deployment replays.
- `Accounted` is reached independently on each side via separate accounted callbacks.
- In cross-org routing, pair-integrity is eventual (detect + alert), not atomic (prevent). The outbox relay is the consistency mechanism; a stuck outbox row is the alert signal.

