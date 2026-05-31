## Why

Managed currencies defined which instruments the desk offers. Traders still lack an **authoritative institution catalog** and execution still accepts arbitrary counterparty text. **Phase 1** onboard banks as **institutions** in Settings, enforce **onboarded institution at execute** (autocomplete), and apply a **strict execute cold start** (mirror currencies discipline). Term CSV and OnCall curves remain later phases ([`PROGRAMME.md`](PROGRAMME.md)).

## What Changes

- **Institution catalog**: traders onboard with **`displayName`** only; system generates immutable **`institutionCode`** as **`{ACRONYM}-{nn}`** (e.g. `HSBC-01`); active/inactive; settings REST + UI.
- **Execute enforcement**: execution requires selection of an **active onboarded institution**; trader UI **autocomplete** from catalog; contract-first change to execute API (`institutionCode`); persisted execution **counterparty** derived from institution (order vocabulary per P-02).
- **Strict execute cold start**: empty institution catalog ⇒ **execute rejected**; quickstart/demo must onboard institutions before executing (intake unchanged in phase 1).
- **Settings hub (U-01)**: single header **Settings** link; sub-navigation **Currencies | Institutions** under `/settings/*` (replaces standalone Currencies header link).
- **Settings UX (U-02)**: institution screens reuse `currency-settings-ux` patterns (`DeskReturnService`, back-to-desk toolbar, shared settings styling).

**Deferred** to [`term-rate-daily-upload`](../term-rate-daily-upload/) and [`oncall-rate-curve-handoff`](../oncall-rate-curve-handoff/): rates, CSV, OnCall segments, Kafka rate events.

**Out of scope:** rate tables; auto-binding rates to orders; intake institution field from PM.

**Breaking (trader execute API):** `POST .../execute` replaces free-text-only counterparty with required **`institutionCode`** (counterparty on response/detail derived from institution). Document in `specs/002` delta + OpenAPI.

## Capabilities

### New Capabilities

- `institution-onboarding`: Institution catalog via product REST and Angular settings UI.
- `order-institution-constraints`: Execute path validates active onboarded institution; maps to execution `counterparty`; execute blocked when catalog empty.

### Modified Capabilities

- `trader-desk-navigation`: Single Settings header entry, in-settings sub-nav (Currencies, Institutions), desk tabs hidden on settings routes.
- `currency-settings-ui` (delta if needed): Align currency settings with Settings hub header/sub-nav refactor.
- `trader-order-detail-actions` (or 002 execute surface): Execute request/UX aligned with institution selection (delta spec in this change).

## Impact

- **Contracts**: `specs/004-institution-settings/` (settings API); **delta** `specs/002-trader-orders-views/contracts/openapi.yaml` execute body; `api-v1.md` mirrors.
- **Backend**: `Institution`, `OrderAgainstInstitutionPolicy`, `ExecuteOrderService` + `InstitutionRepository`; Flyway `institution`; handoff payload `counterparty` unchanged shape (populated from institution).
- **Frontend**: Institution settings; **execute form autocomplete** (active institutions); optional `GET` list endpoint for typeahead if not embedded from settings API.
- **Tests**: Policy + execute service tests; execute integration rejects unknown/inactive/empty catalog; Vitest autocomplete.
- **Decisions**: [`OPEN-DECISIONS.md`](OPEN-DECISIONS.md) P-01–P-03 **decided**.
