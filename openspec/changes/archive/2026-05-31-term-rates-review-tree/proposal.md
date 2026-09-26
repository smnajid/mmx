## Why

The Term rates **Review** section still renders a flat table of institution codes, currencies, tenors, and rates. After [`term-rates-settings-ux`](../term-rates-settings-ux/) delivered the prepare → upload → review workflow, traders report that repeated codes (`BI-01`, `BP-01`, …) are hard to scan and do not match how they think about the morning sheet (per bank, then per currency). Showing **institution display names** and a **hierarchical review tree** makes upload verification faster without changing ingest or REST contracts.

**Programme context:** Builds on completed [`term-rate-daily-upload`](../term-rate-daily-upload/) and in-progress/near-complete [`term-rates-settings-ux`](../term-rates-settings-ux/). Product decisions from discovery are captured in `design.md` (display name via Institutions catalog join; default collapsed; expand/collapse all; tree-only review; name on institution header only).

## What Changes

- Replace the flat rates table in **Review** with a **three-level tree**: institution → currency → tenor/rate leaves.
- Show **institution display name** on each institution header, joined client-side from `GET /api/v1/settings/institutions` (current catalog `displayName`; code shown as secondary mono label). Fallback to code only when no catalog match.
- **Default collapsed** at all levels when a trading day loads; provide **Expand all** and **Collapse all** controls for the tree.
- Per-level **counts** on institution and currency headers (e.g. currencies and rates under a bank; tenors under a currency).
- **Tenor sort order** fixed to catalog order: `1W`, `2W`, `1M`, `3M`, `6M`, `1Y` (not alphabetical).
- **Vitest** coverage for grouping, display-name join/fallback, expand/collapse all, and default collapsed state.
- **SDD sync:** update `specs/005-term-rate-settings/spec.md` review UX bullets when implementing.

**Out of scope:**

- Backend or OpenAPI changes (no `institutionDisplayName` on `TermRate` DTO).
- Flat-table toggle or cross-bank tenor comparison view.
- Display names on currency or tenor rows (institution header only).
- Persisting expand state across sessions or page reloads.
- CSV sample format, upload ingest, day chips, replace-day confirm (unchanged).

No **BREAKING** API or routing changes.

## Capabilities

### New Capabilities

_None — presentation extends the existing Term rates settings UI capability._

### Modified Capabilities

- `term-rate-settings-ui`: Replace flat “rates table optimized for scanning” requirement with hierarchical review tree, institution display names on headers, expand/collapse-all, and default-collapsed behaviour.

## Impact

- **Frontend (`frontend/`)**: `term-rate-settings.component.ts`, `settings.scss` (tree/disclosure styles), optional small grouping helper; `term-rate-settings.component.spec.ts`; inject `InstitutionSettingsApiService` for catalog join.
- **Feature specs (SDD)**: `specs/005-term-rate-settings/spec.md` — Review section UX (no REST contract change).
- **Backend**: None.
- **Tests**: Vitest in `frontend/`; no new Maven scope unless shared helper is extracted to a testable module.
