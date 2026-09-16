# money-market-order-lifecycle Specification

## Purpose

Authoritative **Money Market Order** lifecycle rules for intake, assignment, mutation, execution, and cancellation — the core domain behaviour from feature 001 consolidated into OpenSpec. HTTP shapes are defined in `contracts/001-mm-order-processing/openapi.yaml` (intake) and `contracts/002-trader-orders-views/openapi.yaml` (desk mutations). Currency, institution, and contract-number constraints are cross-referenced in sibling capabilities; this spec owns the state machine and mutation orchestration.
## Requirements
### Requirement: Order status state machine

The system SHALL recognise `OrderStatus` values `RECEIVED`, `ROUTED`, `ASSIGNED`, `EXECUTED`, `CANCELLED`, and `REJECTED`. `ACCOUNTED` is governed by `back-office-accounting-handoff`. `ROUTED` SHALL apply only to a **client-side order** that has been handed to a TradingHub for execution; a **hub-side order** (and any native desk order) SHALL never use `ROUTED`. Allowed transitions SHALL be:

- `RECEIVED` → `ASSIGNED` (assign)
- `RECEIVED` → `CANCELLED` (cancel)
- `RECEIVED` → `REJECTED` (reject, including routing failure)
- `RECEIVED` → `ROUTED` (route at a TradingClient intake — client-side only; via synchronous local routing, or via leg-A accept / leg-B `ACCEPTED` for remote routing)
- `ASSIGNED` → `RECEIVED` (unassign)
- `ASSIGNED` → `EXECUTED` (execute)
- `ASSIGNED` → `REJECTED` (reject)
- `ROUTED` → `EXECUTED` (propagation from a hub-side execute — synchronous for local pairs, asynchronous via leg B for remote pairs)
- `ROUTED` → `REJECTED` (routing failure, or hub-side reject propagation — synchronous for local pairs, asynchronous via leg B for remote pairs)
- `ROUTED` → `CANCELLED` (hub-side cancel propagation — synchronous for local pairs, asynchronous via leg B for remote pairs)

A client-side order SHALL never transition to or from `ASSIGNED` (a TradingClient has no desk). Any other transition MUST be rejected. For a **remote** client-side order, `Received` SHALL cover all pre-confirmed-signal conditions (leg A in flight, leg A lost with circuit-breaker open, leg A succeeded at the hub but awaiting leg-B `ACCEPTED`) — these are operational metadata, not distinct domain states. Routing semantics, the two-record model, and propagation rules are governed by `order-routing`. No new `OrderStatus` value SHALL be introduced for remote routing.

#### Scenario: Assign from Received

- **WHEN** a trader assigns an order in `RECEIVED` status
- **THEN** the order transitions to `ASSIGNED` with the trader recorded as assignee

#### Scenario: Disallowed transition rejected

- **WHEN** a client attempts to cancel an order in `ASSIGNED` status
- **THEN** the system rejects the request and the status remains unchanged

#### Scenario: Routed from Received at a TradingClient intake (local)

- **WHEN** a local TradingClient's intake routes the order to its TradingHub in the same deployment
- **THEN** the client-side order transitions `RECEIVED → ROUTED` synchronously and a linked hub-side order is created in `RECEIVED`

#### Scenario: Routed from Received via leg B (remote)

- **WHEN** a remote TradingClient's order is accepted at the hub and the leg-A response is lost
- **THEN** the client-side order transitions `RECEIVED → ROUTED` when the leg-B `ACCEPTED` event arrives

#### Scenario: Remote Received covers in-flight and circuit-open

- **WHEN** a remote client-side order's leg-A request is in flight or the circuit-breaker is open
- **THEN** the order is in `RECEIVED` with no additional status value distinguishing in-flight from awaiting-confirm

#### Scenario: Routed order is never Assigned

- **WHEN** a client-side order is in `ROUTED` status
- **THEN** no `ASSIGNED` transition is permitted and no assignee can be recorded

#### Scenario: Hub-side order never uses Routed

- **WHEN** a hub-side order is created via routing
- **THEN** its status is `RECEIVED` and it follows the `RECEIVED → ASSIGNED → EXECUTED` desk path; `ROUTED` is never used for a hub-side order

### Requirement: Portfolio Management intake channel

