# back-office-accounting-handoff (delta)

## MODIFIED Requirements

### Requirement: Outbound transmission of every newly executed order

For every order that transitions to `EXECUTED`, the system SHALL schedule exactly one durable back-office handoff by writing a transactional **outbox** row whose payload conforms to **`OrderExecutedV1`** in `specs/002-trader-orders-views/contracts/asyncapi.yaml`. Scheduling SHALL occur in the **same database transaction** as persisting `EXECUTED` (via `ExecuteOrderService` orchestration). The back-office SHALL observe the handoff only after the outbox **relay** successfully publishes to Kafka (post-commit); mmx MUST NOT invoke a synchronous `notifyExecution` gateway call from the REST controller.

#### Scenario: Each EXECUTED transition schedules exactly one outbox row

- **WHEN** the trader executes an assigned order
- **THEN** exactly one outbox row exists for that `orderId` and `handoffStatus` is `PENDING` after commit

#### Scenario: Back-office cannot observe handoff before EXECUTED commit

- **WHEN** the outbox row is created for a given order
- **THEN** querying the order by `orderId` in the same database transaction returns `status = EXECUTED`

#### Scenario: Trader execute succeeds regardless of Kafka availability

- **WHEN** the trader executes an assigned order while Kafka is unavailable
- **THEN** the HTTP response is success, the order is `EXECUTED`, and handoff remains `PENDING` or becomes `FAILED` per relay rules — not rolled back

---

## REMOVED Requirements

### Requirement: Best-effort transmission tolerates back-office unavailability

**Reason**: Superseded by transactional outbox, Kafka relay with retries, and `handoffStatus` (`PENDING` / `PUBLISHED` / `FAILED`) per change `async-resilient-backoffice-communication`.

**Migration**: Outbound handoff is no longer a synchronous swallowed gateway failure; durable outbox + relay + AsyncAPI contract apply. Inbound accounted callback requirements are unchanged.

---

## ADDED Requirements

### Requirement: Inbound HTTP accounted callback remains contract-first OpenAPI

The inbound `POST /api/v1/back-office/orders/{orderId}/accounted` surface SHALL remain defined in `specs/002-trader-orders-views/contracts/openapi.yaml` (contract-first sync). Idempotent `EXECUTED → ACCOUNTED` behaviour and POC unauthenticated callback posture from the baseline spec are unchanged.

#### Scenario: Accounted callback documented in OpenAPI

- **WHEN** the accounted callback contract is reviewed for this delivery
- **THEN** the operation and response codes are declared in `openapi.yaml` and described in `api-v1.md`

#### Scenario: Outbound async and inbound sync contracts are both canonical

- **WHEN** integration behaviour for this feature is specified
- **THEN** outbound Kafka uses `asyncapi.yaml` and inbound HTTP uses `openapi.yaml` — neither is prose-only or code-first
