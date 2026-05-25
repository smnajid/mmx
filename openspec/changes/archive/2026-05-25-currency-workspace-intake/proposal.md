## Why

Traders need **Term-only** or **OnCall-only** intake for a currency while keeping it **active**. Today the catalog requires at least one enabled tenor **and** at least one notice period, and **Deactivate** blocks all workspaces. That forces desks to keep notice periods enabled when they only trade Term (or vice versa).

## What Changes

- Relax catalog validation: **empty** `enabledTenors` or **empty** `enabledNoticePeriods` allowed; **at least one workspace** must remain (non-empty tenors **or** non-empty notices).
- **PATCH** semantics: `enabledTenors: []` / `enabledNoticePeriods: []` explicitly clears that workspace (not “leave unchanged”).
- **UI**: allow unchecking all notices when tenors remain (and symmetrically); block only when both would be empty.
- **OpenAPI** / `api-v1.md`: `minItems: 0` on tenor/notice arrays; document workspace rule.
- **Specs** delta on `managed-currency-settings`; replace “last tenor/notice” requirement with workspace coverage rule.

**Out of scope:**

- Separate `termEnabled` / `onCallEnabled` flags (derive workspace from enabled sets).
- Institution/rates settings.
- Changing global `active` semantics.

No new REST paths; **behavior change** on update/onboard validation (non-breaking for clients that keep both workspaces enabled).

## Capabilities

### Modified Capabilities

- `managed-currency-settings`: Workspace-scoped intake via empty tenor or notice sets; catalog save guard “at least one workspace”.

## Impact

- **Domain**: `ManagedCurrency` validation
- **REST**: OpenAPI array constraints; `CurrencySettingsRestMapper` PATCH empty-array semantics
- **Frontend**: edit form toggle guards and save validation
- **Tests**: domain, application, REST, Vitest
- **Specs**: `specs/003-managed-currency-settings/contracts/*`
