# trader-desk-navigation Specification (delta)

## MODIFIED Requirements

### Requirement: Two-tier desk tabs in main content

The trader application SHALL expose desk navigation as two visible tab tiers placed in the **main content area** below the application header (not in the header): (**1**) primary tabs **ON-CALL** and **Term**, and (**2**) sub-tabs **Received**, **Assigned**, and **Executed** scoped to the active primary tab. Primary tab labels SHALL NOT include the word “Workspace”. Routing SHALL continue to use paths `/oncall/{queue}` and `/term/{queue}` with `queue` ∈ {`received`, `assigned`, `executed`}. **Received** routes SHALL load the shared Received list shell (same structural pattern as Executed thin wrappers).

#### Scenario: Primary tabs use ON-CALL and Term labels

- **WHEN** the trader views any desk queue screen
- **THEN** the primary tab labels read **ON-CALL** and **Term** (not “OnCall Workspace” / “Term Workspace”)

#### Scenario: Sub-tabs follow active primary tab

- **WHEN** the trader selects **Term** then **Assigned**
- **THEN** the URL path is under `/term/assigned` and only Term-scoped assigned content is shown

#### Scenario: Received routes use shared list shell

- **WHEN** the trader selects **Term** then **Received**
- **THEN** routing resolves to `/term/received` using the shared Received list component for the Term workspace (not a standalone duplicate Term-only list implementation)