The system SHALL accept Money Market orders from Portfolio Management via `POST /api/v1/orders` as defined in `contracts/001-mm-order-processing/openapi.yaml`. The request SHALL include a required **`legalEntityCode`** identifying the LegalEntity the order belongs to; Portfolio Management is scoped per Organisation and may submit for any LegalEntity of that Organisation. The system SHALL persist the order with the supplied `legalEntityCode` as its owning LegalEntity and in `RECEIVED` status.

#### Scenario: Valid intake persists order

- **WHEN** Portfolio Management submits a structurally valid order payload with a `legalEntityCode`
- **THEN** the system persists a new order in `RECEIVED` status owned by that LegalEntity

#### Scenario: Missing legalEntityCode rejected

- **WHEN** Portfolio Management submits an order without `legalEntityCode`
- **THEN** intake is rejected with a validation error and no order is persisted

---

### Requirement: Intake structural validation

At intake the system SHALL validate:

- `legalEntityCode` is present and identifies a LegalEntity of the Organisation Portfolio Management is authorised for, and Portfolio Management is authorised to submit for that LegalEntity
- `orderOperation` is allowed for `orderType` (Term: Subscription only; OnCall: Subscription, Increase, Decrease, Redemption)
- Subscription orders include required fields per contract (portfolio, external reference, type, currency, amount, valueDate, institution, tenor or notice period)
- Lifecycle operations reference an existing `sourceContractNumber`
- `valueDate` is at least two calendar days in the future
- `amount` is greater than zero; when `minimumRate` is present it is greater than or equal to zero

Currency, institution, tenor/notice, and amount minimum rules are additionally enforced per `order-currency-constraints` and `order-institution-constraints`.

#### Scenario: Unknown legalEntityCode rejected

- **WHEN** Portfolio Management submits an order with a `legalEntityCode` that is not a LegalEntity of its Organisation
- **THEN** intake is rejected with a validation error

#### Scenario: Invalid operation for order type rejected

- **WHEN** Portfolio Management submits a Term order with operation Increase
- **THEN** intake is rejected with a validation error

#### Scenario: ValueDate too soon rejected

- **WHEN** Portfolio Management submits an order with `valueDate` fewer than two calendar days ahead
- **THEN** intake is rejected

#### Scenario: Zero amount rejected

- **WHEN** Portfolio Management submits an order with `amount` of zero
- **THEN** intake is rejected

---

### Requirement: Intake idempotency by external reference

Order intake SHALL be idempotent on the combination of **`legalEntityCode`** and **`externalOrderReference`**. Submitting the same `(legalEntityCode, externalOrderReference)` twice MUST NOT create a duplicate order; the same `externalOrderReference` submitted for a different `legalEntityCode` SHALL create a separate order.

#### Scenario: Duplicate reference does not create second order

- **WHEN** Portfolio Management submits the same `(legalEntityCode, externalOrderReference)` twice with identical payload
- **THEN** the second request does not create a new order row

#### Scenario: Same reference for a different entity creates a separate order

- **WHEN** Portfolio Management submits the same `externalOrderReference` for `PAR` and later for `LOC`
- **THEN** two distinct orders are persisted, one owned by `PAR` and one owned by `LOC`

---

### Requirement: Assign and unassign

A trader SHALL assign a `RECEIVED` order to themselves, transitioning it to `ASSIGNED`. Only one assignee at a time. The assignee SHALL unassign, returning the order to `RECEIVED`.

#### Scenario: Concurrent assign — one winner

- **WHEN** two traders attempt to assign the same `RECEIVED` order simultaneously
- **THEN** exactly one succeeds and the other receives an error indicating the order is no longer `RECEIVED`

#### Scenario: Unassign returns to Received

- **WHEN** the assignee unassigns an `ASSIGNED` order
- **THEN** the order returns to `RECEIVED` with no assignee

---

### Requirement: Assignee-only mutation and execution

Only the trader currently assigned to an order SHALL update or execute it. Non-assignees MUST receive a clear failure with no state change.

#### Scenario: Non-assignee execute rejected

- **WHEN** a trader who is not the assignee submits execute on an `ASSIGNED` order
- **THEN** the system rejects the request and the order remains `ASSIGNED`

#### Scenario: Non-assignee update rejected

- **WHEN** a trader who is not the assignee submits an amount update
- **THEN** the system rejects the request

---

### Requirement: Update assigned order fields

