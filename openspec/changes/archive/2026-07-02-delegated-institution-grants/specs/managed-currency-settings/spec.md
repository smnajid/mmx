## ADDED Requirements

### Requirement: Managed currencies are hub-owned and client-read-only

Managed currencies SHALL be owned and managed by the TradingHub LegalEntity. A user with the Trader role on a TradingHub SHALL onboard, update, deactivate, and reactivate managed currencies. A `ClientRepresentative` on a TradingClient SHALL read the connected hub's managed currency catalog (in-process, same deployment) and SHALL NOT create, update, deactivate, or reactivate currencies. A TradingClient's intake currency and workspace-enabled validation SHALL use the connected hub's managed currency set.

#### Scenario: ClientRepresentative reads the hub currency catalog

- **WHEN** a `ClientRepresentative` on `PAR` requests the managed currency list
- **THEN** the system returns `LOC`'s managed currency catalog read-only

#### Scenario: ClientRepresentative cannot mutate a managed currency

- **WHEN** a `ClientRepresentative` on `PAR` attempts to onboard, update, deactivate, or reactivate a managed currency
- **THEN** the system rejects the request with an authorisation error and no currency is changed

---

## MODIFIED Requirements

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
