# term-rate-settings-ui Specification (delta)

## Purpose

Extend Term rates **Review** presentation: hierarchical tree by institution and currency, institution display names from catalog, default collapsed with expand/collapse all. Prepare, Upload, day discovery, replace-day guard, and day summary unchanged.

## MODIFIED Requirements

### Requirement: Rates table optimized for scanning

The Review area SHALL present uploaded rates in a **hierarchical tree**, not a flat table. Structure:

1. **Institution** level (expandable) — header shows institution **display name** (from Institutions catalog when available) and **institution code** as a secondary mono label; optional summary counts (currencies and total rates under that institution).
2. **Currency** level (expandable under an institution) — header shows ISO currency code and tenor count.
3. **Leaf** rows — **Tenor** and formatted **Rate** only (no institution or currency columns repeated).

Tenors within a currency MUST be ordered: `1W`, `2W`, `1M`, `3M`, `6M`, `1Y`. Institution groups MUST be ordered by `institutionCode` ascending. Currency groups MUST be ordered by currency code ascending.

Per-row **uploadedBy** MUST NOT appear in the review layout. Upload validation errors MUST continue to use shared settings error styling (`.settings-error` and structured row error list).

#### Scenario: Tree shows institution then currency then rates

- **WHEN** rates exist for a day across two institutions and multiple currencies
- **THEN** the trader can expand an institution to see its currencies, expand a currency to see tenor and rate pairs, without a single flat grid of repeated institution codes

#### Scenario: Tenors in catalog order

- **WHEN** a currency has tenors `3M`, `1W`, and `1M` uploaded
- **THEN** leaves appear in order `1W`, `1M`, `3M`

#### Scenario: Row errors use settings error pattern

- **WHEN** upload returns HTTP 400 with row errors
- **THEN** errors are displayed with the same visual treatment as other settings screens (mono error text, readable list) and the review tree is not shown until a successful load for that attempt

---

### Requirement: Term rates workflow is step-oriented

The screen SHALL present three ordered workflow areas with visible headings: **Prepare** (sample download), **Upload** (file + submit), and **Review** (trading day selection and **rates review tree**). Download sample SHALL remain available from Prepare; Upload SHALL not be the only place for sample access.

#### Scenario: Prepare section offers sample download

- **WHEN** the trader is on the Term rates screen
- **THEN** a **Download sample CSV** control is available in the Prepare area

#### Scenario: Upload section is distinct from review tree

- **WHEN** the trader scrolls the page
- **THEN** file selection and Upload submit are grouped separately from the rates review tree

## ADDED Requirements

### Requirement: Institution display name on review headers

When rendering an institution node in the review tree, the UI SHALL resolve **displayName** from `GET /api/v1/settings/institutions` (current catalog) by matching `institutionCode`. The primary visible label on the institution header SHALL be **displayName** when found; **institutionCode** SHALL appear as a secondary mono label on that header only. Display names MUST NOT be shown on currency or tenor rows.

If no catalog entry matches a code present in the loaded rates, the header SHALL show **institutionCode** as the primary label (no error for missing name).

#### Scenario: Header shows name and code

- **WHEN** rates include `BP-01` and institutions list has `institutionCode` `BP-01` with `displayName` `BNP Paribas`
- **THEN** the institution header shows `BNP Paribas` prominently and `BP-01` as secondary mono text

#### Scenario: Fallback when catalog miss

- **WHEN** rates include code `XX-99` not in the institutions list
- **THEN** the institution header shows `XX-99` as the primary label

---

### Requirement: Review tree default collapsed with expand and collapse all

When rates load for a selected trading day (including after chip or date change), **all** institution and currency nodes in the review tree SHALL start **collapsed**. The Review area SHALL provide **Expand all** and **Collapse all** controls that expand or collapse every institution and currency node for the current day. Changing the selected trading day SHALL reset the tree to all collapsed.

#### Scenario: Initial load collapsed

- **WHEN** rates are loaded for a trading day with multiple institutions
- **THEN** no institution or currency children are visible until the trader expands a node or uses Expand all

#### Scenario: Expand all reveals full tree

- **WHEN** the trader activates Expand all
- **THEN** every institution and currency node for that day is expanded and all tenor-rate leaves are visible

#### Scenario: Collapse all resets visibility

- **WHEN** the trader activates Collapse all after expanding nodes
- **THEN** all institution and currency nodes are collapsed

#### Scenario: Day change resets collapse

- **WHEN** the trader selects a different trading day via chip or date control
- **THEN** the newly loaded tree is all collapsed regardless of prior expand state
