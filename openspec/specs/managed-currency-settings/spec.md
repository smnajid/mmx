# managed-currency-settings Specification

## Purpose

Trader-maintained money-market currency catalog and per-currency operational rules (enabled tenors, enabled notice periods, minimum amounts). Exposed via contract-first REST under `contracts/003-managed-currency-settings/openapi.yaml` and Angular settings UI.

## Requirements

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

The system SHALL allow a trader to add a currency to the catalog by ISO 4217 code (three uppercase letters). On create, the system SHALL require initial values for minimum subscription amount, minimum increase/decrease amount, and at least one workspace enabled (non-empty `enabledTenors` for Term **or** non-empty `enabledNoticePeriods` for OnCall). All minimum amounts MUST be greater than zero.

#### Scenario: Successful onboard

- **WHEN** the trader submits a valid new currency code `CHF` with valid minimums and at least one workspace enabled
- **THEN** the currency is persisted, marked active, and returned with HTTP success

#### Scenario: Reject invalid ISO code

- **WHEN** the trader submits a code that is not a valid ISO 4217 alphabetic code (e.g. `EURO`, `12`)
- **THEN** the system rejects the request with a clear validation error and does not persist the currency

#### Scenario: Reject duplicate currency

- **WHEN** the trader submits a code that already exists in the catalog
- **THEN** the system rejects the request with a clear duplicate error and does not create a second row

---

### Requirement: Update per-currency rules

The system SHALL allow a trader to update enabled tenors, enabled notice periods, and minimum amounts for an existing managed currency. The set of configurable tenor codes SHALL be exactly `1W`, `2W`, `1M`, `3M`, `6M`, `1Y`. The set of configurable notice period codes SHALL be exactly `24H`, `48H`. PATCH updates: omit a field to leave it unchanged; send `[]` to clear that workspace's enabled set.

#### Scenario: Update minimum amounts

- **WHEN** the trader patches minimum subscription or increase/decrease amounts to valid positive decimals
- **THEN** the persisted configuration reflects the new values

#### Scenario: Enable and disable tenors

- **WHEN** the trader toggles individual tenor flags while at least one workspace remains enabled
- **THEN** the persisted configuration reflects the requested enabled set

#### Scenario: Enable and disable notice periods

- **WHEN** the trader toggles individual notice period flags while at least one workspace remains enabled
- **THEN** the persisted configuration reflects the requested enabled set

---

### Requirement: Block disabling the last enabled tenor or notice period

The system SHALL allow a managed currency to have **zero** enabled tenors (Term workspace off) or **zero** enabled notice periods (OnCall workspace off). The system SHALL reject onboard and rule updates that would leave **both** workspaces empty (no enabled tenors **and** no enabled notice periods). The trader UI SHALL prevent toggling off the last enabled control in a workspace **only when** the other workspace would also become empty.

#### Scenario: Term-only currency saves successfully

- **WHEN** the trader saves EUR with at least one enabled tenor and zero enabled notice periods
- **THEN** the configuration persists and HTTP success is returned

#### Scenario: API rejects both workspaces empty

- **WHEN** the trader attempts to save a currency with zero enabled tenors and zero enabled notice periods
- **THEN** the system rejects the update with a clear validation error

#### Scenario: UI blocks last tenor only when OnCall also empty

- **WHEN** the trader edits a currency with no enabled notice periods and a single enabled tenor
- **THEN** the UI prevents disabling that last tenor

#### Scenario: UI allows clearing all notices when tenors remain

- **WHEN** the trader edits a currency with at least one enabled tenor
- **THEN** the UI allows disabling all notice period controls

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

### Requirement: Intake respects workspace-enabled sets

When a managed currency is **active**, Term order intake SHALL be allowed only if the order tenor is in `enabledTenors` (which may be non-empty while notice periods are empty). OnCall order intake SHALL be allowed only if the order notice period is in `enabledNoticePeriods` (which may be non-empty while tenors are empty). For an order owned by a **TradingClient**, the enabled-set check SHALL be performed against the connected **TradingHub's** managed currency (the client reads the hub's catalog per `delegated-institution-grants`); the tenor/notice MUST additionally be within the relevant delegated grant's enabled set.

#### Scenario: Term intake with Term-only currency

- **WHEN** EUR is active with enabled tenor `3M` and no enabled notice periods
- **THEN** a new Term order for EUR with tenor `3M` passes currency policy

#### Scenario: OnCall intake rejected for Term-only currency

- **WHEN** EUR is active with enabled tenors and no enabled notice periods
- **THEN** a new OnCall order for EUR is rejected by currency policy

#### Scenario: TradingClient intake validates against the hub's currency set

- **WHEN** a `PAR` order for `EUR` with tenor `3M` is submitted and `PAR`'s connected hub `LOC` has `EUR` active with `enabledTenors` including `3M`
- **THEN** the currency policy check uses `LOC`'s managed `EUR` configuration and the order passes that check (subject to the grant check)

---

### Requirement: Trader settings UI for currencies

The Angular application SHALL provide a currency settings area reachable without using ON-CALL or Term desk queue tabs. The UI SHALL support listing currencies, onboarding a new currency, editing rules for an existing currency, deactivating a currency, and reactivating an inactive currency. Forms SHALL mirror API validation including the workspace coverage guard (at least one of Term tenors or OnCall notice periods enabled).

#### Scenario: Navigate to currency list

- **WHEN** the trader follows the shell settings entry to currencies
- **THEN** the currency list screen is shown

#### Scenario: Edit currency rules

- **WHEN** the trader opens a currency from the list and saves valid rule changes
- **THEN** the UI reflects persisted values after a successful API response

#### Scenario: UI blocks last tenor toggle

- **WHEN** the currency has only one enabled tenor and no enabled notice periods
- **THEN** that tenor checkbox cannot be turned off

---

### Requirement: Contract-first settings REST API

The currency settings HTTP surface SHALL be defined in `contracts/003-managed-currency-settings/openapi.yaml` with a prose mirror in `api-v1.md`. The `mmx-adapter-in-rest` module SHALL implement generated API interfaces; runtime request and response shapes MUST match the published contract.

#### Scenario: Implementation follows OpenAPI

- **WHEN** a client calls documented settings endpoints with valid payloads
- **THEN** server behaviour and response schemas match the canonical OpenAPI document

---

### Requirement: Managed currencies are hub-owned and client-read-only

Managed currencies SHALL be owned and managed by the TradingHub LegalEntity. A user with the Trader role on a TradingHub SHALL onboard, update, deactivate, and reactivate managed currencies. A `ClientRepresentative` on a TradingClient SHALL read the connected hub's managed currency catalog (in-process, same deployment) and SHALL NOT create, update, deactivate, or reactivate currencies. A TradingClient's intake currency and workspace-enabled validation SHALL use the connected hub's managed currency set.

#### Scenario: ClientRepresentative reads the hub currency catalog

- **WHEN** a `ClientRepresentative` on `PAR` requests the managed currency list
- **THEN** the system returns `LOC`'s managed currency catalog read-only

#### Scenario: ClientRepresentative cannot mutate a managed currency

- **WHEN** a `ClientRepresentative` on `PAR` attempts to onboard, update, deactivate, or reactivate a managed currency
- **THEN** the system rejects the request with an authorisation error and no currency is changed
