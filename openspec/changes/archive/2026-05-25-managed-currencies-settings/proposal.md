## Why

Traders today process orders against hard-coded tenor, notice-period, and currency assumptions baked into the domain and intake API. Money-market desks need per-currency operational rules— which instruments are offered, and minimum ticket sizes by operation— before institutions and rates are added to the same settings model. This change introduces **managed currencies** as the first trader-facing reference-data capability so intake and desk workflows only accept orders the desk has explicitly configured.

## What Changes

- Add a **managed currency catalog**: traders can list currencies the desk supports and onboard new ISO 4217 currencies into that catalog.
- Per managed currency, traders configure:
  - **Term orders**: enable/disable each standard tenor (`1W`, `2W`, `1M`, `3M`, `6M`, `1Y`) for new and existing Term traffic.
  - **OnCall orders**: enable/disable each standard notice period (`24H`, `48H`).
  - **Minimum amounts**: separate floors for **Subscription** vs **Increase/Decrease** (Redemption amounts follow the Increase/Decrease floor where applicable).
- **Enforce configuration at order boundaries**: Portfolio Management intake (`POST /api/v1/orders`) and trader amount updates reject unknown currencies, disabled tenors/notice periods, and amounts below the configured minimums for the operation type.
- Add **trader settings UI and REST API** (contract-first OpenAPI) for viewing and editing currency configuration.
- Extend application navigation so traders can reach currency settings from the desk shell.

**Out of scope for this change** (planned follow-ons in the same settings programme):

- Institution (counterparty) onboarding and execution restricted to a listed institution catalog.
- Rate tables or other reference data beyond currency rules.

No **BREAKING** changes to existing order lifecycle states or desk queue behaviour; intake validation becomes stricter for currencies not present in the catalog or violating per-currency rules.

## Capabilities

### New Capabilities

- `managed-currency-settings`: Trader-maintained currency catalog and per-currency configuration (enabled tenors, enabled notice periods, minimum subscription amount, minimum increase/decrease amount) exposed via product REST and Angular settings screens.
- `order-currency-constraints`: Domain and application enforcement that order receive and trader update paths validate currency, tenor/notice period, and amount against the active managed-currency configuration.

### Modified Capabilities

- `trader-desk-navigation`: Add trader-accessible navigation to currency settings without disrupting the existing ON-CALL / Term desk tab model.

## Impact

- **Feature specs & contracts**: New feature folder under `specs/` (e.g. managed-currency settings OpenAPI + `api-v1.md`); updates to `specs/001-mm-order-processing` functional requirements (FR-005, FR-006, currency/amount validation) and intake error semantics.
- **Backend (hexagonal)**: New domain/application artifacts for `ManagedCurrency` configuration; persistence adapter + Flyway migration; validation hooks in receive/update use cases; REST adapter implementing generated API interfaces in `mmx-adapter-in-rest`.
- **Frontend (Angular)**: New settings feature area (list + detail/edit per currency); `order-api` unchanged for queues; new API client for currency settings; possible shared form patterns for tenor/notice toggles and amount inputs.
- **Tests**: TDD— domain tests for constraint rules; application/integration tests for intake rejection cases; Vitest for settings UI; contract-aligned REST tests.
- **Data**: New relational table(s) for currency catalog and per-currency rule rows; no migration of historical orders required beyond rejecting new invalid intake.
