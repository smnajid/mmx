## ADDED Requirements

### Requirement: Rejection origin classification at routing reject sites

At every routing-related reject site, the system SHALL set the `RejectionOrigin` (governed by `money-market-order-lifecycle`) as follows: a local intake routing failure (unresolved `GlobalAccountDirectory` account, delegated-grant or enabled-set violation) SHALL be `ROUTING_FAILURE`; a remote client-side routing failure (unresolved `ExternalIdentityGateway` account, no round-trip) SHALL be `ROUTING_FAILURE`; a remote accept routing failure at the hub's `AcceptRoutedHubOrderUseCase` (grant/currency/tenor violation, closing the client-side order via the leg-A HTTP reject) SHALL be `ROUTING_FAILURE`; a hub-trader reject of an existing hub-side order (propagated to the client-side order, whether synchronously for a local pair or via a leg-B `REJECTED` event for a remote pair) SHALL be `TRADER`. A routing-failure reject that is HTTP-only (no hub-side order created) SHALL still record `ROUTING_FAILURE` on the client-side order. This requirement governs which origin each routing reject site records; the origin value itself and its persistence are governed by `money-market-order-lifecycle`.

#### Scenario: Local routing failure is ROUTING_FAILURE

- **WHEN** a local TradingClient's intake fails routing at the client deployment
- **THEN** the client-side order carries `rejectionOrigin = ROUTING_FAILURE`

#### Scenario: Remote routing failure at the client is ROUTING_FAILURE

- **WHEN** a remote TradingClient's intake fails at the client deployment (unresolved account or a leg-A HTTP reject for a grant violation)
- **THEN** the client-side order carries `rejectionOrigin = ROUTING_FAILURE`

#### Scenario: Hub-trader reject propagated is TRADER

- **WHEN** the hub trader rejects an existing routed hub-side order and the reject propagates to the client-side order
- **THEN** the client-side order carries `rejectionOrigin = TRADER`

---

### Requirement: Broken-pair leg-B outcome quarantine

For a remote pair, when `ApplyRemoteOrderOutcomeUseCase` detects a broken pair — a missing client-side order for `(originatingLegalEntityCode=self, routingId)`, or a mismatched terminal (a genuine divergence between the event and the client-side order's state) — and the same deterministic `RoutedOrderPairIntegrityException` recurs across a bounded number of attempts (`mmx.cross-org.outcome.max-attempts`, default 3) on the same offset, the consumer SHALL publish the original message to the dead-letter topic `mmx.routed-order-outcome.{orgCode}.dlq`, emit an operational alert, and commit the offset so the consumer advances past the poison message. Transient failures (database unavailability, deserialisation glitches) SHALL NOT be routed to the dead-letter topic; they SHALL retry via the consumer's normal backoff without ever reaching the dead-letter path. The dead-letter payload SHALL carry the original `RoutingOutcomeV1` message plus a diagnostic envelope (failure reason, failing offset, attempt count, detected-at timestamp) so an operator can reconcile manually. The local hub-side throw-and-rollback on a missing local client-side order (the in-process, transactional path) SHALL remain unchanged — it is not a Kafka consumer path and is not subject to this quarantine.

#### Scenario: Deterministic broken pair is dead-lettered after bounded retries

- **WHEN** a leg-B outcome for a remote pair repeatedly yields `RoutedOrderPairIntegrityException` because the client-side order is missing, up to the configured attempt count
- **THEN** the message is published to `mmx.routed-order-outcome.{orgCode}.dlq`, an alert is emitted, and the consumer offset advances past it

#### Scenario: Transient failure is not dead-lettered

- **WHEN** a leg-B outcome fails to apply because the consumer's database is momentarily unavailable
- **THEN** the message retries via normal backoff and is never written to the dead-letter topic

#### Scenario: Local hub-side throw is unchanged

- **WHEN** the hub trader executes a local routed hub-side order whose linked local client-side order is missing
- **THEN** the in-process `RoutedOrderPairIntegrityException` rolls back the hub-side terminal transition as before, and no dead-letter topic is involved

#### Scenario: Dead-letter payload carries diagnostics

- **WHEN** a message is published to the dead-letter topic
- **THEN** its payload includes the original message plus the failure reason, failing offset, attempt count, and detected-at timestamp

---

### Requirement: Stale-Routed detection for remote client-side orders

The system SHALL detect a remote client-side order that has remained in `ROUTED` beyond a configurable staleness threshold (`mmx.cross-org.stale-routed.threshold`, a duration) without reaching a terminal outcome (`EXECUTED`, `CANCELLED`, or `REJECTED`) from the hub. Detection SHALL run as a periodic sweep over remote client-side orders in `ROUTED`. A detected stale-`Routed` order SHALL emit an operational alert; it SHALL NOT trigger an automatic state transition (consistent with "silence is never terminal" — the resolution is an operational/reconciliation act, not a domain transition). Local client-side orders SHALL be subject to the same sweep if configured, but local routing's synchronous propagation is the primary correctness mechanism for local pairs.

#### Scenario: A remote Routed order past the threshold alerts

- **WHEN** a remote client-side order has been in `ROUTED` longer than the staleness threshold and no terminal outcome has arrived
- **THEN** an operational alert is emitted and the order remains `ROUTED` (no automatic transition)

#### Scenario: A Routed order that reaches terminal does not alert

- **WHEN** a remote client-side order receives its terminal outcome within the staleness threshold
- **THEN** no stale-`Routed` alert is emitted for it

---

### Requirement: Remote client-side accounting correlated by routing key

For a remote routed trade, the client deployment's back office SHALL account the client-side order using the cross-boundary correlation key `(originatingLegalEntityCode, routingId)`, because no internal order UUID crosses the organisation boundary and the back office learns of the execution only via the leg-B outcome. The detailed callback contract is governed by `back-office-accounting-handoff`. Local routed orders SHALL continue to be accounted by `orderId` on both sides unchanged.

#### Scenario: Remote client-side order is accounted by routing key

- **WHEN** the client deployment's back office confirms the client-side booking for a remote routed trade
- **THEN** it accounts the client-side order via its `(originatingLegalEntityCode, routingId)` key, transitioning it `EXECUTED → ACCOUNTED`

#### Scenario: Local routed orders keep orderId accounting

- **WHEN** a local routed trade is accounted on either side
- **THEN** the back office addresses the callback by `orderId` as before, and no routing-key addressing is required
