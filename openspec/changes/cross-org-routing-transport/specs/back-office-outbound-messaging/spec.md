## ADDED Requirements

### Requirement: Routed-order-outcome channel for cross-org outcome propagation

For a remote routed order, the hub deployment SHALL emit cross-org outcome events on a dedicated **routed-order-outcome** Kafka channel, reusing the existing transactional-outbox + relay infrastructure. The topic SHALL be **owned by the hub deployment** and **suffixed with the hub's `OrganisationCode`** (e.g. `mmx.routed-order-outcome.LODH`); the client deployment SHALL hold a **consume-only ACL**. The channel SHALL carry a type-discriminated payload supporting `ACCEPTED` (non-terminal, emitted same-transaction as hub-side order creation in `AcceptRoutedHubOrderUseCase`), `EXECUTED`, `CANCELLED`, and `REJECTED` (trader-reject only). The outbox row for each event SHALL be committed in the **same database transaction** as the hub-side transition it mirrors (order creation for `ACCEPTED`; execute/cancel/reject for terminal outcomes). The `RoutingOutcomeV1` message SHALL carry `(originatingLegalEntityCode, routingId)` as the cross-boundary correlation key and SHALL NOT carry any deployment-internal order UUID. The relay SHALL publish keyed by `(originatingLegalEntityCode, routingId)`. This channel is distinct from `mmx.order.executed` (the back-office execution-handoff channel) and carries inter-deployment routing outcomes, not back-office booking facts.

#### Scenario: ACCEPTED is emitted same-transaction as hub-side order creation

- **WHEN** LODH's `AcceptRoutedHubOrderUseCase` creates a hub-side order for a remote client
- **THEN** an `ACCEPTED` outbox row is committed in the same transaction as the hub-side order, on the `mmx.routed-order-outcome.LODH` topic

#### Scenario: Terminal outcomes are emitted same-transaction as the hub-side transition

- **WHEN** the hub trader executes a remote hub-side order
- **THEN** an `EXECUTED` outbox row is committed in the same transaction as the hub-side `EXECUTED` transition, on the `mmx.routed-order-outcome.LODH` topic

#### Scenario: Routing-failure reject emits no outcome event

- **WHEN** LODH's `AcceptRoutedHubOrderUseCase` rejects a leg-A request (grant/currency/tenor invalid) and creates no hub-side order
- **THEN** no outbox row is committed on the routed-order-outcome channel (the reject is HTTP-only)

#### Scenario: The outcome message carries only the cross-boundary correlation key

- **WHEN** a routed-order-outcome message is inspected
- **THEN** it carries `(originatingLegalEntityCode, routingId)` and does not carry any deployment-internal order UUID across the organisation boundary

#### Scenario: The routed-order-outcome channel is distinct from the back-office execution channel

- **WHEN** a remote hub-side order is executed
- **THEN** the back-office `OrderExecutedV1` is published on `mmx.order.executed` (for back-office booking) AND a routing-outcome `EXECUTED` is published on `mmx.routed-order-outcome.LODH` (for the client-side order apply) — two distinct channels with distinct payloads
