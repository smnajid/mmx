# trader-desk-navigation Specification

## Purpose

Trader-facing desk navigation: two-tier tabs (**ON-CALL** / **Term** primary, **Received** / **Assigned** / **Executed** sub-tabs) in the main content area, with routing and highlight behaviour aligned to feature 002.

## Requirements

### Requirement: Two-tier desk tabs in main content

The trader application SHALL expose desk navigation as two visible tab tiers placed in the **main content area** below the application header (not in the header): (**1**) primary tabs **ON-CALL** and **Term**, and (**2**) sub-tabs **Received**, **Assigned**, and **Executed** scoped to the active primary tab. Primary tab labels SHALL NOT include the word “Workspace”. Routing SHALL continue to use paths `/oncall/{queue}` and `/term/{queue}` with `queue` ∈ {`received`, `assigned`, `executed`}. **Received** routes SHALL load the shared Received list shell (same structural pattern as Executed thin wrappers).

#### Scenario: Primary tabs use ON-CALL and Term labels

- **WHEN** the trader views any desk queue screen
- **THEN** the primary tab strip shows **ON-CALL** and **Term** and does not show “Workspace” in tab labels

#### Scenario: Sub-tabs visible under active primary

- **WHEN** the trader is on `/term/assigned`
- **THEN** **Term** is the active primary tab and **Assigned** is the active sub-tab, and **Received** and **Executed** sub-tabs remain visible

#### Scenario: Navigation sits below brand header

- **WHEN** the trader loads the application shell
- **THEN** the top header contains brand and trader identity only, and both tab tiers appear at the top of the main content region above the queue list

#### Scenario: Received routes use shared list shell

- **WHEN** the trader selects **Term** then **Received**
- **THEN** routing resolves to `/term/received` using the shared Received list component for the Term workspace (not a standalone duplicate Term-only list implementation)

---

### Requirement: Dual-tier active indication on all desk queues

On every desk queue screen (Received, Assigned, Executed for the active primary tab), the UI SHALL visually distinguish **both** the active primary tab (**ON-CALL** or **Term**) **and** the active sub-tab. This SHALL apply when the trader navigates directly to Assigned or Executed, not only from Received.

#### Scenario: Assigned shows both tiers active

- **WHEN** the trader opens `/oncall/assigned`
- **THEN** **ON-CALL** and **Assigned** appear as the selected tabs

#### Scenario: Executed on Term shows both tiers active

- **WHEN** the trader opens `/term/executed`
- **THEN** **Term** and **Executed** appear as the selected tabs

---

### Requirement: Order details preserve tab context

When the trader opens order details from a queue list via **View**, the same primary tab and sub-tab SHALL remain visually indicated as active. The back control SHALL return to that queue. When order details are opened without `ws` and `queue` query parameters, no queue-specific tab selection is required until the trader navigates; back MAY fall back to default desk entry (**ON-CALL** > **Received**).

#### Scenario: Details from Term Assigned keep highlight

- **WHEN** the trader opens order details from `/term/assigned` (including via **View** with `?ws=term&queue=assigned`)
- **THEN** **Term** and **Assigned** remain active in desk navigation and back returns to Term Assigned

#### Scenario: Details without context do not force selection

- **WHEN** the trader opens `/orders/{id}` without `ws` or `queue` query parameters
- **THEN** neither primary nor sub-tab is required to show as selected

---

### Requirement: Default primary tab ON-CALL per session

Each new authenticated session (or default application entry) SHALL open with **ON-CALL** as the active primary tab. Primary tab choice SHALL NOT persist across logout or session boundary. A full page reload SHALL return to the default route mapping (**ON-CALL** > **Received** unless the URL path specifies otherwise).

#### Scenario: Fresh session lands on ON-CALL Received

- **WHEN** the trader starts a new session and opens the application root
- **THEN** the active primary tab is **ON-CALL** and the default sub-tab is **Received**

#### Scenario: Prior session Term not restored

- **WHEN** the trader used **Term** in a prior session and starts a new session
- **THEN** the desk opens on **ON-CALL** (not **Term**)

---

### Requirement: Responsive desk tabs on narrow viewports

On viewports where horizontal space is insufficient to show all tabs on one line, each tab tier SHALL remain visible and usable via **horizontal scrolling** within its row. Sub-tabs SHALL NOT be collapsed behind a menu or hidden until a primary tab is chosen. Tab controls SHALL meet a minimum touch-friendly height (approximately 44 CSS pixels).

#### Scenario: Narrow viewport keeps both tab rows

- **WHEN** the viewport width is 320px and the trader is on `/oncall/executed`
- **THEN** both primary and sub-tab rows are present and **Executed** can be reached without losing the sub-tab row

#### Scenario: Sub-tabs not hidden on mobile

- **WHEN** the viewport is below the desktop breakpoint
- **THEN** Received, Assigned, and Executed sub-tabs remain visible (not moved exclusively into an overflow menu)
