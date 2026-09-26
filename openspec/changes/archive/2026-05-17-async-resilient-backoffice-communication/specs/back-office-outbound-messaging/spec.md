# back-office-outbound-messaging (delta)

## ADDED Requirements

### Requirement: Product and integration communication is contract-first (sync and async)

All wire formats used for **product** communication (Trader HTTP API) and **integration** communication (mmx-published Kafka toward back-office) for this capability SHALL be defined **first** in machine-readable contracts before implementation ships. **Synchronous** HTTP MUST use the canonical **OpenAPI 3** document (`specs/002-trader-orders-views/contracts/openapi.yaml`) with prose mirror `api-v1.md`. **Asynchronous** Kafka messages MUST use the canonical **AsyncAPI 3.0** document (`specs/002-trader-orders-views/contracts/asyncapi.yaml`) with prose mirror `asyncapi-v1.md`. Prose mirrors MUST NOT be the sole source of truth. Runtime request, response, and message bodies MUST conform to the published contracts in the **same delivery** as behavioural changes.

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

When an order transitions to `EXECUTED`, the system SHALL insert exactly one outbox row in the **same database transaction** as persisting the order as `EXECUTED`. The outbox `payload` SHALL be the JSON serialisation of `OrderExecutedV1` per `asyncapi.yaml`, frozen at schedule time. A **unique** constraint on `order_id` SHALL enforce one handoff per order.

#### Scenario: Execute commits order and outbox together

- **WHEN** the trader successfully executes an assigned order
- **THEN** the order is `EXECUTED`, `handoffStatus` is `PENDING`, and exactly one outbox row exists for that `orderId` in the same commit

#### Scenario: Outbox payload matches AsyncAPI schema

- **WHEN** an outbox row is written for a newly executed order
- **THEN** its `payload` validates against the `OrderExecutedV1` schema in `asyncapi.yaml`

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

The `OrderExecutedV1` message SHALL include only fields required for back-office booking (per `asyncapi.yaml`). The `eventType` field SHALL be the constant `OrderExecutedV1`. The back-office consumer contract (documented in `asyncapi-v1.md`) SHALL require idempotent processing keyed by `orderId`. mmx SHALL NOT require the consumer to call mmx HTTP APIs to obtain execution facts.

#### Scenario: Message is self-contained for booking

- **WHEN** a back-office consumer processes a valid `OrderExecutedV1` message for the first time
- **THEN** it can book using fields in the message body without a follow-up mmx read

#### Scenario: Duplicate delivery is safe

- **WHEN** the same `orderId` message is delivered more than once
- **THEN** duplicate processing does not create duplicate booking side effects (consumer responsibility documented in async contract)
