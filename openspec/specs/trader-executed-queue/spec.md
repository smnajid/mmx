# trader-executed-queue Specification

## Purpose

Workspace-scoped **Executed** (executed-not-yet-accounted) list behaviour: API shape, counterparty visibility, and exclusion of **ACCOUNTED** orders.

## Requirements

### Requirement: Workspace-scoped Executed list

The system SHALL expose, for each workspace (`OrderType` **Term** or **OnCall**), an Executed list endpoint that returns orders whose `OrderStatus` is `EXECUTED` AND whose `OrderType` matches the active workspace. Orders of any other `OrderStatus` and orders of the other `OrderType` SHALL be excluded.

#### Scenario: Term Executed list returns only EXECUTED Term orders

- **WHEN** the trader requests the **Term** Executed list
- **THEN** every row has `status = EXECUTED` and `orderType = TERM`

#### Scenario: OnCall Executed list returns only EXECUTED OnCall orders

- **WHEN** the trader requests the **OnCall** Executed list
- **THEN** every row has `status = EXECUTED` and `orderType = ON_CALL`

#### Scenario: Cross-workspace orders are excluded

- **WHEN** an `EXECUTED` order has `orderType = ON_CALL`
- **THEN** it does **not** appear in the Term Executed list

#### Scenario: Non-EXECUTED orders are excluded

- **WHEN** a Term order is in `RECEIVED`, `ASSIGNED`, `CANCELLED`, or `REJECTED`
- **THEN** it does **not** appear in the Term Executed list

---

### Requirement: Counterparty visible on Executed list rows

For each row returned by an Executed list endpoint, the response SHALL include the execution `counterparty` string that was recorded when the order was executed. The Executed list response MUST NOT omit counterparty for orders that have execution data.

#### Scenario: Counterparty present on every row

- **WHEN** the Executed list (Term or OnCall) returns at least one row
- **THEN** each row carries a non-null `counterparty` field

#### Scenario: Counterparty matches what was recorded at execution

- **WHEN** an order was executed with counterparty `"Acme Bank"`
- **THEN** that order's row in the Executed list carries `counterparty = "Acme Bank"`

---

### Requirement: Accounted orders leave the Executed list

When an order transitions from `EXECUTED` to `ACCOUNTED`, it SHALL no longer appear in any Executed list response. Other orders in `EXECUTED` SHALL remain in the list.

#### Scenario: Newly accounted order disappears

- **WHEN** an order in the Term Executed list transitions to `ACCOUNTED`
- **THEN** the next request to the Term Executed list does not include it

#### Scenario: Other executed orders remain

- **WHEN** one EXECUTED Term order transitions to `ACCOUNTED` while another remains `EXECUTED`
- **THEN** the next request to the Term Executed list returns the still-`EXECUTED` order and excludes the `ACCOUNTED` one

---

### Requirement: Executed list is read-only

The Executed list endpoints SHALL be query-only and SHALL NOT modify any order's state. No Trader action (assign, unassign, execute, cancel, reject, account) is performed as a side effect of reading the list.

#### Scenario: Reading the list does not change state

- **WHEN** the trader requests an Executed list
- **THEN** every order in the result has the same `status` and `updatedAt` after the request as before

---

### Requirement: Executed list rows expose handoffStatus (contract-first OpenAPI)

For each row returned by a workspace Executed list endpoint (`GET /api/v1/orders/term/executed` and `GET /api/v1/orders/oncall/executed`), the response SHALL include `handoffStatus` with one of `PENDING`, `PUBLISHED`, or `FAILED` as defined in `specs/002-trader-orders-views/contracts/openapi.yaml`. The field SHALL be added **contract-first** (OpenAPI and `api-v1.md` before implementation). For orders in `EXECUTED` status, `handoffStatus` SHALL reflect the integration handoff state distinct from lifecycle `status` (which remains `EXECUTED` until accounted).

#### Scenario: PENDING visible while relay has not acked Kafka

- **WHEN** an order is `EXECUTED` with `handoffStatus = PENDING`
- **THEN** the Term or OnCall Executed list row includes `handoffStatus: PENDING`

#### Scenario: PUBLISHED distinguishes awaiting accounting from handoff failure

- **WHEN** an order is `EXECUTED` with `handoffStatus = PUBLISHED`
- **THEN** the Executed list row includes `handoffStatus: PUBLISHED` (trader-visible "submitted toward back-office; awaiting accounting" per FR-013)

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
