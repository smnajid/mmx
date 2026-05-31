## ADDED Requirements

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