The assignee SHALL update `amount` and `valueDate` on an `ASSIGNED` order. Updated values MUST pass the same validation rules as intake. `minimumRate` MUST NOT be changed after intake.

#### Scenario: Amount update revalidated

- **WHEN** the assignee updates `amount` below the per-currency minimum for the operation
- **THEN** the update is rejected per `order-currency-constraints`

#### Scenario: MinimumRate immutable after intake

- **WHEN** the assignee attempts to change `minimumRate` on an assigned order
- **THEN** the system rejects the change

---

### Requirement: Execute with executedRate and system-generated execution metadata

The assignee SHALL execute an `ASSIGNED` order by providing `executedRate` only. Counterparty and `institutionCode` are locked from intake per `order-institution-constraints`. Upon successful execution the system SHALL:

- Record `executionTime` automatically
- Generate a unique `dealingReference`
- Allocate `generatedContractNumber` per `execution-contract-number`
- Transition the order to `EXECUTED`
- Schedule back-office handoff per `back-office-outbound-messaging`

Execution MUST fail entirely if reference generation or contract allocation fails — no partial state.

#### Scenario: Missing executedRate rejected

- **WHEN** the assignee submits execute without `executedRate`
- **THEN** execution fails and the order remains `ASSIGNED`

#### Scenario: DealingReference generated on success

- **WHEN** execution succeeds
- **THEN** the order carries a system-generated `dealingReference` and recorded `executionTime`

---

### Requirement: MinimumRate floor at execute

When the order has a `minimumRate` from intake, `executedRate` MUST be greater than or equal to `minimumRate`. When `minimumRate` was omitted at intake, no PM rate floor applies.

#### Scenario: Execute below minimumRate rejected

- **WHEN** the assignee submits `executedRate` below the persisted `minimumRate`
- **THEN** execution is rejected and the order remains `ASSIGNED`

#### Scenario: Execute without PM floor when minimumRate absent

- **WHEN** intake omitted `minimumRate` and the assignee submits a valid positive `executedRate`
- **THEN** execution proceeds subject to other validation rules

---

### Requirement: Cancel and reject

Cancellation (order withdrawn) SHALL be allowed only from `RECEIVED`. Rejection (trader refuses to proceed) SHALL be allowed from `RECEIVED` or `ASSIGNED`. Any trader MAY reject a `RECEIVED` order; only the assignee MAY reject an `ASSIGNED` order. Rejection MUST include a reason.

#### Scenario: Cancel from Assigned rejected

- **WHEN** a trader attempts to cancel an `ASSIGNED` order
- **THEN** the system rejects the request

#### Scenario: Reject from Assigned requires assignee

- **WHEN** a non-assignee attempts to reject an `ASSIGNED` order
- **THEN** the system rejects the request

#### Scenario: Reject includes reason

- **WHEN** a trader rejects an order with a valid reason
- **THEN** the order transitions to `REJECTED` and the reason is persisted

---

### Requirement: Audit on every mutation

Every mutating business action (assign, unassign, update, execute, cancel, reject) SHALL produce an audit record capturing actor identity and timestamp.

#### Scenario: Assign produces audit

- **WHEN** a trader assigns an order
- **THEN** an audit entry records the actor and timestamp

---

### Requirement: Any trader may read order detail within their LegalEntity

Any authenticated trader whose active session scope matches the order's owning `LegalEntityCode` SHALL view full order details regardless of status or assignment. A trader scoped to a different LegalEntity SHALL NOT view the order. Mutating actions remain restricted to the assignee.

#### Scenario: Unassigned trader reads Assigned order in same entity

- **WHEN** a trader scoped to `LOC` requests order detail for an `ASSIGNED` order owned by `LOC`
- **THEN** the system returns the full detail payload

#### Scenario: Trader in another entity cannot read the order

- **WHEN** a trader scoped to `PAR` requests order detail for an order owned by `LOC`
- **THEN** the system does not return the order (not-found / unauthorised)

---

### Requirement: Subscription discards sourceContractNumber at intake

When `orderOperation` is Subscription, the system MUST NOT persist `sourceContractNumber`. If Portfolio Management supplies `sourceContractNumber` on a Subscription payload, the system SHALL discard it at reception.

#### Scenario: Subscription with spurious sourceContractNumber

- **WHEN** Portfolio Management submits a Subscription with `sourceContractNumber` present
- **THEN** the persisted order has null `sourceContractNumber`

