## Context

The `cross-org-routing-transport` change introduces the eventually-consistent remote routing path: leg A (sync REST) gives a bounded-time accept/reject; leg B (async Kafka via the transactional outbox) mirrors hub outcomes back to the client-side order. That change ships with **basic safety** only — it throws `RoutedOrderPairIntegrityException` on a broken remote pair, records no rejection classification, and leaves the client deployment's accounting callback addressable only by `orderId` (which never crosses the organisation boundary).

This hardening change closes the four operational gaps the transport change deliberately deferred (its Non-Goals): (1) rejections are unclassified, (2) a broken remote pair is a poison message that stalls the leg-B consumer indefinitely, (3) the client deployment's back office cannot account a remote order by its cross-boundary key, and (4) "silence is never terminal" has no operational signals. The synchronous/atomic local routing path is **untouched** throughout — its in-process throw+rollback on a missing local client is preserved.

## Goals / Non-Goals

**Goals:**

- Persist a `RejectionOrigin` (`TRADER` | `ROUTING_FAILURE`) on every rejected order, for both local and remote rejects, so the two reject classes the transport change established are queryable and reconcilable.
- Quarantine unrecoverable leg-B messages in a dead-letter topic after bounded retries, so a broken remote pair alerts instead of stalling the consumer.
- Let the client deployment's back office account a remote client-side order by `(originatingLegalEntityCode, routingId)`, the only key that crosses the boundary.
- Define the operational SLOs (consumer-lag, outbox-age, broker-health, stale-`Routed`, DLQ depth) that make a silent `Received`/`Routed` visible to operations.
- Complete ADR-0002's "detect + alert" relaxation across deployments.

**Non-Goals:**

- Generalising beyond the single CGD→LOC case (V2).
- Auto-recovery / auto-replay of DLQ messages (manual reconciliation runbook — the DLQ is a quarantine, not a self-healing pipeline).
- Reversal/replace of accounted orders (back-office-internal; `ACCOUNTED` stays terminal).
- The Deposits back-office cross-org broadcast channel (Deposits programme).
- Booking-time account-failure runbook (ops fog — a bad CGED mapping surfaces post-`Executed` at Transactions 2; manual unwind).
- Specific metrics tooling brand (Prometheus/Grafana/Alertmanager are infra/platform choices; this change fixes *what* must be measured and alerted, not the brand).

## Decisions

### D1: Rejection origin — a two-valued classification mirroring the transport boundary

A new `RejectionOrigin` value (`TRADER` | `ROUTING_FAILURE`) is recorded on `MoneyMarketOrder` alongside the existing free-text `rejectionReason`. The two values mirror exactly the two reject classes the transport change established:

- **`ROUTING_FAILURE`** — the route itself failed; no hub-side order lifecycle exists or the route never completed. Set at: local intake routing failure (unresolved `GlobalAccountDirectory` account, delegated-grant/enabled-set violation); remote client-side routing failure (unresolved `ExternalIdentityGateway` account → CGED-side reject, no round-trip); remote accept routing failure at LODH (`AcceptRoutedHubOrderUseCase` grant/currency/tenor violation → HTTP reject closes the client-side order).
- **`TRADER`** — a desk decision rejected an existing order. Set at: trader `reject(...)` on a desk order; `propagateRejectFromHub` (a hub-trader reject propagated to the client-side order, synchronously for local pairs, via leg-B `REJECTED` for remote pairs).

**Rationale:** exactly two values, no more. `ROUTING_FAILURE` means "no hub-side order to mirror / the route failed"; `TRADER` means "a desk rejected an existing order". This is the precise boundary the transport change drew (routing-failure reject is HTTP-only; trader-reject rides leg-B). A `SYSTEM`/`UNKNOWN` bucket is deliberately omitted — every reject site knows which kind it is; an unclassified reject is a bug, not a category.

**Persistence:** a nullable `rejection_origin` column added by Flyway. Existing rejected rows backfill to `null` (they remain queryable by `rejectionReason`); every **new** reject sets the origin. Nullable — not a NOT NULL with a default — because a NOT NULL + `TRADER` default would silently misclassify every historical routing failure as a trader reject on backfill. `null` reads honestly as "pre-origin-tracking".

**Adapter placement:** `RejectionOrigin` enum + aggregate mutation in `mmx-domain`; the application records the correct origin at each call site (`mmx-application`); Flyway migration + JPA mapping in `mmx-adapter-out-persistence`. Tests: unit-test each reject site sets the right origin (`mmx-domain` / `mmx-application`).

### D2: Broken-pair retry-then-DLQ — deterministic poison quarantine, not transient suppression

