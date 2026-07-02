# order-institution-constraints Specification

## Purpose

Enforce onboarded **institution** rules at **order intake** and **execution**: Portfolio Management MUST supply an active catalog `institutionCode` at intake; the system derives **counterparty** (order vocabulary) from the institution's `displayName`. At execute, assigned traders provide only `executedRate` — the intake institution is locked; traders who cannot deal with the PM-chosen counterparty MUST reject the order. Uses `OrderAgainstInstitutionPolicy` with `InstitutionRepository`. Contract-first execute and intake API delta in `contracts/002-trader-orders-views/openapi.yaml`.

## Requirements

### Requirement: Execute requires active onboarded institutionCode

On execute, the trader request SHALL include only **`executedRate`**. The **`institutionCode`** is already set on the order at intake by Portfolio Management. The system SHALL use the intake-provided institution for execution — the trader MUST NOT change the counterparty. If the trader cannot deal with the PM-chosen counterparty, they MUST reject the order. The system MUST still validate that the institution remains active at execution time.

#### Scenario: Successful execute uses intake institution

- **WHEN** the assigned trader executes an order that was received with `institutionCode` `HSBC-01` (active, displayName `HSBC`) and provides only `executedRate` 3.45
- **THEN** the order transitions to EXECUTED with counterparty `HSBC` and executedRate 3.45

#### Scenario: Execute below minimumRate rejected

- **WHEN** the order has `minimumRate` 3.50 from intake and the assignee submits `executedRate` 3.45
- **THEN** the system rejects execute and the order remains ASSIGNED

#### Scenario: Execute with stale institution rejects

- **WHEN** institution `HSBC-01` was active at intake but is now inactive, and the assigned trader submits execute
- **THEN** the system rejects execute with a clear institution error
- **AND** the order remains ASSIGNED

#### Scenario: Trader rejects order when counterparty is unworkable

- **WHEN** the assigned trader cannot deal with the PM-chosen counterparty
- **THEN** the trader rejects the order with a reason explaining why the counterparty cannot be serviced

---

### Requirement: Execute API is contract-first with institutionCode

The canonical OpenAPI for trader execute (`contracts/002-trader-orders-views/openapi.yaml`) SHALL define `ExecuteOrderRequest` with only **`executedRate`** as required. `institutionCode` and `counterparty` SHALL NOT appear on the execute request body — they are set at intake. Prose mirror `api-v1.md` SHALL match. Generated server interfaces and Angular clients MUST align with the published contract in the same delivery.

#### Scenario: OpenAPI documents rate-only execute

- **WHEN** a consumer reads the execute operation schema
- **THEN** `executedRate` is required and neither `institutionCode` nor `counterparty` is a client-supplied execute input field

---

### Requirement: Intake unchanged for institutions in phase 1

**Superseded.** Portfolio Management intake (`POST /api/v1/orders`) SHALL now require **`institutionCode`** referencing an active institution. The system SHALL validate the institution is active and onboarded, derive `counterparty` from `Institution.displayName`, and persist both on the order at reception. `desiredCounterpartyComment` is removed from the intake request.

**TradingClient intake is grant-bound (per `delegated-institution-grants`):** when the order's owning LegalEntity is a TradingClient, the `institutionCode` SHALL reference an active **thin-proxy** institution of that TradingClient; a **delegated institution grant** for `(hubInstitution, clientLegalEntity, currency)` SHALL exist and be active; and the order's tenor (Term) or notice period (OnCall) SHALL be within the grant's enabled set for that currency. If any of these fail, intake SHALL be rejected and no order SHALL be created. At a TradingHub, the institution rules above (active, onboarded, derive counterparty) apply unchanged.

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

#### Scenario: TradingClient intake with granted tenor succeeds

- **WHEN** Portfolio Management submits a `PAR` order for proxy `BNP via LOC` in `EUR` with tenor `3M`, and the grant `(BNP, PAR, EUR)` is active with `enabledTenors` including `3M`
- **THEN** intake succeeds and the order is persisted with counterparty `BNP via LOC`

#### Scenario: TradingClient intake with tenor outside the grant is rejected

- **WHEN** Portfolio Management submits a `PAR` order for `BNP via LOC` in `EUR` with tenor `6M`, and the grant's `enabledTenors` is `["1M","3M"]`
- **THEN** the system rejects intake with a clear grant error and no order is created

#### Scenario: TradingClient intake for a non-granted institution is rejected

- **WHEN** Portfolio Management submits a `PAR` order for a proxy `SG via LOC` while no active grant `(SG, PAR, currency)` exists
- **THEN** the system rejects intake with a clear grant error and no order is created

#### Scenario: TradingClient intake with a deactivated grant is rejected

- **WHEN** Portfolio Management submits a `PAR` order for `BNP via LOC` in `EUR` and the grant `(BNP, PAR, EUR)` is inactive
- **THEN** the system rejects intake and no order is created

---

### Requirement: Lifecycle intake institution must match source contract

For OnCall orders with `orderOperation` ∈ {INCREASE, DECREASE, REDEMPTION} and a non-null `sourceContractNumber`, the system SHALL look up the original executed Subscription order by `generatedContractNumber` equal to `sourceContractNumber`. The submitted `institutionCode` MUST equal the subscription order's persisted `institutionCode`. If they differ, intake SHALL be rejected with a clear error and no order SHALL be created.

#### Scenario: Lifecycle intake with matching institution succeeds

- **WHEN** Portfolio Management submits an OnCall INCREASE with sourceContractNumber CT-00042 and institutionCode BNKCO, and the executed Subscription for CT-00042 has institutionCode BNKCO
- **THEN** intake succeeds

#### Scenario: Lifecycle intake with mismatched institution is rejected

- **WHEN** Portfolio Management submits an OnCall INCREASE with sourceContractNumber CT-00042 and institutionCode SGFR, but the executed Subscription for CT-00042 has institutionCode BNKCO
- **THEN** the system rejects intake with a clear institution/contract mismatch error
- **AND** no order is created

#### Scenario: Subscription intake is not subject to contract institution match

- **WHEN** Portfolio Management submits an OnCall SUBSCRIPTION with any active institutionCode and no sourceContractNumber
- **THEN** the lifecycle contract institution rule does not apply (normal institution validation only)
