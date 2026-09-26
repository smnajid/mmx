# Silence is never terminal: remote routing gateway owns the ops signal

**Status:** accepted

In cross-Organisation routing (Leg A / Leg B), the client deployment sends the routing request to the hub deployment over HTTP (Leg A) and receives terminal outcomes asynchronously over Kafka (Leg B). Transport unreachability — timeouts, 5xx errors, network partitions, hub-deployment downtime — is an expected operational reality. We decided that **silence (no response / no event) never produces a terminal status** on the client-side order. Only an explicit hub-side outcome (accept, reject, execute, cancel) — or a synchronous routing-failure reject returned in Leg A — can transition the client-side order out of `Received`.

The `ResilientRemoteRoutingGateway` wraps the REST adapter with retry, exponential backoff, and a circuit-breaker. When the circuit is open or all retries are exhausted, it throws `RemoteRoutingCircuitOpenException` or `RemoteRoutingTransientFailureException`. The intake use case catches these, leaves the client-side order in `Received`, and returns — the order is not `Rejected`. The gateway's operational signal (circuit-open alert) is the ops team's call to action, not a domain-level rejection. This mirrors the Leg B contract: a missing Kafka event is never interpreted as a negative outcome — the client-side order simply waits.

## Considered options

- **Auto-reject on transport failure** — rejected: a transient network blip would permanently reject a valid order, with no way to undo it. The client PM would see a false `Rejected` and have to re-submit, losing the original `ExternalOrderReference` idempotency key and creating operational confusion.
- **Auto-retry indefinitely with no terminal escape** — rejected: while silence is not terminal, an order stuck in `Received` forever with no ops visibility is also unacceptable. The circuit-breaker provides the escape valve via an operational signal, without forcing a domain-level terminal status.
- **Silence is never terminal; gateway owns the ops signal (chosen).** The order stays `Received`; the gateway's retry/circuit-breaker is the operational response; an explicit hub outcome eventually arrives and transitions the order.

## Consequences

- The client-side order can remain in `Received` for an unbounded duration during a hub outage. This is by design — `Received` is the correct wait-state for "outcome not yet known."
- The `ResilientRemoteRoutingGateway` MUST emit an operational signal (circuit-open) so the ops team can intervene. The signal is not a domain event — it does not affect order status.
- Leg B consumers must be idempotent: a delayed or duplicated Kafka event must not double-apply an outcome. The `ApplyRemoteOrderOutcomeUseCase` guards against this by checking the current status before transitioning.
- This ADR relaxes the V1 same-Organisation guarantee where `Received → Routed` was atomic in the intake transaction. In cross-org routing, `Received → Routed` depends on a successful Leg A response, and `Received` is an observable wait-state whose duration is bounded by the transport SLA, not the database transaction.
