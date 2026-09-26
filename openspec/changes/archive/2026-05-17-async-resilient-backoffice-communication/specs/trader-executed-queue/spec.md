# trader-executed-queue (delta)

## ADDED Requirements

### Requirement: Executed list rows expose handoffStatus (contract-first OpenAPI)

For each row returned by a workspace Executed list endpoint (`GET /api/v1/orders/term/executed` and `GET /api/v1/orders/oncall/executed`), the response SHALL include `handoffStatus` with one of `PENDING`, `PUBLISHED`, or `FAILED` as defined in `specs/002-trader-orders-views/contracts/openapi.yaml`. The field SHALL be added **contract-first** (OpenAPI and `api-v1.md` before implementation). For orders in `EXECUTED` status, `handoffStatus` SHALL reflect the integration handoff state distinct from lifecycle `status` (which remains `EXECUTED` until accounted).

#### Scenario: PENDING visible while relay has not acked Kafka

- **WHEN** an order is `EXECUTED` with `handoffStatus = PENDING`
- **THEN** the Term or OnCall Executed list row includes `handoffStatus: PENDING`

#### Scenario: PUBLISHED distinguishes awaiting accounting from handoff failure

- **WHEN** an order is `EXECUTED` with `handoffStatus = PUBLISHED`
- **THEN** the Executed list row includes `handoffStatus: PUBLISHED` (trader-visible “submitted toward back-office; awaiting accounting” per FR-013)

#### Scenario: FAILED distinguishes integration problem from books delay

- **WHEN** an order is `EXECUTED` with `handoffStatus = FAILED` after relay exhausted publish attempts
- **THEN** the Executed list row includes `handoffStatus: FAILED` (trader-visible handoff/integration problem per FR-013)

#### Scenario: handoffStatus is defined in OpenAPI before codegen

- **WHEN** this capability is implemented
- **THEN** `handoffStatus` exists on `OrderSummaryResponse` in `openapi.yaml` and the server uses generated or contract-aligned types — not a hand-maintained parallel DTO

---

### Requirement: Trader Executed list HTTP remains contract-first sync

All Trader Executed list request and response shapes for this capability SHALL conform to the canonical OpenAPI 3 document. Async handoff payloads on Kafka remain governed by `asyncapi.yaml`; the Executed list exposes only the trader-appropriate `handoffStatus` summary, not the full `OrderExecutedV1` message body.

#### Scenario: Executed endpoints documented in OpenAPI

- **WHEN** a client integrates with Term or OnCall Executed lists including `handoffStatus`
- **THEN** paths, parameters, and `OrderSummaryResponse` schemas are declared in `openapi.yaml`
