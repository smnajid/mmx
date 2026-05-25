# trader-desk-navigation Specification (delta)

## MODIFIED Requirements

### Requirement: Two-tier desk tabs in main content

The trader application SHALL expose desk navigation as two visible tab tiers placed in the **main content area** below the application header (not in the header): (**1**) primary tabs **ON-CALL** and **Term**, and (**2**) sub-tabs **Received**, **Assigned**, and **Executed** scoped to the active primary tab. Primary tab labels SHALL NOT include the word “Workspace”. Routing SHALL continue to use paths `/oncall/{queue}` and `/term/{queue}` with `queue` ∈ {`received`, `assigned`, `executed`}. **Received** routes SHALL load the shared Received list shell (same structural pattern as Executed thin wrappers).

The application header (brand and trader identity row) MAY additionally contain a **settings** entry for currency configuration. Desk primary and sub-tabs SHALL remain in the main content area only and MUST NOT be moved into the header.

#### Scenario: Primary tabs use ON-CALL and Term labels

- **WHEN** the trader views any desk queue screen
- **THEN** the primary tab strip shows **ON-CALL** and **Term** and does not show “Workspace” in tab labels

#### Scenario: Sub-tabs visible under active primary

- **WHEN** the trader is on `/term/assigned`
- **THEN** **Term** is the active primary tab and **Assigned** is the active sub-tab, and **Received** and **Executed** sub-tabs remain visible

#### Scenario: Navigation sits below brand header

- **WHEN** the trader loads the application shell on a desk queue route
- **THEN** the top header contains brand, trader identity, and the settings entry, and both desk tab tiers appear at the top of the main content region above the queue list

#### Scenario: Received routes use shared list shell

- **WHEN** the trader selects **Term** then **Received**
- **THEN** routing resolves to `/term/received` using the shared Received list component for the Term workspace (not a standalone duplicate Term-only list implementation)

---

## ADDED Requirements

### Requirement: Settings entry reaches currency configuration

The trader application SHALL provide a visible settings control in the application header that navigates to the managed currency settings area (list and edit flows per `managed-currency-settings`). Opening settings MUST NOT change ON-CALL or Term primary tab routing; returning from settings MUST restore the prior desk context when the trader uses browser back or an explicit back control.

#### Scenario: Open currencies from header

- **WHEN** the trader activates the header settings entry
- **THEN** the application navigates to the currency settings list route

#### Scenario: Desk tabs unchanged on settings route

- **WHEN** the trader is on the currency settings list or edit screen
- **THEN** ON-CALL and Term desk queue tabs are not required to appear selected as active desk queues

#### Scenario: Desk queue routes retain two-tier tabs

- **WHEN** the trader navigates to `/oncall/received` or `/term/assigned`
- **THEN** the two-tier desk tab model behaves as before this change
