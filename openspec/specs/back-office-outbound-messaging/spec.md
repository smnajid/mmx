# back-office-outbound-messaging Specification

## Purpose

Contract-first asynchronous integration from mmx toward the back-office: transactional outbox, Kafka relay, retry/FAILED semantics, and thin handoff DTO governance.
## Requirements
### Requirement: Product and integration communication is contract-first (sync and async)

All wire formats used for **product** communication (Trader HTTP API) and **integration** communication (mmx-published Kafka toward back-office) for this capability SHALL be defined **first** in machine-readable contracts before implementation ships. **Synchronous** HTTP MUST use the canonical **OpenAPI 3** document (`contracts/002-trader-orders-views/openapi.yaml`) with prose mirror `api-v1.md`. **Asynchronous** Kafka messages MUST use the canonical **AsyncAPI 3.0** document (`contracts/002-trader-orders-views/asyncapi.yaml`) with prose mirror `asyncapi-v1.md`. Prose mirrors MUST NOT be the sole source of truth. Runtime request, response, and message bodies MUST conform to the published contracts in the **same delivery** as behavioural changes.

#### Scenario: HTTP surface changes start in OpenAPI

- **WHEN** a Trader-facing or inbound back-office HTTP operation or schema changes for this feature
- **THEN** `openapi.yaml` and `api-v1.md` are updated before controllers and generated interfaces are changed

#### Scenario: Kafka payload changes start in AsyncAPI

- **WHEN** the mmx-published execution handoff message shape or channel changes
- **THEN** `asyncapi.yaml` and `asyncapi-v1.md` are updated before producer serialisation and relay code are changed

#### Scenario: No ad-hoc Kafka payloads from domain objects

- **WHEN** mmx schedules or publishes an execution handoff message
- **THEN** the serialised bytes conform to the `OrderExecutedV1` schema defined in `asyncapi.yaml` and are not produced by unconstrained serialisation of the domain aggregate

---

### Requirement: Transactional outbox records handoff intent with contract payload

When an order transitions to `EXECUTED`, the system SHALL insert exactly one outbox row in the **same database transaction** as persisting the order as `EXECUTED` — **except for the client-side order of a routed trade**, whose `EXECUTED` transition (set by synchronous propagation from the hub-side order per `order-routing`) SHALL NOT insert an outbox row. For a routed trade, exactly one outbox row SHALL be inserted for the **hub-side order**, and its `payload` SHALL include the routing-context block (`routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`) per `order-routing` and `asyncapi.yaml`. The outbox `payload` SHALL be the JSON serialisation of `OrderExecutedV1` per `asyncapi.yaml`, frozen at schedule time. A **unique** constraint on `order_id` SHALL enforce one handoff per order.

#### Scenario: Execute commits order and outbox together

- **WHEN** the trader successfully executes a native (non-routed) assigned order
- **THEN** the order is `EXECUTED`, `handoffStatus` is `PENDING`, and exactly one outbox row exists for that `orderId` in the same commit

#### Scenario: Outbox payload matches AsyncAPI schema

- **WHEN** an outbox row is written for a newly executed order
- **THEN** its `payload` validates against the `OrderExecutedV1` schema in `asyncapi.yaml`

#### Scenario: Routed trade inserts one outbox row for the hub-side order

- **WHEN** the hub trader executes a routed hub-side order
- **THEN** exactly one outbox row exists for the hub-side `orderId` (and none for the linked client-side `orderId`) in the same commit, and its `payload` includes the routing-context block

#### Scenario: Client-side routed EXECUTED inserts no outbox row

- **WHEN** a routed client-side order transitions to `EXECUTED` via synchronous propagation
- **THEN** no outbox row is inserted for the client-side `orderId`

---

### Requirement: Outbox relay publishes to Kafka with producer ack before SENT

A scheduled relay SHALL read pending outbox rows, publish to the Kafka channel `mmx.order.executed` defined in `asyncapi.yaml`, and use **record key** = `orderId`. The outbox row SHALL transition to `SENT` and the order `handoffStatus` to `PUBLISHED` **only after** the Kafka producer acknowledges the send. The bytes on the wire SHALL equal the outbox `payload`.

