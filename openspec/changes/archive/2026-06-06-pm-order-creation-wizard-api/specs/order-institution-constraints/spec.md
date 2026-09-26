# order-institution-constraints Specification (delta)

## MODIFIED Requirements

### Requirement: Execute requires active onboarded institutionCode

On execute, the trader request SHALL include only **`executedRate`**. The **`institutionCode`** is already set on the order at intake by Portfolio Management. The system SHALL use the intake-provided institution for execution — the trader MUST NOT change the counterparty. If the trader cannot deal with the PM-chosen counterparty, they MUST reject the order. The system MUST still validate that the institution remains active at execution time.

#### Scenario: Successful execute uses intake institution

- **WHEN** the assigned trader executes an order that was received with `institutionCode` `HSBC-01` (active, displayName `HSBC`) and provides only `executedRate` 3.45
- **THEN** the order transitions to EXECUTED with counterparty `HSBC` and executedRate 3.45

#### Scenario: Execute with stale institution rejects

- **WHEN** institution `HSBC-01` was active at intake but is now inactive, and the assigned trader submits execute
- **THEN** the system rejects execute with a clear institution error
- **AND** the order remains ASSIGNED

#### Scenario: Trader rejects order when counterparty is unworkable

- **WHEN** the assigned trader cannot deal with the PM-chosen counterparty
- **THEN** the trader rejects the order with a reason explaining why the counterparty cannot be serviced

---

### Requirement: Execute API is contract-first with institutionCode

The canonical OpenAPI for trader execute (`specs/002-trader-orders-views/contracts/openapi.yaml`) SHALL define `ExecuteOrderRequest` with only **`executedRate`** as required. `institutionCode` and `counterparty` SHALL NOT appear on the execute request body — they are set at intake. Prose mirror `api-v1.md` SHALL match. Generated server interfaces and Angular clients MUST align with the published contract in the same delivery.

#### Scenario: OpenAPI documents rate-only execute

- **WHEN** a consumer reads the execute operation schema
- **THEN** `executedRate` is required and neither `institutionCode` nor `counterparty` is a client-supplied execute input field

---

### Requirement: Intake unchanged for institutions in phase 1

**Superseded.** Portfolio Management intake (`POST /api/v1/orders`) SHALL now require **`institutionCode`** referencing an active institution. The system SHALL validate the institution is active and onboarded, derive `counterparty` from `Institution.displayName`, and persist both on the order at reception. `desiredCounterpartyComment` is removed from the intake request.

#### Scenario: Intake with valid active institutionCode succeeds

- **WHEN** Portfolio Management submits a valid order with `institutionCode` `BNKCO` for active institution with displayName `BankCo`
- **THEN** intake succeeds, the order is persisted with institutionCode `BNKCO` and counterparty `BankCo`

#### Scenario: Intake with unknown institutionCode is rejected

- **WHEN** Portfolio Management submits an order with `institutionCode` `NOPE-01` that does not exist
- **THEN** the system rejects intake with a clear institution error
- **AND** no order is created

#### Scenario: Intake with inactive institutionCode is rejected

- **WHEN** Portfolio Management submits an order with `institutionCode` `DEAD-01` for an inactive institution
- **THEN** the system rejects intake with a clear institution error
- **AND** no order is created

#### Scenario: Intake without institutionCode is rejected

- **WHEN** Portfolio Management submits an order without `institutionCode`
- **THEN** the system rejects intake with a validation error

---

## REMOVED Requirements

### Requirement: Execute UI selects institution from catalog

**Reason**: Institution is now set at intake by Portfolio Management, not selected by the trader at execution time. The trader sees the PM-chosen counterparty on the order and either executes (rate only) or rejects.
**Migration**: Trader execution UI shows the locked counterparty (from intake) and only accepts `executedRate` input. Remove institution autocomplete from execute dialog.

### Requirement: Reject execute when institution catalog is empty

**Reason**: Institution validation moves to intake. Execute uses the institution already on the order. The equivalent cold-start check ("no institutions onboarded") now applies at intake and in the order creation options API (no counterparties returned when catalog is empty).
**Migration**: Intake rejects when no active institution matches the provided code. Order creation options endpoints return empty lists when no institutions exist.
