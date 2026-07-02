# institution-onboarding Specification

## Purpose

Trader-maintained catalog of onboarded **institutions** (banks) for desk settings, future rate tables, and execution selection. Exposed via contract-first REST under `contracts/004-institution-settings/openapi.yaml` and Angular settings UI under `/settings/institutions`. **Institution** is the Settings vocabulary; **counterparty** remains the order/execution field (see `order-institution-constraints`).

## Requirements

### Requirement: List onboarded institutions

The system SHALL expose an authenticated operation to list institutions in the catalog visible to the caller's active scope. Each list entry SHALL include `institutionCode`, `displayName`, and `active`. A **Trader** on a **TradingHub** SHALL see the hub's **native** institutions. A **ClientRepresentative** on a **TradingClient** SHALL see only that TradingClient's **thin-proxy** institutions (per `delegated-institution-grants`); native hub institutions SHALL NOT appear in a client's list.

#### Scenario: Empty catalog on cold start

- **WHEN** no institution has been onboarded and the user requests the institution list
- **THEN** the system returns an empty list with HTTP success

#### Scenario: Trader list returns native hub institutions

- **WHEN** `HSBC-01` and `BCI-01` exist as native institutions on hub `LOC` and a Trader on `LOC` requests the list
- **THEN** the list contains both native entries with `institutionCode`, `displayName`, and `active`

#### Scenario: Client list returns only proxies

- **WHEN** `PAR` holds proxy `BNP via LOC` and a `ClientRepresentative` on `PAR` requests the list
- **THEN** the list contains `PAR`'s proxy entries only and does not include `LOC`'s native institutions

---

### Requirement: Onboard an institution with system-generated institutionCode

The system SHALL allow an authorised user to onboard an institution by supplying **`displayName`** only (non-blank, trimmed). The system MUST NOT accept `institutionCode` from the client on create. On success, the system SHALL generate a unique immutable **`institutionCode`** in the form **`{ACRONYM}-{nn}`** (e.g. `HSBC-01`, `BCI-02`), persist the institution as **active**, and return the full entry including the generated code.

**Acronym rules:** single word 2–6 alphanumeric characters → uppercased token as acronym; multiple words → first alphanumeric character of each word, uppercased, max 6 characters; if acronym would be empty → base `INST`. **Suffix:** for a given acronym base, assign the next two-digit suffix `01`–`99` among existing codes with that base; reject onboard if suffix would exceed `99`.

**Role-qualified onboarding (per `delegated-institution-grants`):** a user with the **Trader** role on a **TradingHub** SHALL onboard a **native** institution by supplying `displayName` as above. A user with the **ClientRepresentative** role on a **TradingClient** SHALL onboard a **thin-proxy** institution by selecting an active delegated grant they hold; the system SHALL derive the proxy `displayName` as "{hub institution displayName} via {hubLegalEntityCode}", generate the proxy `institutionCode` by applying the acronym rules to that derived name, and persist the proxy referencing `(hubLegalEntityCode, hubInstitutionCode)`. A ClientRepresentative SHALL NOT supply a free-form `displayName` for a proxy, and a proxy SHALL NOT be created unless an active grant exists for that `(hubInstitution, client)` for at least one currency.

#### Scenario: Successful onboard single-word name

- **WHEN** a TradingHub Trader submits `displayName` `HSBC` and no institution with base `HSBC` exists
- **THEN** the system persists `institutionCode` `HSBC-01`, `active` true, and returns HTTP 201 with the entry

#### Scenario: Second institution same acronym base

- **WHEN** `HSBC-01` already exists and a TradingHub Trader submits `displayName` `HSBC` again
- **THEN** the system persists `institutionCode` `HSBC-02` and returns HTTP 201

#### Scenario: Multi-word display name derives acronym

- **WHEN** a TradingHub Trader submits `displayName` `Bank Co International` and no `BCI-%` codes exist
- **THEN** the system persists `institutionCode` `BCI-01`

#### Scenario: Reject blank display name

- **WHEN** a TradingHub Trader submits an empty or whitespace-only `displayName`
- **THEN** the system rejects the request with a clear validation error and does not persist

#### Scenario: Reject suffix overflow for acronym base

- **WHEN** institutions `INST-01` through `INST-99` already exist and another onboard derives acronym base `INST`
- **THEN** the system rejects onboard with a clear error and does not persist

#### Scenario: ClientRepresentative onboards a proxy from a grant

- **WHEN** a `ClientRepresentative` on `PAR` selects an active grant `(BNP, PAR, EUR)` and onboards a proxy for hub institution `BNP` (displayName `BNP`) whose hub is `LOC`
- **THEN** the system persists a proxy with derived `displayName` `BNP via LOC`, a generated `institutionCode`, and a reference to `(LOC, BNP)`

#### Scenario: ClientRepresentative proxy without a grant rejected

- **WHEN** a `ClientRepresentative` on `PAR` attempts to onboard a proxy for `BNP` while no active grant `(BNP, PAR, *)` exists
- **THEN** the system rejects the request and no proxy is persisted

#### Scenario: ClientRepresentative cannot supply a free-form proxy name

- **WHEN** a `ClientRepresentative` onboards a proxy and submits a free-form `displayName`
- **THEN** the system rejects the request and the proxy `displayName` is derived only from the grant

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

---

### Requirement: ClientRepresentative institution settings surface

A `ClientRepresentative` on a TradingClient SHALL access institutions under `/settings/institutions` (Settings only, per `trader-desk-navigation` role gating). The client institution screen SHALL list only the client's thin-proxy institutions and SHALL allow enabling tenors (Term) and notice periods (OnCall) only within the enabled set of an active grant for `(hubInstitution, client, currency)`. Controls for tenors or notice periods outside the grant's enabled set SHALL be disabled or hidden. Currencies, Term rates, and OnCall rates screens SHALL render read-only for a `ClientRepresentative` (per `delegated-institution-grants` reference-data ownership).

#### Scenario: Client sees only proxies and granted toggles

- **WHEN** a `ClientRepresentative` on `PAR` opens `/settings/institutions` and holds grant `(BNP, PAR, EUR)` with `enabledTenors` `["1M","3M"]`
- **THEN** the screen lists `PAR`'s proxy `BNP via LOC` and allows enabling only `1M` and `3M` for `EUR`; other tenors are disabled/hidden

#### Scenario: Client currency and rate screens are read-only

- **WHEN** a `ClientRepresentative` on `PAR` opens the Currencies, Term rates, or OnCall rates settings screens
- **THEN** the screens render read-only with no mutate controls
