# oncall-rate-settings-ui Specification (delta)

## Purpose

Define trader-facing presentation for OnCall rate settings at `/settings/oncall-rates`: institution-scoped curve-point review tree, current-rate headers, add-form coupling, and canceled-segment filtering. REST behaviour remains defined in feature 002 OpenAPI and `oncall-rate-curve-management` domain rules.

## ADDED Requirements

### Requirement: OnCall rates screen uses desk visual theme

The OnCall rates settings screen SHALL use the same global design tokens as desk order views and other settings screens (`--mmx-bg`, `--mmx-surface`, `--mmx-border`, `--mmx-text`, `--mmx-text-muted`, `--mmx-accent`, `--mmx-accent-dim`, `--font-display`, `--font-mono`). Primary actions, elevated surfaces, tables, form controls, and the curve review tree on this screen SHALL NOT use browser-default styling or ad-hoc palette colours absent from desk queues.

#### Scenario: Form controls match settings styling

- **WHEN** the trader opens `/settings/oncall-rates`
- **THEN** institution selector, add-rate inputs, and curve review controls use the same border, background, focus ring, and mono label patterns as other settings screens

#### Scenario: Workflow sections use elevated surfaces

- **WHEN** the trader views Institution, Add rate, and Curve segments areas
- **THEN** each area is visually contained in a settings card with border and background consistent with other settings screens

---

### Requirement: OnCall rates screen explains curve maintenance

The page SHALL include a short **lede** under the `OnCall rates` heading that states the screen maintains value-dated OnCall rate **curves per institution**, that pending segments price new orders immediately, and that back-office confirmation refreshes in-life contracts.

#### Scenario: Lede visible on entry

- **WHEN** the trader navigates to `/settings/oncall-rates`
- **THEN** explanatory copy about curve maintenance and pending confirmation is visible without expanding a section

---

### Requirement: Curve segments presented as curve-point review tree

The **Curve segments** area SHALL present rate segments in a **hierarchical review tree**, not a flat table mixing all currencies and notice periods. Structure:

1. **Curve point** level (expandable) — one node per `(currency, noticePeriod)` that has at least one **non-canceled** segment for the selected institution. Header label SHALL be `{currency} · {noticePeriod}` (mono currency and notice codes).
2. **Segment body** (visible when expanded) — compact table of segments for that curve point only: **Value date**, **End date**, **Rate**, **Status**, and **Cancel** action when applicable. Currency and notice period MUST NOT repeat on each segment row.

Curve point nodes MUST be ordered by `currency` ascending, then `noticePeriod` in catalog order `24H`, `48H`. Segments within a curve point MUST be ordered by `valueDate` descending (newest first).

Segments with status **`CANCELED`** MUST NOT appear in the review tree. Curve points whose segments are all canceled MUST NOT appear as nodes.

#### Scenario: Tree groups by curve point

- **WHEN** segments exist for `EUR · 24H` and `USD · 48H` at the same institution
- **THEN** the trader sees two curve-point nodes and can expand each to view only that point's segment history without a single flat grid mixing both

#### Scenario: Canceled segments hidden

- **WHEN** the list API returns valid, pending, and canceled segments for a curve point
- **THEN** the review tree shows only non-canceled segments and omits canceled rows from segment tables

#### Scenario: Empty curve points omitted

- **WHEN** a `(currency, noticePeriod)` combination has no non-canceled segments
- **THEN** no curve-point node is rendered for that combination

#### Scenario: Segments sorted newest first within curve point

- **WHEN** a curve point has segments with value dates `2026-01-01` and `2026-06-01`
- **THEN** the expanded segment table lists `2026-06-01` before `2026-01-01`

---

### Requirement: Curve-point header shows current rate summary

Each collapsed curve-point header SHALL display a **current rate summary** derived from the **open segment** for that curve point (segment whose `endDate` is the no-end sentinel `2999-12-31`). The summary SHALL include the formatted rate and human-readable status label (e.g. **Valid**, **Pending confirmation**).

If no open segment exists for the curve point (only historical closed segments remain), the header SHALL indicate that no open segment is active (wording left to implementation but MUST be visible without expanding).

#### Scenario: Open pending segment summarized on header

- **WHEN** `EUR · 24H` has an open segment at rate `3.25` in status `PENDING_CONFIRMATION`
- **THEN** the curve-point header shows rate `3.25` and pending confirmation status without expanding the node

#### Scenario: Open valid segment summarized on header

- **WHEN** `USD · 48H` has an open segment at rate `4.10` in status `VALID`
- **THEN** the curve-point header shows rate `4.10` and valid status without expanding the node

---

### Requirement: Sentinel end date displayed as Open

In segment rows (and anywhere end date is shown to the trader on this screen), the inclusive no-end sentinel date **`2999-12-31`** SHALL be displayed as **Open**, not the raw sentinel value.

#### Scenario: Open segment end date label

- **WHEN** a segment row has `endDate` `2999-12-31`
- **THEN** the End date column shows **Open**

---

### Requirement: Review tree default collapsed with expand and collapse all

When segments load for a selected institution (including after institution change), **all** curve-point nodes in the review tree SHALL start **collapsed**. The Curve segments area SHALL provide **Expand all** and **Collapse all** controls that expand or collapse every curve-point node. Changing the selected institution SHALL reset the tree to all collapsed and clear curve-point selection highlight.

#### Scenario: Initial load collapsed

- **WHEN** segments are loaded for an institution with multiple curve points
- **THEN** no segment tables are visible until the trader expands a node or uses Expand all

#### Scenario: Institution change resets collapse

- **WHEN** the trader selects a different institution
- **THEN** all curve-point nodes are collapsed again

---

### Requirement: Add rate form syncs to selected curve point

When the trader selects a curve-point node (by expanding or explicitly selecting its header), the **Add rate** form SHALL set **`currency`** and **`noticePeriod`** to match that curve point. The selected curve-point node SHALL be visually distinguished (e.g. selected highlight class). Submitting Add rate SHALL continue to use the existing `POST /api/v1/settings/institutions/{institutionCode}/oncall-rates` contract with those form values.

#### Scenario: Expanding curve point updates add form

- **WHEN** the trader expands the `EUR · 24H` curve-point node
- **THEN** the Add rate form shows `currency` `EUR` and `noticePeriod` `24H`

#### Scenario: Selected curve point highlighted

- **WHEN** the trader selects `USD · 48H`
- **THEN** that curve-point node carries a visible selected state distinct from unselected nodes

---

### Requirement: Pending segment cancel unchanged

The review tree SHALL retain the ability to **cancel** a segment in status `PENDING_CONFIRMATION` via `POST /api/v1/settings/institutions/{institutionCode}/oncall-rates/{segmentId}/cancel`. Cancel controls MUST appear only on pending segments in the expanded segment table.

#### Scenario: Cancel available on pending row

- **WHEN** an expanded curve point includes a `PENDING_CONFIRMATION` segment
- **THEN** a Cancel control is available on that segment row and invokes the existing cancel endpoint
