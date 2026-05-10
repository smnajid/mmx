## ADDED Requirements

### Requirement: Received default near-term window

The system SHALL, for each workspace Received list (`OrderType` **Term** or **OnCall**), restrict rows in the **default** mode to orders in `OrderStatus.RECEIVED` whose **scheduling date** (`valueDate`) falls within **today and the following two calendar days**, inclusive, using the **business calendar timezone** applied consistently to Money Market order dates in this codebase.

#### Scenario: Default mode excludes far-dated received orders

- **WHEN** the trader loads Received for Term or OnCall with **default** (near-term) mode
- **THEN** every row has `valueDate` within the inclusive three-calendar-day window from “today” in the business timezone

#### Scenario: Default mode includes in-window orders

- **WHEN** at least one `RECEIVED` order has `valueDate` inside the window and another outside
- **THEN** default mode returns the in-window order and excludes the outside order (subject to pagination)

---

### Requirement: Session-scoped show all for Received

The system SHALL expose an explicit trader control that requests **all** `RECEIVED` orders for the active workspace type regardless of the near-term window. This mode SHALL apply only for the **current authenticated session** (or equivalent SPA session lifetime): a **new** session SHALL start again in **default** near-term mode without reading any stored user preference.

#### Scenario: Show all reveals out-of-window orders

- **WHEN** the trader enables “show all” (or equivalent) on Received for that workspace
- **THEN** the list includes `RECEIVED` orders for that `OrderType` with `valueDate` outside the default window as well as inside it

#### Scenario: Turning off restores window

- **WHEN** the trader disables show all after enabling it
- **THEN** the list returns to default near-term behaviour

#### Scenario: New session resets funnel

- **WHEN** the authenticated session ends and the trader starts a new session
- **THEN** Received loads in default near-term mode until they enable show all again

---

### Requirement: Workspace switch preserves session mode

Switching workspace (**Term** ↔ **OnCall**) SHALL **not** reset the Received session mode (default vs show all): the **same** mode SHALL apply immediately to the other workspace’s Received dataset.

#### Scenario: Toggle survives workspace change

- **WHEN** show all is enabled on Term Received and the trader switches to OnCall Received
- **THEN** OnCall Received is still in show-all mode until the trader turns it off
