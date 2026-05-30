# order-institution-constraints Specification

## Purpose

Enforce onboarded **institution** rules at **order execution**: assigned traders MUST select an active catalog institution; execution facts persist **counterparty** (order vocabulary) from the institution’s `displayName`. Uses `OrderAgainstInstitutionPolicy` with `InstitutionRepository`. Contract-first execute API delta in `specs/002-trader-orders-views/contracts/openapi.yaml`.

## ADDED Requirements

### Requirement: Reject execute when institution catalog is empty

Before validating a specific institution, the system SHALL reject execute when no institution row exists in the catalog (strict execute cold start, mirror currencies discipline for reference data setup).

#### Scenario: Empty catalog rejects execute

- **WHEN** the assigned trader submits a valid execute request and the institution catalog is empty
- **THEN** the system rejects execute with a clear error indicating institutions must be onboarded first
- **AND** the order remains in ASSIGNED status

---

### Requirement: Execute requires active onboarded institutionCode

On execute, the trader request SHALL include **`institutionCode`** referencing a catalog row. The system MUST reject execute when the code is unknown or the institution is inactive. The system MUST NOT accept free-text **counterparty** on the execute request as the source of truth for catalog validation.

#### Scenario: Successful execute with institutionCode

- **WHEN** the assigned trader executes with `institutionCode` `HSBC-01` for an active institution with `displayName` `HSBC`
- **THEN** the order transitions to EXECUTED
- **AND** persisted execution **counterparty** equals `HSBC` (the institution `displayName`)

#### Scenario: Unknown institutionCode rejects execute

- **WHEN** the assigned trader executes with `institutionCode` `NOPE-01` that does not exist
- **THEN** the system rejects execute with a clear institution error
- **AND** the order remains ASSIGNED

#### Scenario: Inactive institution rejects execute

- **WHEN** `HSBC-01` is inactive and the assigned trader executes with `institutionCode` `HSBC-01`
- **THEN** the system rejects execute with a clear institution error
- **AND** the order remains ASSIGNED

#### Scenario: Missing institutionCode rejects execute

- **WHEN** the assigned trader submits execute without `institutionCode`
- **THEN** the system rejects execute with a clear validation error

---

### Requirement: Execute API is contract-first with institutionCode

The canonical OpenAPI for trader execute (`specs/002-trader-orders-views/contracts/openapi.yaml`) SHALL define **`institutionCode`** as required on the execute request body. Prose mirror `api-v1.md` SHALL match. Generated server interfaces and Angular clients MUST align with the published contract in the same delivery.

#### Scenario: OpenAPI documents required institutionCode

- **WHEN** a consumer reads the execute operation schema
- **THEN** `institutionCode` is required and `counterparty` is not a client-supplied execute input field

---

### Requirement: Execute UI selects institution from catalog

On the order details execute action, the trader application SHALL provide **autocomplete** (or equivalent constrained picker) over **active** institutions: visible label **`displayName`**, submitted value **`institutionCode`**. The UI MUST NOT offer unconstrained free-text counterparty entry for execute.

#### Scenario: Autocomplete shows active institutions only

- **WHEN** the assignee opens execute on an ASSIGNED order and `HSBC-01` is active and `BCI-01` is inactive
- **THEN** the picker includes `HSBC-01` (by display name) and excludes inactive `BCI-01`

#### Scenario: Execute disabled when catalog empty

- **WHEN** the assignee opens execute and the institution catalog is empty
- **THEN** execute is not submittable and the trader is directed to onboard institutions in Settings

#### Scenario: Submit sends institutionCode

- **WHEN** the assignee selects institution `HSBC` (`HSBC-01`) and confirms execute
- **THEN** the client calls the execute API with `institutionCode` `HSBC-01` and not a free-text counterparty field

---

### Requirement: Intake unchanged for institutions in phase 1

Portfolio Management intake (`POST /api/v1/orders`) SHALL NOT require an institution field in this capability. Institution rules apply at execute only.

#### Scenario: Intake succeeds without institution catalog

- **WHEN** Portfolio Management submits a structurally valid order and the institution catalog is empty but currency rules pass
- **THEN** intake succeeds and the order is persisted (execute remains blocked until institutions exist)