`ApplyRemoteOrderOutcomeUseCase` (leg-B consumer) throws `RoutedOrderPairIntegrityException` on (a) a missing client-side order for `(originatingLegalEntityCode=self, routingId)` or (b) a mismatched terminal (genuine divergence). Under at-le-once Kafka this redelivers forever and stalls the consumer. The hardening:

- **DLQ only for the deterministic integrity violation.** The retry-then-DLQ policy targets `RoutedOrderPairIntegrityException` specifically — a missing client or mismatched terminal is deterministic (retrying will not make a missing order appear; the client-side order lives in the consumer deployment's own DB, written by its own intake). Transient failures (DB down, serialisation glitch) are **not** DLQ-eligible — they retry via the consumer's normal backoff and never reach the DLQ. This separation is critical: DLQ-ing a transient outage would silently drop live traffic.
- **Bounded retries then DLQ.** After `mmx.cross-org.outcome.max-attempts` (default 3) attempts on the same offset all throw `RoutedOrderPairIntegrityException`, the consumer publishes the original message to `mmx.routed-order-outcome.{orgCode}.dlq`, emits an operational alert, and commits the offset (advances past the poison). A small retry count (not 1) is cheap insurance against subtle in-DB read-visibility edge cases.
- **DLQ ownership = consumer deployment.** The DLQ is consumer-side (CGED produces to it; CGED owns it). Named `mmx.routed-order-outcome.{orgCode}.dlq` — the `.dlq` suffix on the source topic name. LODH is unaware of it. The DLQ carries the original message + a diagnostic envelope (failure reason, failing offset, attempt count, detected-at timestamp) so an operator can reconcile manually.
- **Local throw+rollback is unchanged.** The local `RoutedOrderOutcomePropagation` throw inside the hub execute transaction still rolls back the hub terminal. There is no Kafka consumer on that path (it is in-process and transactional), so no poison-message concern. This change does not touch the local throw.

**Rationale:** DLQ-on-deterministic-cause is the standard poison-message quarantine pattern; DLQ-on-any-exception is the classic silent-data-loss trap. `RoutedOrderPairIntegrityException` is the one cause that is provably non-transient in this topology.

**Adapter placement:** the retry/DLQ policy in `mmx-application` (or a thin consumer-side wrapper in `mmx-adapter-out-messaging` calling `ApplyRemoteOrderOutcomeUseCase`); the DLQ Kafka producer in `mmx-adapter-out-messaging`; the alert hook emits a metric/log consumable by ops. Tests: unit-test the policy (N integrity throws → DLQ + alert; transient throw → no DLQ); Testcontainers integration for the end-to-end poison → DLQ path.

### D3: Accounted-by-routingId — a separate operation, not an overloaded path

The existing callback `POST /api/v1/back-office/orders/{orderId}/accounted` is addressed by the mmx `orderId` (UUID). For a remote client-side order, no UUID crosses the boundary — the client deployment's back office learns of the execution only via the leg-B outcome keyed by `(originatingLegalEntityCode, routingId)`. So the back office cannot address the callback by `orderId`.

**Decision: a separate, additive operation addressed by the cross-boundary key.** `POST /api/v1/back-office/orders/by-routing-key/{originatingLegalEntityCode}/{routingId}/accounted`, defined contract-first in `contracts/002-trader-orders-views/openapi.yaml` (+ `api-v1.md` mirror), codegen into `mmx-adapter-in-rest`. It resolves the client-side order by `(originatingLegalEntityCode=self, routingId)` — the same lookup `ApplyRemoteOrderOutcomeUseCase` already owns — then delegates to the **same** `EXECUTED → ACCOUNTED` transition and idempotency semantics as the orderId path.

**Rejected alternatives:**
- *Overload `{orderId}` to accept a routingId* — ambiguous, breaks the UUID contract, mismatches the generated interface type.
- *Body field `{routingId}` on the existing path* — the path still mandates `{orderId}`, so the caller must supply a value they do not have; awkward and error-prone.
- *A query correlator on the existing path* — conflates two correlation dimensions on one operation; harder to govern and test.

**Rationale:** a separate operation keeps each correlation dimension on its own clean, generated, contract-first surface. It is additive (BACKWARD-compatible) and shares the transition/idempotency core, so there is one behaviour with two addresses. The orderId path remains canonical for local orders.

**Adapter placement:** OpenAPI + codegen in `mmx-adapter-in-rest`; the controller resolves by routing key then calls the existing accounted use case in `mmx-application`; the routingId lookup reuses the repository method the transport change introduced (`mmx-adapter-out-persistence`). Tests: unit-test routing-key resolution + transition; integration-test the OpenAPI contract against the running app.

### D4: Operational SLOs — measure the silence, don't tool the brand

"Silence is never terminal" is only operable if a stuck `Received`/`Routed` is visible. This change fixes **what** must be measured and alerted; the metrics backend (Prometheus/Grafana, Alertmanager) is an infra/platform choice.

- **Consumer lag** on `mmx.routed-order-outcome.{orgCode}` (CGED consumer) — a rising lag means hub outcomes are not being applied; client-side orders are stuck in `Received`/`Routed`.
- **Outbox age** on the LODH leg-B outbox — a `PENDING` outbox row older than the threshold means a hub terminal has not been published; the client does not yet know.
- **Broker health** — broker unavailability affects both leg-B publish (outbox relay stalls) and consume (consumer lag rises); a single signal covers both.
- **Stale-`Routed`** — a remote client-side order parked in `Routed` beyond an SLO (the hub neither executed, cancelled, nor rejected within the window) signals a stuck remote pair awaiting a terminal outcome.
- **DLQ depth** — a non-empty `mmx.routed-order-outcome.{orgCode}.dlq` requires manual reconciliation; depth > 0 alerts.

**Adapter placement:** metric/counters emitted from `mmx-adapter-out-messaging` (consumer lag, DLQ depth), the outbox relay (outbox age), and a periodic stale-`Routed` sweep in `mmx-application` (a scheduled query over client-side orders). Alert thresholds are configuration. The specs capture the SLO + alert requirement; the brand is infra.

### D5: ADR-0002 completion — detect + alert, fully realised

ADR-0002's consequence ("status must be synchronised ... in the same transaction") is already relaxed for remote pairs by the transport change's basic-safety throw. This change completes the "alert" half and records the realised relaxation:

- **Local pairs:** atomic throw+rollback inside the hub execute transaction — **unchanged**. A missing local client still prevents the hub terminal.
- **Remote pairs:** detect (the throw in `ApplyRemoteOrderOutcomeUseCase`) + alert (DLQ + operational alert after bounded retries, per D2). The hub is already terminal before the client sees the event, so prevention is impossible; detection + alert is the realised contract.

The ADR update makes the cross-deployment relaxation explicit rather than implicit.

## Risks / Trade-offs

- **[Risk] DLQ drops a message that later becomes recoverable** — e.g., a missing client-side order whose intake commit was merely delayed. Mitigation: a small retry count (3) before DLQ; the DLQ carries the full original message + diagnostics for manual replay; the reconciliation runbook covers re-injection. Accepted: an unbounded stall is worse than a quarantined, alerted message.
- **[Risk] `ROUTING_FAILURE` vs `TRADER` misclassification at a call site** — a future reject site forgets to set the origin or sets the wrong one. Mitigation: the domain mutation requires the origin (no originless reject on new code); unit tests assert the origin at every site; `null` on historical rows reads as "pre-tracking", not a silent default.
- **[Risk] Two accounted operations diverge in behaviour** — the routingId path and orderId path could drift. Mitigation: both delegate to one transition + idempotency core; shared tests cover both addresses against the same `EXECUTED → ACCOUNTED` semantics.
- **[Trade-off] Nullable `rejection_origin` backfill** — historical rejects carry `null`, so origin-filtered queries exclude them. Accepted: a NOT NULL + default would silently misclassify; `null` is honest. Dashboards filter `WHERE rejection_origin IS NOT NULL` for classified data.
- **[Trade-off] Stale-`Routed` SLO is a polling sweep, not a push** — detecting a stuck `Routed` requires a scheduled query. Accepted: simpler than per-order timers; the sweep cadence bounds detection latency.

## Migration Plan

1. **Domain (additive):** add `RejectionOrigin` value object; extend `MoneyMarketOrder` reject mutations to record origin. No behaviour change to transitions themselves.
2. **Persistence (Flyway, additive):** nullable `rejection_origin` column; no data backfill (historical rows stay `null`). No new columns for DLQ (the DLQ is broker infrastructure).
3. **Application:** record origin at each reject site; add the broken-pair retry-then-DLQ policy; add routingId-addressed accounted resolution; add the stale-`Routed` sweep.
4. **Contracts (BACKWARD-compatible):** add the routingId-addressed accounted operation to `contracts/002-trader-orders-views/openapi.yaml` (+ `api-v1.md`); provision the DLQ topic + consumer config.
5. **Adapters:** DLQ producer + consumer policy wiring; metrics/alert hooks; routingId accounted controller via codegen.
6. **Rollback:** migrations are additive (nullable column only); revert code; the `accounted` orderId path and local routing are untouched. Remote routing keeps working without the hardening (it reverts to the transport change's basic-safety throw); the DLQ simply stops receiving.

## Open Questions

(None — all hardening decisions resolve within the boundary the transport change established. Deferred items are listed in Non-Goals.)
