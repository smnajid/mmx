# managed-currency-settings Specification

## Purpose

Trader-maintained money-market currency catalog and per-currency operational rules (enabled tenors, enabled notice periods, minimum amounts). Exposed via contract-first REST under `specs/003-managed-currency-settings/contracts/openapi.yaml` and Angular settings UI.

## ADDED Requirements

### Requirement: List managed currencies

The system SHALL expose an authenticated trader operation to list all currencies in the managed catalog. Each list entry SHALL include the ISO 4217 code, active flag, enabled tenor codes, enabled notice period codes, minimum subscription amount, and minimum increase/decrease amount.

#### Scenario: Empty catalog on cold start

- **WHEN** no currency has been onboarded and the trader requests the currency list
- **THEN** the system returns an empty list with HTTP success

#### Scenario: List returns onboarded currencies

- **WHEN** EUR and USD are onboarded and active
- **THEN** the list contains both currencies with their current configuration fields

---

### Requirement: Onboard a managed currency

The system SHALL allow a trader to add a currency to the catalog by ISO 4217 code (three uppercase letters). On create, the system SHALL require initial values for minimum subscription amount, minimum increase/decrease amount, at least one enabled tenor, and at least one enabled notice period. All minimum amounts MUST be greater than zero.

#### Scenario: Successful onboard

- **WHEN** the trader submits a valid new currency code `CHF` with valid minimums and at least one tenor and one notice period enabled
- **THEN** the currency is persisted, marked active, and returned with HTTP success

#### Scenario: Reject invalid ISO code

- **WHEN** the trader submits a code that is not a valid ISO 4217 alphabetic code (e.g. `EURO`, `12`)
- **THEN** the system rejects the request with a clear validation error and does not persist the currency

#### Scenario: Reject duplicate currency

- **WHEN** the trader submits a code that already exists in the catalog
- **THEN** the system rejects the request with a clear duplicate error and does not create a second row

---

### Requirement: Update per-currency rules

The system SHALL allow a trader to update enabled tenors, enabled notice periods, and minimum amounts for an existing managed currency. The set of configurable tenor codes SHALL be exactly `1W`, `2W`, `1M`, `3M`, `6M`, `1Y`. The set of configurable notice period codes SHALL be exactly `24H`, `48H`.

#### Scenario: Update minimum amounts

- **WHEN** the trader patches minimum subscription or increase/decrease amounts to valid positive decimals
- **THEN** the persisted configuration reflects the new values

#### Scenario: Enable and disable tenors

- **WHEN** the trader toggles individual tenor flags while at least one tenor remains enabled
- **THEN** the persisted configuration reflects the requested enabled set

#### Scenario: Enable and disable notice periods

- **WHEN** the trader toggles individual notice period flags while at least one notice period remains enabled
- **THEN** the persisted configuration reflects the requested enabled set

---

### Requirement: Block disabling the last enabled tenor or notice period

The system SHALL reject updates that would leave zero enabled tenors or zero enabled notice periods for a managed currency. The trader UI SHALL prevent toggling off the last remaining enabled tenor or notice period control.

#### Scenario: API rejects last tenor disable

- **WHEN** the trader attempts to disable the only enabled tenor for a currency
- **THEN** the system rejects the update with a clear validation error and leaves configuration unchanged

#### Scenario: API rejects last notice period disable

- **WHEN** the trader attempts to disable the only enabled notice period for a currency
- **THEN** the system rejects the update with a clear validation error and leaves configuration unchanged

---

### Requirement: Disable a managed currency

The system SHALL allow a trader to deactivate a managed currency. Deactivation SHALL be permitted even when orders in Received or Assigned status still reference that currency code.

#### Scenario: Disable with open orders

- **WHEN** the trader deactivates EUR while Received orders exist for EUR
- **THEN** the currency is marked inactive and HTTP success is returned

#### Scenario: Deactivated currency remains in list

- **WHEN** a currency is inactive
- **THEN** the currency list still includes the currency with `active` false so traders can review or reactivate configuration

---

### Requirement: Reactivate a managed currency

The system SHALL allow a trader to reactivate a previously deactivated managed currency by setting `active` true. Reactivation SHALL restore intake eligibility for that currency according to its configured rules.

#### Scenario: Reactivate inactive currency

- **WHEN** the trader reactivates EUR while EUR is inactive
- **THEN** the currency is marked active and HTTP success is returned

#### Scenario: Reactivate is idempotent for active currency

- **WHEN** the trader reactivates EUR while EUR is already active
- **THEN** the currency remains active and HTTP success is returned

---

### Requirement: Trader settings UI for currencies

The Angular application SHALL provide a currency settings area reachable without using ON-CALL or Term desk queue tabs. The UI SHALL support listing currencies, onboarding a new currency, editing rules for an existing currency, deactivating a currency, and reactivating an inactive currency. Forms SHALL mirror API validation including last-tenor and last-notice guards.

#### Scenario: Navigate to currency list

- **WHEN** the trader follows the shell settings entry to currencies
- **THEN** the currency list screen is shown

#### Scenario: Edit currency rules

- **WHEN** the trader opens a currency from the list and saves valid rule changes
- **THEN** the UI reflects persisted values after a successful API response

#### Scenario: UI blocks last tenor toggle

- **WHEN** only one tenor is enabled for a currency on the edit screen
- **THEN** that tenor control is not actionable for disable until another tenor is enabled first

---

### Requirement: Contract-first settings REST API

The currency settings HTTP surface SHALL be defined in `specs/003-managed-currency-settings/contracts/openapi.yaml` with a prose mirror in `api-v1.md`. The `mmx-adapter-in-rest` module SHALL implement generated API interfaces; runtime request and response shapes MUST match the published contract.

#### Scenario: Implementation follows OpenAPI

- **WHEN** a client calls documented settings endpoints with valid payloads
- **THEN** server behaviour and response schemas match the canonical OpenAPI document
