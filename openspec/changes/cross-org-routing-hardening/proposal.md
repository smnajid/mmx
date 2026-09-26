## Why

The `cross-org-routing-transport` change ships the eventually-consistent remote routing path (leg A REST + leg B Kafka) with **basic safety only**: a broken remote pair throws, rejects are unclassified, and the client deployment's accounting callback can only be addressed by `orderId` — which never crosses the organisation boundary. This change hardens that path to a production-ready failure model: it classifies rejections by origin so they are observable and reconcilable, quarantines poison messages so a broken pair cannot stall the leg-B consumer indefinitely, lets the client deployment's back office account a remote order by its cross-boundary key, and defines the operational SLOs that make "silence is never terminal" safe to operate.

## What Changes

- **Rejection origin is persisted.** A new `RejectionOrigin` value (`TRADER` | `ROUTING_FAILURE`) SHALL be recorded on every rejected order, alongside the existing free-text `rejectionReason`. A routing failure (unresolved account, grant/currency/tenor violation at intake or at remote accept) SHALL be `ROUTING_FAILURE`; a desk-trader reject SHALL be `TRADER`. This applies to **both** local and remote rejects. A Flyway migration adds a nullable `rejection_origin` column (additive; existing rows backfill to `null` and remain queryable by reason). Dashboards, reconciliation, and the "routing-failure reject is HTTP-only" rule now have a concrete, queryable field rather than a free-text heuristic.
- **Broken-pair poison messages go to a dead-letter topic.** `ApplyRemoteOrderOutcomeUseCase` (leg-B consumer) today throws `RoutedOrderPairIntegrityException` on a missing/mismatched client-side order — correct for safety, but under at-least-once Kafka a genuinely broken pair redelivers forever and stalls the consumer. A bounded retry-then-DLQ policy SHALL route an unrecoverable message to a dedicated dead-letter topic (`mmx.routed-order-outcome.{orgCode}.dlq`) after a configurable attempt count, emit an operational alert, and advance the consumer offset. The local hub-side throw+rollback (in-process, transactional) is **unchanged** — it still prevents a local hub terminal on a missing local client.
- **Accounted callback resolves a remote order by routingId.** No internal order UUID crosses the boundary, so the client deployment's back office only ever learns a remote execution via the leg-B outcome keyed by `(originatingLegalEntityCode, routingId)`. The `accounted` callback SHALL gain a routingId-addressed variant (or body correlation) so the client deployment's back office can transition a remote client-side order `EXECUTED → ACCOUNTED` using only the cross-boundary key. The existing `orderId`-addressed callback is unchanged for local orders.
- **Operational SLOs and alerts are defined.** Consumer-lag on the leg-B consumer, outbox-age on the hub leg-B outbox, broker health, and stale-`Routed` orders (a remote client-side order parked in `Routed` beyond an SLO because the hub never executed/cancelled/rejected) SHALL be measured and alerted. "Silence is never terminal" is only operable with these signals; without them a stuck `Received`/`Routed` is invisible.
- **ADR-0002 completion.** The atomic pair-integrity guarantee (throw+rollback inside one DB) is already relaxed to "detect + alert" across deployments by the transport change's basic-safety throw. This change formalises the "alert" half (DLQ + alert on detection) and records the completed relaxation in ADR-0002.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `money-market-order-lifecycle`: the reject transition carries a `RejectionOrigin` (`TRADER` | `ROUTING_FAILURE`); origin is set at every reject site and is queryable.
- `order-routing`: rejection origin classification (routing-failure vs trader) at all reject sites; broken-pair leg-B consumer retry-then-DLQ + alert; stale-`Routed` SLO + alert; remote accounted callback `routingId`-correlation.
- `back-office-outbound-messaging`: leg-B consumer dead-letter topic + retry-then-DLQ policy; outbox-age / consumer-lag / broker-health observability SLOs for the routed-order-outcome channel.
- `back-office-accounting-handoff`: the `accounted` callback resolves a remote client-side order by `(originatingLegalEntityCode, routingId)` in addition to the existing `orderId` path.

## Impact

- **Backend — hexagonal (TDD):**
  - `mmx-domain`: `RejectionOrigin` value object; `MoneyMarketOrder` reject transitions record origin; reject factory/mutation methods extended.
  - `mmx-application`: record `RejectionOrigin` at every reject site (intake routing failure, accept routing failure, trader reject, leg-B trader-reject apply); `ApplyRemoteOrderOutcomeUseCase` broken-pair retry-then-DLQ policy; accounted-by-routingId resolution use case.
  - `mmx-adapter-in-rest`: extend the `accounted` callback contract to accept routingId correlation for remote client-side orders (contract-first OpenAPI update + codegen).
  - `mmx-adapter-out-persistence`: Flyway migration adding nullable `rejection_origin` column; repository support for routingId-addressed order lookup.
  - `mmx-adapter-out-messaging`: leg-B consumer dead-letter producer; consumer-lag / outbox-age / broker-health metrics + alert hooks.
  - `mmx-bootstrap`: wire DLQ policy, metrics, and the routingId-addressed accounted path.
- **Contracts (BACKWARD-compatible):** the `accounted` callback OpenAPI gains a routingId-correlation variant (additive; existing `orderId` path unchanged); the dead-letter topic is new infrastructure (`mmx.routed-order-outcome.{orgCode}.dlq`).
- **TDD:** red-first JUnit 5 for rejection-origin recording at each reject site, broken-pair retry-then-DLQ, routingId-addressed accounting, and stale-`Routed` detection; Testcontainers integration for the leg-B DLQ path.
- **ADRs:** ADR-0002 update completing the "detect + alert" relaxation across deployments.
- **Depends on:** `cross-org-routing-transport` (this change hardens the path that change introduces). **Coexistence:** local routing's synchronous/atomic guarantees are unchanged; the local throw+rollback on a missing local client is preserved.
