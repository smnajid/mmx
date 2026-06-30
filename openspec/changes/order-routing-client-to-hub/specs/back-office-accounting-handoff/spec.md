## MODIFIED Requirements

### Requirement: ACCOUNTED is the only transition out of EXECUTED, and is terminal

The order lifecycle SHALL allow exactly one transition out of `OrderStatus.EXECUTED`: to `OrderStatus.ACCOUNTED`. `ACCOUNTED` SHALL be terminal — no further transitions are permitted from it. Attempting any other transition out of `EXECUTED` (e.g. back to `ASSIGNED`, or to `CANCELLED` / `REJECTED`) SHALL fail. An order in `ROUTED` (a routed client-side order awaiting the hub outcome) SHALL NOT be account-able; it must first reach `EXECUTED`.

#### Scenario: EXECUTED can transition to ACCOUNTED

- **WHEN** an order in `EXECUTED` is marked accounted
- **THEN** its `status` becomes `ACCOUNTED` and its `updatedAt` is bumped to the moment of transition

#### Scenario: ACCOUNTED cannot transition further

- **WHEN** an order in `ACCOUNTED` is asked to transition to any other status
- **THEN** the system raises an invalid-status-transition error and the order remains `ACCOUNTED`

#### Scenario: Non-EXECUTED orders cannot become ACCOUNTED

- **WHEN** an order in `RECEIVED`, `ROUTED`, `ASSIGNED`, `CANCELLED`, or `REJECTED` is asked to be marked accounted
- **THEN** the system raises an invalid-status-transition error and the order's `status` is unchanged

---

### Requirement: Outbound transmission of every newly executed order

For every order that transitions to `EXECUTED`, the system SHALL schedule exactly one durable back-office handoff by writing a transactional **outbox** row whose payload conforms to **`OrderExecutedV1`** in `contracts/002-trader-orders-views/asyncapi.yaml` — **except for the client-side order of a routed trade**, which SHALL NOT schedule an outbox row (its `EXECUTED` is MMX-internal; the single routed handoff is emitted from the hub-side order per `back-office-outbound-messaging` and `order-routing`). For non-routed orders, scheduling SHALL occur in the **same database transaction** as persisting `EXECUTED` (via `ExecuteOrderService` orchestration). The back-office SHALL observe the handoff only after the outbox **relay** successfully publishes to Kafka (post-commit); mmx MUST NOT invoke a synchronous `notifyExecution` gateway call from the REST controller.

#### Scenario: Each EXECUTED transition schedules exactly one outbox row

- **WHEN** the trader executes a native (non-routed) assigned order
- **THEN** exactly one outbox row exists for that `orderId` and `handoffStatus` is `PENDING` after commit

#### Scenario: Back-office cannot observe handoff before EXECUTED commit

- **WHEN** the outbox row is created for a given order
- **THEN** querying the order by `orderId` in the same database transaction returns `status = EXECUTED`

#### Scenario: Trader execute succeeds regardless of Kafka availability

- **WHEN** the trader executes an assigned order while Kafka is unavailable
- **THEN** the HTTP response is success, the order is `EXECUTED`, and handoff remains `PENDING` or becomes `FAILED` per relay rules — not rolled back

#### Scenario: Routed client-side EXECUTED schedules no handoff

- **WHEN** a routed client-side order transitions to `EXECUTED` via synchronous propagation
- **THEN** no outbox row is scheduled for the client-side `orderId`; the routed handoff is scheduled once for the hub-side order

---

### Requirement: Inbound accounted callback addressed by orderId

The system SHALL expose an inbound HTTP endpoint `POST /api/v1/back-office/orders/{orderId}/accounted` that, when invoked for an existing order in `EXECUTED` status, transitions that order to `ACCOUNTED`. The path identifier SHALL be the mmx `orderId` (UUID). The body MAY include an `accountedAt` informational field; mmx records its own server-side `updatedAt` regardless and does not depend on the body. For a **routed trade**, the back office SHALL invoke the callback **twice**: once with the hub-side `orderId` (after the hub booking is confirmed by Transactions 2) and once with the client-side `orderId` (after the client booking is confirmed). The two calls are independent; each transitions its own order from `EXECUTED` to `ACCOUNTED`, and accounting one side SHALL NOT require the other.

#### Scenario: Callback for an EXECUTED order accounts it

- **WHEN** the back-office posts to `/api/v1/back-office/orders/{orderId}/accounted` for an order in `EXECUTED`
- **THEN** the response is `200`, the order's `status` becomes `ACCOUNTED`, and an audit event records the transition

#### Scenario: Callback for an unknown orderId returns 404

- **WHEN** the back-office posts the callback with an `orderId` that does not exist
- **THEN** the response is `404` with error code `ORDER_NOT_FOUND` and no order state changes

#### Scenario: Callback for an order not in EXECUTED returns 409

- **WHEN** the back-office posts the callback for an order in `RECEIVED`, `ROUTED`, `ASSIGNED`, `CANCELLED`, or `REJECTED`
- **THEN** the response is `409` with error code `INVALID_STATUS_TRANSITION` and the order's `status` is unchanged

#### Scenario: Body is optional

- **WHEN** the back-office posts the callback with an empty JSON body `{}`
- **THEN** the response is `200` and the order is accounted using mmx's server-side timestamp

#### Scenario: Routed trade accounted on both sides independently

- **WHEN** the back-office posts the accounted callback for the hub-side `orderId` and later for the client-side `orderId` of the same routed trade
- **THEN** each order transitions to `ACCOUNTED` independently; accounting the hub side does not require the client side to be accounted (and vice versa)
