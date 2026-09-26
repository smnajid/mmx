# managed-currency-settings Specification (delta)

## MODIFIED Requirements

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

### Requirement: Trader settings UI for currencies

The Angular application SHALL provide a currency settings area reachable without using ON-CALL or Term desk queue tabs. The UI SHALL support listing currencies, onboarding a new currency, editing rules for an existing currency, deactivating a currency, and reactivating an inactive currency. Forms SHALL mirror API validation including the workspace coverage guard (at least one of Term tenors or OnCall notice periods enabled).

#### Scenario: UI blocks last tenor toggle

- **WHEN** the currency has only one enabled tenor and no enabled notice periods
- **THEN** that tenor checkbox cannot be turned off

---

## ADDED Requirements

### Requirement: Intake respects workspace-enabled sets

When a managed currency is **active**, Term order intake SHALL be allowed only if the order tenor is in `enabledTenors` (which may be non-empty while notice periods are empty). OnCall order intake SHALL be allowed only if the order notice period is in `enabledNoticePeriods` (which may be non-empty while tenors are empty).

#### Scenario: Term intake with Term-only currency

- **WHEN** EUR is active with enabled tenor `3M` and no enabled notice periods
- **THEN** a new Term order for EUR with tenor `3M` passes currency policy

#### Scenario: OnCall intake rejected for Term-only currency

- **WHEN** EUR is active with enabled tenors and no enabled notice periods
- **THEN** a new OnCall order for EUR is rejected by currency policy
