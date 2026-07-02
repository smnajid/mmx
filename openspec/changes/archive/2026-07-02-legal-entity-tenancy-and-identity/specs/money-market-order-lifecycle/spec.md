## MODIFIED Requirements

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

### Requirement: Any trader may read order detail within their LegalEntity

Any authenticated trader whose active session scope matches the order's owning `LegalEntityCode` SHALL view full order details regardless of status or assignment. A trader scoped to a different LegalEntity SHALL NOT view the order. Mutating actions remain restricted to the assignee.

#### Scenario: Unassigned trader reads Assigned order in same entity

- **WHEN** a trader scoped to `LOC` requests order detail for an `ASSIGNED` order owned by `LOC`
- **THEN** the system returns the full detail payload

#### Scenario: Trader in another entity cannot read the order

- **WHEN** a trader scoped to `PAR` requests order detail for an order owned by `LOC`
- **THEN** the system does not return the order (not-found / unauthorised)
