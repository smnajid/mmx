# term-rate-settings-ui Specification

## Purpose
TBD - created by archiving change term-rates-settings-ux. Update Purpose after archive.
## Requirements
### Requirement: Term rates screen uses desk visual theme

The Term rates settings screen SHALL use the same global design tokens as desk order views and other settings screens (`--mmx-bg`, `--mmx-surface`, `--mmx-border`, `--mmx-text`, `--mmx-text-muted`, `--mmx-accent`, `--mmx-accent-dim`, `--font-display`, `--font-mono`). Primary actions, elevated surfaces, tables, and form controls on this screen SHALL NOT use browser-default styling or ad-hoc palette colours absent from desk queues.

#### Scenario: Upload controls match settings form styling

- **WHEN** the trader opens `/settings/term-rates`
- **THEN** the file picker and trading-date control use the same border, background, focus ring, and mono label patterns as currency settings form inputs

#### Scenario: Workflow sections use elevated surfaces

- **WHEN** the trader views the prepare, upload, and review areas
- **THEN** each area is visually contained in a surface with border and background consistent with `settings-table-wrap` (not flat text on the page background alone)

---

### Requirement: Term rates screen explains the morning workflow

The page SHALL include a short **lede** under the `Term rates` heading that states the screen is for the morning reference sheet, that CSV upload defines rates per trading day, and that **re-upload replaces the entire day** (T-02).

#### Scenario: Lede visible on entry

- **WHEN** the trader navigates to `/settings/term-rates`
- **THEN** explanatory copy about daily sheet and replace-day behaviour is visible without expanding a section

---

### Requirement: Term rates workflow is step-oriented

The screen SHALL present three ordered workflow areas with visible headings: **Prepare** (sample download), **Upload** (file + submit), and **Review** (trading day selection and **rates review tree**). Download sample SHALL remain available from Prepare; Upload SHALL not be the only place for sample access.

#### Scenario: Prepare section offers sample download

- **WHEN** the trader is on the Term rates screen
- **THEN** a **Download sample CSV** control is available in the Prepare area

#### Scenario: Upload section is distinct from review tree

- **WHEN** the trader scrolls the page
- **THEN** file selection and Upload submit are grouped separately from the rates review tree

### Requirement: Trading days with data are discoverable

On load and after a successful upload, the UI SHALL call `GET /api/v1/settings/term-rates/days` and present the returned `tradingDate` values as quick-select controls (e.g. chips), ordered as returned by the API (newest first). Selecting a quick-select SHALL set the active trading day and refresh the rates list. A manual date control SHALL remain for selecting dates not in the quick-select list.

#### Scenario: Chips reflect uploaded days

- **WHEN** the API returns days `2026-05-31` and `2026-05-30`
- **THEN** both dates appear as quick-select options and selecting `2026-05-30` loads rates for that day

#### Scenario: Manual date still works

- **WHEN** the trader picks a date via the date control that has no chip (e.g. today with no upload yet)
- **THEN** the UI requests rates for that date and shows the empty state if none exist

#### Scenario: Days list refreshes after upload

- **WHEN** the trader successfully uploads rates for a new trading day
- **THEN** the quick-select list includes that day without a full page reload

---

### Requirement: Day summary shows sheet status

When at least one rate exists for the selected trading day, the UI SHALL show a summary line including the trading date, **row count**, and **last upload time** derived from the loaded rates (maximum `uploadedAt` among rows).

#### Scenario: Summary visible with data

- **WHEN** twelve rates exist for `2026-05-31` with latest `uploadedAt` `2026-05-31T08:12:00Z`
- **THEN** the summary indicates twelve rates and a last-upload time consistent with that timestamp

#### Scenario: No summary when empty

- **WHEN** no rates exist for the selected day
- **THEN** the summary strip is omitted or shows only the selected date without implying a prior upload

---

### Requirement: Replace-day upload requires confirmation

When the trader submits an upload and the rates table for the **currently selected** trading day already contains at least one row, the UI SHALL require explicit confirmation before calling `POST /api/v1/settings/term-rates/upload`, with copy that explains the upload will **replace all rates for that trading day**. If the trader dismisses confirmation, the upload MUST NOT be sent.

#### Scenario: Confirm when day has existing rates

- **WHEN** the selected day already shows rates and the trader clicks Upload
- **THEN** a confirmation prompt appears before the HTTP upload request

#### Scenario: No confirm on empty day

- **WHEN** the selected day has no rates and the trader clicks Upload with a file chosen
- **THEN** the upload proceeds without a replace-day confirmation prompt

#### Scenario: Cancel leaves data unchanged

- **WHEN** the trader declines the replace-day confirmation
- **THEN** no upload request is sent and the displayed rates for the selected day are unchanged

---

### Requirement: Empty state guides first upload

When no rates exist for the selected trading day, the UI SHALL show an empty state that directs the trader to download the sample CSV and upload a completed file (not only passive “no rates” text).

#### Scenario: Empty day shows guidance

- **WHEN** the selected trading day has no uploaded rates
- **THEN** the UI offers a path to download sample and indicates upload is the next step

---

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

### Requirement: Term rates UI does not surface rates on order screens

Rates MUST NOT appear on order detail or execute screens in this capability (unchanged from term-rate-daily-upload).

#### Scenario: Order detail unchanged

- **WHEN** the trader opens order details from a desk queue
- **THEN** Term rate reference data is not shown on that screen

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

