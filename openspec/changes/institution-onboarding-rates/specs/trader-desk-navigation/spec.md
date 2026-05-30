# trader-desk-navigation Specification (delta)

## MODIFIED Requirements

### Requirement: Navigation sits below brand header

The trader application SHALL expose desk navigation as two visible tab tiers placed in the **main content area** below the application header (not in the header): (**1**) primary tabs **ON-CALL** and **Term**, and (**2**) sub-tabs **Received**, **Assigned**, and **Executed** scoped to the active primary tab. Primary tab labels SHALL NOT include the word “Workspace”. Routing SHALL continue to use paths `/oncall/{queue}` and `/term/{queue}` with `queue` ∈ {`received`, `assigned`, `executed`}. **Received** routes SHALL load the shared Received list shell (same structural pattern as Executed thin wrappers).

The application header (brand and trader identity row) SHALL contain **Desk** and **Settings** entries for navigation between desk queues and the settings area. Desk primary and sub-tabs SHALL remain in the main content area only and MUST NOT be moved into the header.

#### Scenario: Primary tabs use ON-CALL and Term labels

- **WHEN** the trader views any desk queue screen
- **THEN** the primary tab strip shows **ON-CALL** and **Term** and does not show “Workspace” in tab labels

#### Scenario: Sub-tabs visible under active primary

- **WHEN** the trader is on `/term/assigned`
- **THEN** **Term** is the active primary tab and **Assigned** is the active sub-tab, and **Received** and **Executed** sub-tabs remain visible

#### Scenario: Navigation sits below brand header

- **WHEN** the trader loads the application shell on a desk queue route
- **THEN** the top header contains brand, trader identity, and **Desk** / **Settings** navigation entries, and both desk tab tiers appear at the top of the main content region above the queue list

#### Scenario: Received routes use shared list shell

- **WHEN** the trader selects **Term** then **Received**
- **THEN** routing resolves to `/term/received` using the shared Received list component for the Term workspace (not a standalone duplicate Term-only list implementation)

---

### Requirement: Header provides Desk and Settings navigation

The application header SHALL expose two trader-facing navigation entries: one that returns to the **desk** (order queues) and one that opens **settings**. Both SHALL use consistent header styling (typography, colour, hover, active state) aligned with the MMx shell. On any route under `/settings`, the **Settings** entry SHALL appear active; on desk queue routes, the **Desk** entry SHALL appear active. The header MUST NOT show a separate **Currencies** link (currencies are reached via the Settings hub).

#### Scenario: Desk link visible on settings

- **WHEN** the trader is on `/settings/institutions` or `/settings/currencies`
- **THEN** a **Desk** header entry is visible and navigates away from settings

#### Scenario: Settings link active on settings routes

- **WHEN** the trader is on any `/settings` route (including `/settings/currencies` or `/settings/institutions`)
- **THEN** the **Settings** header entry is visually indicated as active

#### Scenario: Desk link active on queue routes

- **WHEN** the trader is on `/term/received`
- **THEN** the **Desk** header entry is visually indicated as active and **Settings** is not active

---

### Requirement: Return from settings restores desk context

When the trader navigates from a desk queue screen to settings, the application SHALL remember that desk URL. Activating the **Desk** header entry or an explicit **Back to desk** control on a settings list SHALL navigate to the remembered URL. If no desk URL was remembered, navigation SHALL fall back to `/oncall/received`.

#### Scenario: Return to Term Assigned after visiting settings

- **WHEN** the trader was on `/term/assigned`, opened **Settings** from the header, then activates **Desk**
- **THEN** the application navigates to `/term/assigned`

#### Scenario: Default desk when no prior queue

- **WHEN** the trader opens Settings directly (no prior desk route in the session)
- **THEN** activating **Desk** navigates to `/oncall/received`

#### Scenario: Institution list offers back to desk

- **WHEN** the trader views the institution settings list
- **THEN** a visible **Back to desk** control (and/or **Desk** header) returns to the desk without requiring browser back

---

### Requirement: Settings routes hide desk queue tabs

When the trader is on any settings route (`/settings` and children, including currencies and institutions), the application SHALL hide the two-tier desk queue tab strip. Opening settings MUST NOT change ON-CALL or Term primary tab routing on desk routes.

#### Scenario: Desk tabs hidden on institution settings

- **WHEN** the trader is on the institution settings list or onboard screen
- **THEN** ON-CALL and Term desk queue tabs are not shown

#### Scenario: Desk tabs hidden on currency settings

- **WHEN** the trader is on the currency settings list or edit screen
- **THEN** ON-CALL and Term desk queue tabs are not shown

#### Scenario: Desk queue routes retain two-tier tabs

- **WHEN** the trader navigates to `/oncall/received` or `/term/assigned`
- **THEN** the two-tier desk tab model behaves as on desk queue screens

## ADDED Requirements

### Requirement: Settings hub sub-navigation for catalog areas

Within settings, the application SHALL expose sub-navigation for reference-data areas. In phase 1 of the institution programme, sub-nav SHALL include **Currencies** and **Institutions**. Selecting a section SHALL route to `/settings/currencies` or `/settings/institutions` respectively without leaving the settings shell.

#### Scenario: Sub-nav visible on institution list

- **WHEN** the trader is on `/settings/institutions`
- **THEN** settings sub-navigation shows **Currencies** and **Institutions** with **Institutions** indicated as active

#### Scenario: Navigate from currencies to institutions

- **WHEN** the trader is on `/settings/currencies` and selects **Institutions** in sub-nav
- **THEN** routing navigates to `/settings/institutions`

#### Scenario: Default settings entry opens currencies

- **WHEN** the trader activates **Settings** from the header without a prior settings path
- **THEN** the application navigates to `/settings` or `/settings/currencies` per product default (documented in quickstart)