#### Scenario: Successful publish updates handoff to PUBLISHED

- **WHEN** the relay publishes an outbox row and receives producer ack
- **THEN** the outbox status is `SENT`, the order `handoffStatus` is `PUBLISHED`, and the Kafka message key equals the order's `orderId`

#### Scenario: Kafka message conforms to AsyncAPI operation

- **WHEN** a message is observed on `mmx.order.executed` after relay
- **THEN** the message body validates as `OrderExecutedV1` per `asyncapi.yaml`

---

### Requirement: Publish retries with terminal FAILED after configurable max attempts

On publish failure, the relay SHALL increment `publish_attempts` and retry while `publish_attempts` is less than `mmx.backoffice.outbox.max-publish-attempts` (default **5**). When attempts reach the limit, the outbox row SHALL become `FAILED` and the order `handoffStatus` SHALL become `FAILED`. The system SHALL emit a WARN log including `orderId`.

#### Scenario: Transient failure retries before FAILED

- **WHEN** Kafka is unavailable for the first publish attempt and becomes available before max attempts
- **THEN** the message is eventually published and handoff becomes `PUBLISHED`

#### Scenario: Exhausted attempts mark FAILED

- **WHEN** publish fails on every attempt up to max-publish-attempts
- **THEN** outbox status is `FAILED`, order `handoffStatus` is `FAILED`, and a WARN log includes the `orderId`

---

### Requirement: OrderExecutedV1 is a thin handoff DTO with idempotent consumer semantics

