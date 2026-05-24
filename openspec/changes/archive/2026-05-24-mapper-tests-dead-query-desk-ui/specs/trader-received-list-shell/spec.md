# trader-received-list-shell Specification (delta)

## ADDED Requirements

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
