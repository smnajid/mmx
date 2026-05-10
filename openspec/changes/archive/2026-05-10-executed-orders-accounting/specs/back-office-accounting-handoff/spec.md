## ADDED Requirements

### Requirement: ACCOUNTED is the only transition out of EXECUTED, and is terminal

The order lifecycle SHALL allow exactly one transition out of `OrderStatus.EXECUTED`: to `OrderStatus.ACCOUNTED`. `ACCOUNTED` SHALL be terminal — no further transitions are permitted from it. Attempting any other transition out of `EXECUTED` (e.g. back to `ASSIGNED`, or to `CANCELLED` / `REJECTED`) SHALL fail.

#### Scenario: EXECUTED can transition to ACCOUNTED

- **WHEN** an order in `EXECUTED` is marked accounted
- **THEN** its `status` becomes `ACCOUNTED` and its `updatedAt` is bumped to the moment of transition

#### Scenario: ACCOUNTED cannot transition further

- **WHEN** an order in `ACCOUNTED` is asked to transition to any other status
- **THEN** the system raises an invalid-status-transition error and the order remains `ACCOUNTED`

#### Scenario: Non-EXECUTED orders cannot become ACCOUNTED

- **WHEN** an order in `RECEIVED`, `ASSIGNED`, `CANCELLED`, or `REJECTED` is asked to be marked accounted
- **THEN** the system raises an invalid-status-transition error and the order's `status` is unchanged

---

### Requirement: Outbound transmission of every newly executed order

For every order that transitions to `EXECUTED`, the system SHALL invoke the back-office handoff exactly once with a representation of that order. The invocation SHALL occur **after** the order has been persisted as `EXECUTED`, so that the back-office can never observe an order before mmx has committed the new status.

#### Scenario: Each EXECUTED transition triggers exactly one transmission

- **WHEN** the trader executes an assigned order
- **THEN** the back-office handoff is invoked exactly once with that order

#### Scenario: Transmission happens after the EXECUTED commit

- **WHEN** the back-office handoff is invoked for a given order
- **THEN** querying the order by `orderId` at that moment returns `status = EXECUTED`

---

### Requirement: Best-effort transmission tolerates back-office unavailability

If the outbound back-office handoff fails (the gateway raises an error or is unavailable), the system SHALL NOT fail the executing Trader's HTTP request, SHALL leave the order in `EXECUTED`, and SHALL log the failure for operational visibility. No automatic retry is performed in this change (durable retry is deferred to a future change).

#### Scenario: Trader's execute succeeds despite gateway failure

- **WHEN** the back-office gateway throws during transmission
- **THEN** the executing Trader's HTTP response is success and the order is persisted as `EXECUTED`

#### Scenario: Order remains EXECUTED on transmission failure

- **WHEN** the back-office gateway throws during transmission
- **THEN** the order's `status` remains `EXECUTED` and is not retried automatically

#### Scenario: Failure is logged for operational follow-up

- **WHEN** the back-office gateway throws during transmission
- **THEN** the system emits a warning log entry that includes the order's `orderId`

---

### Requirement: Inbound accounted callback addressed by orderId

The system SHALL expose an inbound HTTP endpoint `POST /api/v1/back-office/orders/{orderId}/accounted` that, when invoked for an existing order in `EXECUTED` status, transitions that order to `ACCOUNTED`. The path identifier SHALL be the mmx `orderId` (UUID). The body MAY include an `accountedAt` informational field; mmx records its own server-side `updatedAt` regardless and does not depend on the body.

#### Scenario: Callback for an EXECUTED order accounts it

- **WHEN** the back-office posts to `/api/v1/back-office/orders/{orderId}/accounted` for an order in `EXECUTED`
- **THEN** the response is `200`, the order's `status` becomes `ACCOUNTED`, and an audit event records the transition

#### Scenario: Callback for an unknown orderId returns 404

- **WHEN** the back-office posts the callback with an `orderId` that does not exist
- **THEN** the response is `404` with error code `ORDER_NOT_FOUND` and no order state changes

#### Scenario: Callback for an order not in EXECUTED returns 409

- **WHEN** the back-office posts the callback for an order in `RECEIVED`, `ASSIGNED`, `CANCELLED`, or `REJECTED`
- **THEN** the response is `409` with error code `INVALID_STATUS_TRANSITION` and the order's `status` is unchanged

#### Scenario: Body is optional

- **WHEN** the back-office posts the callback with an empty JSON body `{}`
- **THEN** the response is `200` and the order is accounted using mmx's server-side timestamp

---

### Requirement: Accounted callback is idempotent

A repeated accounted callback for the same `orderId` SHALL be a no-op once the order is already `ACCOUNTED`. The response SHALL be `200`, the order's `updatedAt` SHALL NOT be modified by the duplicate, and no second audit event SHALL be emitted.

#### Scenario: Duplicate callback returns 200 without change

- **WHEN** the back-office posts the accounted callback twice for the same `orderId`
- **THEN** both responses are `200` and the order's `updatedAt` after the second call equals the value set by the first call

#### Scenario: Duplicate callback does not re-emit audit event

- **WHEN** the back-office posts the accounted callback for an already-`ACCOUNTED` order
- **THEN** no new `ORDER_ACCOUNTED` audit event is recorded

---

### Requirement: Inbound callback is unauthenticated for the POC

The accounted callback endpoint SHALL accept requests without authentication for the duration of this POC. The OpenAPI specification SHALL declare this explicitly via `security: []` on the operation, overriding any global security scheme. Production deployment SHALL add at minimum a shared secret or network-level allow-list (out of scope for this change).

#### Scenario: Unauthenticated request is processed

- **WHEN** the accounted callback is invoked without any authentication header
- **THEN** the system processes the request normally and applies the rules above (200 / 404 / 409)

#### Scenario: Trader-facing endpoints remain authenticated

- **WHEN** a request to a Trader-facing endpoint (e.g. `/api/v1/orders/term/executed`) arrives without `X-Trader-Id`
- **THEN** the system rejects it per the existing Trader authentication rules — the unauthenticated callback path does not weaken Trader endpoints
