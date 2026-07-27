## ADDED Requirements

### Requirement: Dead-letter channel for the routed-order-outcome consumer

The consumer deployment of the `mmx.routed-order-outcome.{orgCode}` channel SHALL own a dead-letter topic `mmx.routed-order-outcome.{orgCode}.dlq` to quarantine messages that deterministically cannot be applied (a `RoutedOrderPairIntegrityException` that recurs across the bounded attempt count governed by `order-routing`). The dead-letter topic SHALL be produced and owned by the consuming deployment (the deployment that failed to apply the message); the producing deployment SHALL be unaware of it. A message routed to the dead-letter topic SHALL carry the original `RoutingOutcomeV1` payload plus a diagnostic envelope (failure reason, failing offset, attempt count, detected-at timestamp). A non-empty dead-letter topic SHALL require manual reconciliation; the system SHALL NOT automatically replay dead-lettered messages. Messages failing for transient causes (database unavailability, deserialisation glitches) SHALL NOT reach the dead-letter topic.

#### Scenario: The dead-letter topic is consumer-owned

- **WHEN** CGED cannot apply a leg-B outcome from `mmx.routed-order-outcome.LODH` after the configured retries
- **THEN** the message is published to `mmx.routed-order-outcome.LODH.dlq`, which CGED owns and produces to; LODH is not involved

#### Scenario: Dead-letter depth is observable

- **WHEN** the dead-letter topic contains one or more messages
- **THEN** the depth is exposed as an operational metric and an alert fires so an operator can reconcile

#### Scenario: No automatic replay

- **WHEN** a message has been published to the dead-letter topic
- **THEN** the system does not re-inject it into the primary topic; reconciliation is a manual operational act

---

### Requirement: Operational health SLOs for the routed-order-outcome channel

The system SHALL expose operational health signals for the cross-org routed-order-outcome channel so that a silent `Received`/`Routed` is visible to operations: (a) **consumer lag** on the `mmx.routed-order-outcome.{orgCode}` consumer (a rising lag means hub outcomes are not being applied); (b) **outbox age** on the hub's routed-order-outcome outbox (a `PENDING` row older than a threshold means a hub terminal has not been published and the client does not yet know); (c) **broker health** for the shared Kafka cluster (unavailability affects both publish and consume); (d) **dead-letter depth** on `mmx.routed-order-outcome.{orgCode}.dlq` (depth greater than zero requires manual reconciliation). Each signal SHALL have a configurable alert threshold; breaching a threshold SHALL emit an operational alert. The specific metrics backend (e.g. Prometheus/Grafana, Alertmanager) is an infrastructure choice; this requirement fixes what SHALL be measured and alerted, not the brand.

#### Scenario: Consumer lag is measured and alerted

- **WHEN** the routed-order-outcome consumer lag exceeds its threshold
- **THEN** an operational alert is emitted indicating client-side orders may be stuck awaiting outcome apply

#### Scenario: Outbox age is measured and alerted

- **WHEN** a routed-order-outcome outbox row remains `PENDING` beyond its age threshold
- **THEN** an operational alert is emitted indicating a hub terminal outcome has not been published

#### Scenario: Broker unavailability is alerted

- **WHEN** the shared Kafka broker is unavailable
- **THEN** an operational alert covers both the publish (relay stalls) and consume (lag rises) impact on the channel

#### Scenario: Dead-letter depth is alerted

- **WHEN** the dead-letter topic depth is greater than zero
- **THEN** an operational alert is emitted requiring manual reconciliation
