## MODIFIED Requirements

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
