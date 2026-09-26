# term-rate-settings-ui Specification (delta)

## Purpose

Presentation and interaction standards for the Term rates morning workflow at `/settings/term-rates`: desk-aligned theme, guided prepare/upload/review layout, trading-day discovery, replace-day guard, and day summary.

## ADDED Requirements

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

The screen SHALL present three ordered workflow areas with visible headings: **Prepare** (sample download), **Upload** (file + submit), and **Review** (trading day selection and rates table). Download sample SHALL remain available from Prepare; Upload SHALL not be the only place for sample access.

#### Scenario: Prepare section offers sample download

- **WHEN** the trader is on the Term rates screen
- **THEN** a **Download sample CSV** control is available in the Prepare area

#### Scenario: Upload section is distinct from review table

- **WHEN** the trader scrolls the page
- **THEN** file selection and Upload submit are grouped separately from the rates table

---

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

The rates table SHALL show **Institution** (code), **Currency**, **Tenor**, and **Rate** columns. Per-row **uploadedBy** SHALL NOT be shown in the default table layout. Errors from upload validation SHALL use the shared settings error styling (`.settings-error` and structured row error list).

#### Scenario: Table columns for scan

- **WHEN** rates are listed for a day
- **THEN** each row shows institution code, currency, tenor, and formatted rate without an uploaded-by column

#### Scenario: Row errors use settings error pattern

- **WHEN** upload returns HTTP 400 with row errors
- **THEN** errors are displayed with the same visual treatment as other settings screens (mono error text, readable list)

---

### Requirement: Term rates UI does not surface rates on order screens

Rates MUST NOT appear on order detail or execute screens in this capability (unchanged from term-rate-daily-upload).

#### Scenario: Order detail unchanged

- **WHEN** the trader opens order details from a desk queue
- **THEN** Term rate reference data is not shown on that screen
