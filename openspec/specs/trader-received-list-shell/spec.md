# trader-received-list-shell Specification

## Purpose

Trader SPA structure for **Received** desk queues: one workspace-parameterized list component (Term / OnCall), aligned with Executed and Assigned shell patterns.

## Requirements

### Requirement: Single Received list component per workspace pattern

The trader SPA SHALL implement Received queues using one shared list component parameterized by workspace (**Term** or **OnCall**), matching the Executed desk pattern (shared component + route shell or `data.workspace`).

#### Scenario: Term Received uses shared component

- **WHEN** the trader navigates to `/term/received`
- **THEN** the UI renders the shared Received list configured for Term (`OrderType` Term API operation, tenor column visible)

#### Scenario: OnCall Received uses shared component

- **WHEN** the trader navigates to `/oncall/received`
- **THEN** the UI renders the shared Received list configured for OnCall (notice period column visible)

### Requirement: Received behaviour unchanged

The shared Received list SHALL preserve existing trader-visible behaviour from `trader-received-queue`:

- Default near-term window vs session **show all** toggle (`ReceivedViewModeService`).
- Assign affordance on rows.
- Refresh and error handling equivalent to prior Term/OnCall list screens.

#### Scenario: Show all toggle still applies

- **WHEN** the trader enables show all on Received for the active workspace
- **THEN** subsequent list requests use the all `receivedView` mode until toggled off in the session

#### Scenario: No mixed workspaces on one surface

- **WHEN** the trader views Received under Term
- **THEN** only Term received API list operation is called (not OnCall)

### Requirement: Tenor and notice period on Assigned and Executed queues

For **Term** rows on Assigned and Executed list surfaces, **Tenor** SHALL be visible in the list or reachable in one obvious step without leaving context. For **OnCall** rows on Assigned and Executed list surfaces, **Notice period** SHALL be visible under the same rule. REST `OrderSummaryResponse` SHALL expose `tenor` for Term and `noticePeriod` for OnCall consistent with Received behaviour.

#### Scenario: Term Assigned list shows tenor column

- **WHEN** the trader views `/term/assigned`
- **THEN** each row exposes the order's tenor

#### Scenario: OnCall Executed list shows notice period

- **WHEN** the trader views `/oncall/executed`
- **THEN** each row exposes the order's notice period

#### Scenario: Order detail shows workspace-appropriate scheduling field

- **WHEN** the trader opens order detail for a Term order from Assigned or Executed
- **THEN** tenor is visible without navigating away from the detail screen
