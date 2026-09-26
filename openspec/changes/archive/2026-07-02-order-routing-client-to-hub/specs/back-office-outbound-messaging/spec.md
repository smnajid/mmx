## MODIFIED Requirements

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
