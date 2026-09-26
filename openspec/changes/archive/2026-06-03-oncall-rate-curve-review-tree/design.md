## Context

[`oncall-rate-curve-handoff`](../archive/2026-05-31-oncall-rate-curve-handoff/) delivered OnCall rate curve domain rules, REST at `GET/POST /api/v1/settings/institutions/{institutionCode}/oncall-rates`, and the Angular settings screen at `/settings/oncall-rates`. The screen selects an **institution**, provides an **Add rate** form `(currency, noticePeriod, rate, valueDate)`, and lists all segments in one flat `mmx-table` with `Currency`, `Notice`, `Rate`, `Value date`, `End date`, and `Status` columns.

Domain identity (O-01): a **curve point** is `(institution, currency, noticePeriod)`. Segments are a contiguous timeline per curve point. The flat table interleaves rows from different curve points and repeats currency/notice on every row, obscuring the time-based curve mental model.

[`term-rates-review-tree`](../archive/2026-05-31-term-rates-review-tree/) established a reusable **review tree** pattern (`group-term-rates-for-review.ts`, `.settings-review-tree` CSS, `<details>`/`<summary>`, expand/collapse all). OnCall review differs: leaves are **time segments** (not static tenor/rate pairs), institution is already filtered, and headers must expose the **current open segment**.

### Product decisions (resolved)

| Question | Decision |
|----------|----------|
| Default expand state | **All collapsed** when segments load or institution changes; **current rate + status** visible on each curve-point header without expanding |
| Canceled segments | **Hidden** in review UI (client-side filter; no API change) |
| Empty curve points | **Do not show** — only curve points with ≥1 non-canceled segment |
| Add form coupling | **Yes** — selecting/expanding a curve-point node sets add form `currency` and `noticePeriod`; selected node visually highlighted |
| Spec coverage | New **`oncall-rate-settings-ui`** capability + SDD mirror in feature 002 |

## Goals / Non-Goals

**Goals:**

- Hierarchical **Curve segments** UI: one expandable node per curve point `(currency, noticePeriod)`.
- Curve-point header shows **current rate summary** from the open segment (`endDate === 2999-12-31`); if none, show appropriate empty/historical summary per spec.
- Expanded body: compact segment table (value date, end date as **Open**, rate, status, cancel action for pending).
- Filter out **`CANCELED`** segments before grouping.
- Default collapsed; **Expand all** / **Collapse all**; reset on institution change.
- Stable sort: currency ISO ascending, then notice period catalog order `24H`, `48H`.
- Selecting a curve point syncs **Add rate** form fields.
- Vitest for grouping, summary derivation, filtering, expand/collapse, form sync.
- SDD: `oncall-rate-settings-ui` + feature 002 UX bullets.

**Non-Goals:**

- OpenAPI / backend / domain rule changes.
- Timeline/bar chart visualization.
- Flat table alternate view.
- Session-persisted expand or selection state.
- Placeholder nodes for curve points with no segments.
- Showing canceled segments (even in a "history" toggle).

## Decisions

### 1. One-level tree per curve point (not currency → notice nesting)

**Choice:** Each `<details>` node represents one **curve point** with header label `{currency} · {noticePeriod}` (e.g. `EUR · 24H`). Expanded content is a segment table for that point only.

**Alternatives:**

- Two-level currency → notice — rejected for extra click without benefit when headers already encode both dimensions.
- Flat grouped table with `<tbody>` headers — rejected; term-rates tree pattern already proven in repo.

**Rationale:** Matches domain key O-01 directly; institution scope is already fixed by the institution selector.

### 2. Pure function grouping helper

**Choice:** Add `group-oncall-rate-segments-for-review.ts` under `oncall-rate-settings/`:

```text
OnCallCurvePointNode {
  currency, noticePeriod,
  segments: OnCallRateSegment[],  // non-canceled, valueDate desc
  currentSegment?: OnCallRateSegment  // endDate === '2999-12-31' if any
}
```

Export `curvePointKey(currency, noticePeriod)` for expand/selection state.

**Rationale:** Testable without `TestBed`; mirrors `group-term-rates-for-review.ts`.

### 3. Current rate summary on collapsed header

**Choice:** Header meta line shows formatted rate and status label from `currentSegment` (open segment). If no open segment (all historical or empty after filter), show **No open segment** (or last segment rate with closed indicator — pick in implementation to match spec scenarios).

**Rationale:** Answers trader's primary question without expanding.

### 4. Reuse `.settings-review-tree` disclosure pattern

**Choice:** Reuse existing `settings.scss` review tree tokens and toolbar (Expand all / Collapse all). Add modifier classes if needed for segment tables inside curve-point bodies (e.g. `.settings-review-tree__segment-table`).

**Alternatives:**

- Duplicate styles — rejected.
- New accordion component — rejected for POC.

**Rationale:** Visual consistency with Term rates Review; accessible native `<details>`.

### 5. Canceled segment filtering (client-side)

**Choice:** Filter `status === 'CANCELED'` in the grouping helper before building nodes. Curve points whose segments are all canceled produce **no node**.

**Alternatives:**

- Backend query param — rejected (unnecessary contract churn).

**Rationale:** Matches product decision; list API unchanged.

### 6. Add form sync on curve-point interaction

**Choice:** `selectedCurvePointKey` signal updated when user clicks/expands a curve-point `<details>` summary (toggle handler). `addForm.currency` and `addForm.noticePeriod` updated to match. Selected node gets a CSS class (e.g. `.settings-review-tree__curve-point--selected`).

**Alternatives:**

- Separate curve-point picker dropdown — rejected (duplicates tree).

**Rationale:** Closes loop between maintain and review; matches product decision.

### 7. Sentinel end date display

**Choice:** Map `endDate === '2999-12-31'` to display label **Open** in segment rows and summaries (not in API payloads).

**Rationale:** Domain sentinel is correct on wire; hostile in UI.

### 8. No backend changes

**Choice:** Frontend-only. Existing endpoints:

- `GET /api/v1/settings/institutions/{institutionCode}/oncall-rates`
- `POST …/oncall-rates`
- `POST …/oncall-rates/{segmentId}/cancel`

**Rationale:** Presentation-only scope per proposal.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Canceled segments invisible to trader | By design; audit remains in API/DB if needed later |
| Two curve points with similar headers | Distinct `currency · noticePeriod` labels; sort order stable |
| Selection vs expand conflated | Document: any expand/select sets add form; highlight selected key |
| Term tree CSS assumes institution/currency levels | Add curve-point-specific class; segment table inside body |
| Vitest assumes flat table | Update `oncall-rate-settings.component.spec.ts` |

## Migration Plan

Frontend-only deploy. No Flyway or API rollout.

1. Ship grouping helper + Vitest.
2. Replace flat table with curve-point tree + add-form sync.
3. Archive OpenSpec change → `openspec/specs/oncall-rate-settings-ui/spec.md`; sync `specs/002-trader-orders-views/spec.md`.
4. Manual smoke: load institution → collapsed headers show current rates → expand one curve → add form pre-filled → Expand all → switch institution resets.

Rollback: revert frontend commit; no data migration.

## Open Questions

_None — product decisions locked in table above._
