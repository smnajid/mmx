## Why

The OnCall rates **Curve segments** section renders a flat table mixing all `(currency, noticePeriod)` curve points for the selected institution. Repeated currency and notice columns, interleaved rows from different curves, and no at-a-glance **current rate** make it hard for traders to verify curve state or relate the **Add rate** form to a specific curve point. After [`oncall-rate-curve-handoff`](../archive/2026-05-31-oncall-rate-curve-handoff/) delivered domain rules and REST, presentation still does not match how traders think: **one time-based curve per `(currency, noticePeriod)`**.

**Programme context:** Builds on completed on-call curve backend and the existing `/settings/oncall-rates` screen. Product decisions from discovery are captured in `design.md` (default collapsed with current rate in header; hide canceled segments; only show curve points that exist; tie add form to selected curve point; new UI spec capability).

## What Changes

- Replace the flat **Curve segments** table with a **hierarchical review tree** keyed by **curve point** `(currency, noticePeriod)` — one expandable node per curve point that has at least one non-canceled segment.
- Each curve-point header SHALL show **current rate summary** (open segment: end date `2999-12-31`) including formatted rate and lifecycle status label; display **Open** instead of sentinel end date `2999-12-31` in segment rows.
- **Default collapsed** when segments load or institution changes; provide **Expand all** and **Collapse all** controls (reuse term-rates review tree toolbar pattern).
- **Hide `CANCELED` segments** from the review tree (API may still return them; UI filters client-side).
- **Do not show empty curve points** — only render nodes for `(currency, noticePeriod)` combinations that have at least one visible (non-canceled) segment.
- **Tie add form to selection:** selecting or expanding a curve-point node SHALL set the **Add rate** form `currency` and `noticePeriod` to that curve point; visual highlight on the selected curve point.
- **Vitest** coverage for grouping/summary logic, canceled filtering, default collapsed, expand/collapse all, add-form sync, and sentinel date display.
- **SDD sync:** new OpenSpec capability `oncall-rate-settings-ui`; mirror key UX bullets in `specs/002-trader-orders-views/spec.md` when implementing (no REST contract change).

**Out of scope:**

- Backend or OpenAPI changes (list API unchanged; canceled segments may remain in payload).
- Timeline/bar visualization of segment spans.
- Flat-table toggle or cross-currency comparison mode.
- Persisting expand/selection state across sessions or page reloads.
- Showing curve points with zero segments (placeholder nodes).

No **BREAKING** API or routing changes.

## Capabilities

### New Capabilities

- `oncall-rate-settings-ui`: Trader-facing OnCall rate settings presentation at `/settings/oncall-rates` — curve-point review tree, current-rate headers, add-form coupling, canceled-segment filtering, and desk-aligned styling.

### Modified Capabilities

_None — domain rules in `oncall-rate-curve-management` unchanged; this change is presentation-only._

## Impact

- **Frontend (`frontend/`)**: `oncall-rate-settings.component.ts`, new `group-oncall-rate-segments-for-review.ts` (+ spec), extend or reuse `.settings-review-tree` styles in `settings.scss`, update `oncall-rate-settings.component.spec.ts`.
- **OpenSpec / SDD**: new `openspec/specs/oncall-rate-settings-ui/spec.md` (via archive); `specs/002-trader-orders-views/spec.md` — OnCall settings UX bullets (no OpenAPI change).
- **Backend**: None.
- **Tests**: Vitest in `frontend/` only (TDD for grouping helper and component behaviour).
