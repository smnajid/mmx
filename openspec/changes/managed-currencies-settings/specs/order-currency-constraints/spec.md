# order-currency-constraints Specification

## Purpose

Enforce managed-currency rules at order boundaries: Portfolio Management intake (`POST /api/v1/orders`) and trader amount updates on assigned orders. Uses `OrderAgainstCurrencyPolicy` with `ManagedCurrencyRepository` and, for Decrease intake only, `OpenPositionPort` (external PositionApi).

## ADDED Requirements

### Requirement: Reject intake for unknown or inactive currency

On order receive, the system SHALL reject the request when the order currency code is not present in the managed catalog or when the matched catalog entry is inactive. When the managed catalog is empty, all intake currencies SHALL be treated as unknown.

#### Scenario: Empty catalog rejects intake

- **WHEN** Portfolio Management submits a valid structural order and no currency is onboarded
- **THEN** the system rejects intake with a clear currency error and does not persist the order

#### Scenario: Unknown currency code

- **WHEN** Portfolio Management submits an order with currency `JPY` and `JPY` is not in the catalog
- **THEN** the system rejects intake with a clear currency error

#### Scenario: Inactive currency rejects new intake

- **WHEN** EUR is deactivated and Portfolio Management submits a new EUR order
- **THEN** the system rejects intake with a clear currency error

---

### Requirement: Reject intake for disabled tenor or notice period

On order receive, for Term orders the submitted tenor code MUST be enabled for the order currency. For OnCall orders the submitted notice period code MUST be enabled for the order currency.

#### Scenario: Disabled tenor on Term order

- **WHEN** `3M` is disabled for EUR and Portfolio Management submits a Term Subscription in EUR with tenor `3M`
- **THEN** the system rejects intake indicating the tenor is not enabled for that currency

#### Scenario: Disabled notice on OnCall order

- **WHEN** `48H` is disabled for EUR and Portfolio Management submits an OnCall Subscription in EUR with notice period `48H`
- **THEN** the system rejects intake indicating the notice period is not enabled for that currency

---

### Requirement: Reject intake below operation minimum amount

On order receive, the order amount MUST be greater than or equal to the configured minimum for the operation type: Subscription uses minimum subscription amount; Increase, Decrease, and Redemption use minimum increase/decrease amount.

#### Scenario: Subscription below minimum

- **WHEN** EUR minimum subscription is `1000000.00` and Portfolio Management submits a Subscription for `500000.00` EUR
- **THEN** the system rejects intake indicating the amount is below the subscription minimum

#### Scenario: Increase below lifecycle minimum

- **WHEN** EUR minimum increase/decrease is `250000.00` and Portfolio Management submits an OnCall Increase for `100000.00` EUR
- **THEN** the system rejects intake indicating the amount is below the lifecycle minimum

---

### Requirement: Reject Decrease that leaves contract below subscription minimum

On receive of an OnCall **Decrease** order with a `sourceContractNumber`, the system SHALL load the open contract position from PositionApi by contract number. Let `B` be the outstanding amount, `A` the order amount, and `S` the currency minimum subscription amount. The system MUST reject when `B - A < S`. The system MUST also reject when the position currency does not match the order currency.

#### Scenario: Decrease would breach subscription floor

- **WHEN** EUR minimum subscription is `1000000.00`, PositionApi reports outstanding `1500000.00` for the contract, and Portfolio Management submits a Decrease of `600000.00` EUR on that contract
- **THEN** the system rejects intake because the post-decrease balance would be below the subscription minimum

#### Scenario: Valid Decrease passes floor check

- **WHEN** EUR minimum subscription is `1000000.00`, PositionApi reports outstanding `2000000.00`, and Portfolio Management submits a Decrease of `500000.00` EUR that also meets lifecycle minimum and notice rules
- **THEN** the decrease floor check passes and other validations proceed

#### Scenario: Position currency mismatch

- **WHEN** the order currency is EUR and PositionApi returns an open position for the contract with currency USD
- **THEN** the system rejects intake with a clear position/currency mismatch error

#### Scenario: Contract not found

- **WHEN** PositionApi returns no open position for the submitted `sourceContractNumber`
- **THEN** the system rejects intake with a clear contract-not-found error

#### Scenario: PositionApi unavailable fail closed

- **WHEN** PositionApi cannot be reached or returns a server error during Decrease validation
- **THEN** the system rejects intake and does not persist the order

---

### Requirement: No PositionApi validation for Increase or Redemption

On order receive, the system SHALL NOT call PositionApi for Increase or Redemption operations. Currency catalog rules (including lifecycle minimum amounts) still apply.

#### Scenario: Increase without position lookup

- **WHEN** Portfolio Management submits an OnCall Increase with valid currency rules
- **THEN** the system does not invoke PositionApi before accepting the order

---

### Requirement: Apply currency rules on trader amount update

When an assigned trader updates order amount, the system SHALL evaluate the new amount against the same managed-currency rules as intake for that order's currency, operation, order type, tenor, and notice period. Updates MUST be rejected when the currency is inactive or when the new amount violates applicable minimums. Decrease post-balance rules apply when the persisted order is a Decrease operation.

#### Scenario: Update below minimum rejected

- **WHEN** an assigned trader changes amount on a Subscription below the currency subscription minimum
- **THEN** the system rejects the update with a clear amount error

#### Scenario: Update on inactive currency rejected

- **WHEN** the order currency has been deactivated and the assigned trader attempts to change amount
- **THEN** the system rejects the update with a clear currency error

---

### Requirement: Do not re-validate existing orders on configuration change

When managed-currency configuration changes, the system SHALL NOT retroactively re-validate or change status of orders already stored. Constraint checks apply only to new receive attempts and trader update mutations.

#### Scenario: Existing order after tenor disabled

- **WHEN** a Received Term order exists with tenor `3M` and the trader later disables `3M` for that currency
- **THEN** the existing order remains unchanged and is not auto-rejected

---

### Requirement: Duplicate receive ignores payload and currency re-check

When Portfolio Management submits an `externalOrderReference` that already exists, the system SHALL return the existing order without applying the new payload and without re-running currency constraint validation on the duplicate request.

#### Scenario: Duplicate receive after currency disabled

- **WHEN** an order was previously accepted for EUR and Portfolio Management sends the same external reference again after EUR was deactivated
- **THEN** the system returns the existing order with `newlyCreated` false and does not reject as duplicate intake

---

### Requirement: Policy implemented in domain with outbound ports

Currency constraint evaluation SHALL be implemented in `OrderAgainstCurrencyPolicy` in `mmx-domain` using snapshots from `ManagedCurrencyRepository` and `OpenPositionPort`. `ReceiveOrderService` and `UpdateOrderService` SHALL invoke the policy before mutating aggregates. Automated tests SHALL cover the policy matrix independently of HTTP adapters.

#### Scenario: Domain test rejects unknown currency

- **WHEN** the policy is invoked with no matching managed currency for the order code
- **THEN** validation fails with a domain validation exception suitable for mapping to HTTP 4xx
