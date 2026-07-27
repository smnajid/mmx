## ADDED Requirements

### Requirement: Inbound accounted callback addressed by routing key for remote client-side orders

For a remote routed trade, the system SHALL expose an additional inbound HTTP endpoint `POST /api/v1/back-office/orders/by-routing-key/{originatingLegalEntityCode}/{routingId}/accounted` that resolves the client-side order by the cross-boundary key `(originatingLegalEntityCode, routingId)` and, when that order is in `EXECUTED` status, transitions it to `ACCOUNTED`. This operation SHALL be used for remote client-side orders because no internal order UUID crosses the organisation boundary and the client deployment's back office learns of the execution only via the leg-B outcome keyed by `(originatingLegalEntityCode, routingId)`. The operation SHALL be defined contract-first in `contracts/002-trader-orders-views/openapi.yaml` (+ `api-v1.md` mirror) and SHALL reuse the same `EXECUTED → ACCOUNTED` transition and idempotency semantics as the `orderId`-addressed callback. The body MAY include an `accountedAt` informational field; mmx records its own server-side `updatedAt` regardless. The existing `POST /api/v1/back-office/orders/{orderId}/accounted` endpoint SHALL remain unchanged and canonical for local orders and for the hub-side order of a routed trade. Resolution SHALL fail with `ORDER_NOT_FOUND` (404) when no client-side order matches the routing key.

#### Scenario: Routing-key callback accounts a remote client-side order

- **WHEN** the back office posts to `/api/v1/back-office/orders/by-routing-key/{originatingLegalEntityCode}/{routingId}/accounted` for a remote client-side order in `EXECUTED`
- **THEN** the response is `200`, the order's `status` becomes `ACCOUNTED`, and an audit event records the transition

#### Scenario: Routing-key callback is idempotent

- **WHEN** the back office posts the routing-key callback twice for the same already-`ACCOUNTED` client-side order
- **THEN** both responses are `200`, no second audit event is emitted, and `updatedAt` is not modified by the duplicate

#### Scenario: Unknown routing key returns 404

- **WHEN** the back office posts the routing-key callback with a `(originatingLegalEntityCode, routingId)` that matches no client-side order
- **THEN** the response is `404` with error code `ORDER_NOT_FOUND` and no order state changes

#### Scenario: Routing-key callback for an order not in EXECUTED returns 409

- **WHEN** the routing-key callback resolves a client-side order that is in `RECEIVED`, `ROUTED`, `CANCELLED`, or `REJECTED`
- **THEN** the response is `409` with error code `INVALID_STATUS_TRANSITION` and the order's status is unchanged

#### Scenario: The orderId callback remains canonical for local and hub-side orders

- **WHEN** the back office accounts a local order or the hub-side order of a routed trade
- **THEN** it addresses the existing `POST /api/v1/back-office/orders/{orderId}/accounted` endpoint, which is unchanged by this requirement

#### Scenario: Routing-key callback is declared in OpenAPI

- **WHEN** the accounted callback contract is reviewed for this delivery
- **THEN** the routing-key operation and its response codes are declared in `openapi.yaml` and described in `api-v1.md`, contract-first