The `OrderExecutedV1` message SHALL include only fields required for back-office booking (per `asyncapi.yaml`). The `eventType` field SHALL be the constant `OrderExecutedV1`. For a **routed trade**, the message SHALL additionally carry a routing-context block: `routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, and `clientCounterparty`, so the back office can build both the hub-side and the originating client-side contracts and correlate contract reversal/replace by `routingId` without reading any MMX routing table. These routing-context fields SHALL be **absent** for a native (non-routed) order. The back-office consumer contract (documented in `asyncapi-v1.md`) SHALL require idempotent processing keyed by `orderId`. mmx SHALL NOT require the consumer to call mmx HTTP APIs to obtain execution facts.

#### Scenario: Message is self-contained for booking

- **WHEN** a back-office consumer processes a valid `OrderExecutedV1` message for the first time
- **THEN** it can book using fields in the message body without a follow-up mmx read

#### Scenario: Duplicate delivery is safe

- **WHEN** the same `orderId` message is delivered more than once
- **THEN** duplicate processing does not create duplicate booking side effects (consumer responsibility documented in async contract)

#### Scenario: Routed message carries routing context

- **WHEN** a routed `OrderExecutedV1` is observed on `mmx.order.executed`
- **THEN** its body includes the routing-context block whose `routingId` matches the link and whose `clientOrderId`, `clientPortfolioNumber`, and `clientCounterparty` are the originating client-side order's values

#### Scenario: Native message omits routing context

- **WHEN** a native (non-routed) `OrderExecutedV1` is observed
- **THEN** its body omits the routing-context fields

---

### Requirement: AsyncAPI schema is enforced by Schema Registry compatibility checks

The canonical `OrderExecutedV1` schema registered in Redpanda Schema Registry SHALL act as the registry-level compatibility gate for future changes to the outbound messaging contract. Any proposed change to `OrderExecutedV1.json` that would break `BACKWARD` compatibility SHALL be rejected by the registry before it can be deployed. This extends the existing contract-first rule from test-time-only validation to infrastructure-level enforcement.

#### Scenario: BACKWARD-compatible schema evolution is accepted

- **WHEN** a new optional field is added to `OrderExecutedV1.json` and re-registered
- **THEN** the Schema Registry accepts the new version under subject `mmx.order.executed-value`

#### Scenario: BACKWARD-incompatible change is rejected

- **WHEN** a required field is removed from `OrderExecutedV1.json` and registration is attempted
- **THEN** the Schema Registry rejects the registration with a compatibility error before the file can be deployed

### Requirement: OnCall rate update is recorded in the transactional outbox

When a trader adds a rate (a new `PENDING_CONFIRMATION` segment is persisted), the system SHALL insert exactly one outbox row in the **same database transaction** as persisting the segment. The outbox `payload` SHALL be the JSON serialisation of **`OnCallRateUpdatedV1`** per `asyncapi.yaml`, frozen at schedule time. The back office SHALL observe the handoff only after the outbox **relay** successfully publishes to Kafka (post-commit); the trader REST call SHALL NOT make a synchronous gateway call to the back office.

#### Scenario: Adding a rate commits segment and outbox together

- **WHEN** a trader adds a rate and the segment is persisted as `PENDING_CONFIRMATION`
- **THEN** exactly one outbox row carrying the `OnCallRateUpdatedV1` payload for that `segmentId` exists in the same commit

#### Scenario: Add succeeds regardless of Kafka availability

- **WHEN** a trader adds a rate while Kafka is unavailable
- **THEN** the HTTP response is success, the segment is `PENDING_CONFIRMATION`, and the handoff remains pending or becomes `FAILED` per relay rules — not rolled back

---

### Requirement: Relay publishes OnCall rate updates keyed by segmentId

A scheduled relay SHALL read pending OnCall outbox rows, publish them to the OnCall Kafka channel defined in `asyncapi.yaml`, and use **record key = `segmentId`**. The bytes on the wire SHALL equal the outbox `payload`. The outbox row SHALL transition to `SENT` only after the Kafka producer acknowledges the send, reusing the existing retry/`FAILED` semantics of this capability.

#### Scenario: Successful publish marks the row SENT

- **WHEN** the relay publishes an OnCall outbox row and receives producer ack
- **THEN** the outbox status is `SENT` and the Kafka message key equals the segment's `segmentId`

#### Scenario: Message conforms to AsyncAPI

- **WHEN** a message is observed on the OnCall channel after relay
- **THEN** the message body validates as `OnCallRateUpdatedV1` per `asyncapi.yaml`

---

### Requirement: OnCallRateUpdatedV1 is a thin delta DTO

The `OnCallRateUpdatedV1` message SHALL include only the fields the back office needs to refresh in-life contracts: `eventType` (constant `OnCallRateUpdatedV1`), `segmentId`, the curve key `institution`, `currency`, `noticePeriod`, the `rate`, and the inclusive `valueDate`. The message SHALL NOT carry the superseded prior end date — it is **implicit** (a segment ends the day before the next starts; the consumer derives `prior.end = valueDate − 1`). The consumer contract SHALL require idempotent processing keyed by `segmentId`.

#### Scenario: Message is self-contained and omits prior end date

- **WHEN** a back-office consumer processes a valid `OnCallRateUpdatedV1` message
- **THEN** it can refresh contracts using only the message fields and derives the prior segment's end as `valueDate − 1` without a follow-up mmx read

#### Scenario: Duplicate delivery is safe

- **WHEN** the same `segmentId` message is delivered more than once
- **THEN** duplicate processing does not create duplicate side effects (consumer responsibility documented in the async contract)

---

### Requirement: Cancelling a pending rate publishes a cancel notification

When a trader cancels a `PENDING_CONFIRMATION` segment, the system SHALL record an outbox row (same transaction as the cancel) whose payload notifies the back office of the cancellation, keyed by `segmentId`, and relayed to Kafka under the same outbox/relay semantics. This notification is an **early-discard / observability** signal so the back office can drop not-yet-applied refresh work; it is not the correctness mechanism — confirmation safety is guaranteed by the atomic compare-and-set on the inbound confirmation (a confirmation of a canceled segment is rejected).

#### Scenario: Cancel schedules a cancel notification

- **WHEN** a trader cancels a `PENDING_CONFIRMATION` segment
- **THEN** an outbox row carrying the cancel notification for that `segmentId` is committed in the same transaction as the `CANCELED` transition

#### Scenario: Cancel notification is keyed by segmentId

- **WHEN** the relay publishes the cancel notification
- **THEN** the Kafka message key equals the canceled segment's `segmentId`

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

