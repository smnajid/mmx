# institution-onboarding Specification

## Purpose

Trader-maintained catalog of onboarded **institutions** (banks) for desk settings, future rate tables, and execution selection. Exposed via contract-first REST under `contracts/004-institution-settings/openapi.yaml` and Angular settings UI under `/settings/institutions`. **Institution** is the Settings vocabulary; **counterparty** remains the order/execution field (see `order-institution-constraints`).

## Requirements

### Requirement: List onboarded institutions

The system SHALL expose an authenticated trader operation to list all institutions in the catalog. Each list entry SHALL include `institutionCode`, `displayName`, and `active`.

#### Scenario: Empty catalog on cold start

- **WHEN** no institution has been onboarded and the trader requests the institution list
- **THEN** the system returns an empty list with HTTP success

#### Scenario: List returns onboarded institutions

- **WHEN** `HSBC-01` and `BCI-01` exist in the catalog
- **THEN** the list contains both entries with `institutionCode`, `displayName`, and `active`

---

### Requirement: Onboard an institution with system-generated institutionCode

The system SHALL allow a trader to onboard an institution by supplying **`displayName`** only (non-blank, trimmed). The system MUST NOT accept `institutionCode` from the client on create. On success, the system SHALL generate a unique immutable **`institutionCode`** in the form **`{ACRONYM}-{nn}`** (e.g. `HSBC-01`, `BCI-02`), persist the institution as **active**, and return the full entry including the generated code.

**Acronym rules:** single word 2–6 alphanumeric characters → uppercased token as acronym; multiple words → first alphanumeric character of each word, uppercased, max 6 characters; if acronym would be empty → base `INST`. **Suffix:** for a given acronym base, assign the next two-digit suffix `01`–`99` among existing codes with that base; reject onboard if suffix would exceed `99`.

#### Scenario: Successful onboard single-word name

- **WHEN** the trader submits `displayName` `HSBC` and no institution with base `HSBC` exists
- **THEN** the system persists `institutionCode` `HSBC-01`, `active` true, and returns HTTP 201 with the entry

#### Scenario: Second institution same acronym base

- **WHEN** `HSBC-01` already exists and the trader submits `displayName` `HSBC` again
- **THEN** the system persists `institutionCode` `HSBC-02` and returns HTTP 201

#### Scenario: Multi-word display name derives acronym

- **WHEN** the trader submits `displayName` `Bank Co International` and no `BCI-%` codes exist
- **THEN** the system persists `institutionCode` `BCI-01`

#### Scenario: Reject blank display name

- **WHEN** the trader submits an empty or whitespace-only `displayName`
- **THEN** the system rejects the request with a clear validation error and does not persist

#### Scenario: Reject suffix overflow for acronym base

- **WHEN** institutions `INST-01` through `INST-99` already exist and another onboard derives acronym base `INST`
- **THEN** the system rejects onboard with a clear error and does not persist

---

### Requirement: Get institution by institutionCode

The system SHALL allow a trader to retrieve a single catalog entry by `institutionCode`.

#### Scenario: Known code returns entry

- **WHEN** the trader requests `GET` for `institutionCode` `HSBC-01` that exists
- **THEN** the system returns the institution with HTTP success

#### Scenario: Unknown code returns not found

- **WHEN** the trader requests an `institutionCode` that does not exist
- **THEN** the system returns HTTP not found

---

### Requirement: Deactivate and activate institutions

The system SHALL allow a trader to deactivate an institution (`active` false) and later reactivate it (`active` true). The system MUST NOT hard-delete institution rows in this capability.

#### Scenario: Deactivate active institution

- **WHEN** the trader deactivates `HSBC-01` that is active
- **THEN** the institution is persisted with `active` false and returned with HTTP success

#### Scenario: Activate inactive institution

- **WHEN** the trader activates `HSBC-01` that is inactive
- **THEN** the institution is persisted with `active` true and returned with HTTP success

---

### Requirement: Institution settings UI under Settings hub

Institution list and onboard flows SHALL live under `/settings/institutions` (and child routes) within the Settings hub. Screens SHALL use the same return-to-desk and visual patterns as currency settings (`DeskReturnService`, `settings-panel`, desk theme tokens per `currency-settings-ui`).

#### Scenario: List shows code and display name

- **WHEN** the trader opens the institution settings list and institutions exist
- **THEN** each row shows `institutionCode` and `displayName` and an active/inactive indication

#### Scenario: Onboard form has display name only

- **WHEN** the trader opens institution onboard
- **THEN** the form does not allow editing `institutionCode` and submits `displayName` only

#### Scenario: Onboard success shows generated code

- **WHEN** the trader successfully onboards an institution
- **THEN** the UI surfaces the returned `institutionCode` (list refresh or confirmation)
