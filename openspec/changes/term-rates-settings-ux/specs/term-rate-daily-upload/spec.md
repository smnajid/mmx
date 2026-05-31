# term-rate-daily-upload Specification (delta)

## MODIFIED Requirements

### Requirement: Term rate settings UI under Settings hub

Term rate upload and day view SHALL live at `/settings/term-rates` within the Settings hub. The Settings sub-navigation SHALL include a **Term rates** entry (alongside Currencies and Institutions). Screens SHALL use the same return-to-desk and visual patterns as other settings flows (`DeskReturnService`, `settings-panel`, `settings-toolbar`, desk theme tokens per `currency-settings-ui` and `term-rate-settings-ui`).

The screen SHALL provide:

- **Download sample CSV** — invokes the sample `GET` and triggers a browser file download.
- **Upload** — styled file picker and submit to the upload endpoint; display success summary (`tradingDate`, `rowCount`) or row/structural errors from HTTP 400.
- **View day** — date control (default today), **quick-select for trading days returned by `GET /api/v1/settings/term-rates/days`**, day summary when rates exist, and table of rates from `GET` by `tradingDate`; actionable empty state when no upload exists for the selected date.
- **Replace-day guard** — when the selected day already has rates, require trader confirmation before upload (T-02).
- **Workflow presentation** — lede explaining morning sheet and whole-day replace; prepare / upload / review sections on elevated settings surfaces.

Rates MUST NOT appear on order detail or execute screens in this capability.

#### Scenario: Download sample from UI

- **WHEN** the trader clicks **Download sample CSV** on the Term rates screen
- **THEN** the browser saves a file named `term-rates-sample.csv` with the expected header row

#### Scenario: Upload success refreshes day view

- **WHEN** the trader uploads a valid CSV for `2026-05-30` and the view is set to that date
- **THEN** the UI shows the uploaded rates without requiring a full page reload

#### Scenario: Upload errors shown to trader

- **WHEN** the upload returns HTTP 400 with row errors
- **THEN** the UI displays line and message details and does not claim success

#### Scenario: Term rates tab in settings sub-nav

- **WHEN** the trader opens any `/settings/*` route
- **THEN** sub-navigation includes **Term rates** linking to `/settings/term-rates`

#### Scenario: Trading days quick-select

- **WHEN** `GET /api/v1/settings/term-rates/days` returns one or more dates
- **THEN** the UI offers quick-select for those dates and loads rates when one is chosen

#### Scenario: Replace-day confirmation

- **WHEN** the selected trading day already has rates and the trader initiates upload
- **THEN** the UI requires confirmation explaining whole-day replace before sending the upload request
