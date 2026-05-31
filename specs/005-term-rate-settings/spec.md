# Feature 005 — Term rate settings

Trader-maintained **morning Term rate sheet** per trading day: CSV upload, validation against institution and managed-currency catalogs, persistence at grain **(institutionCode, currency, tenor)**. See OpenSpec change `term-rate-daily-upload` for programme context and `design.md` in that change for architecture.

## Scope

- REST API under `/api/v1/settings/term-rates` (contract-first OpenAPI)
- CSV columns: `tradingDate`, `institutionCode`, `currency`, `tenor`, `rate`
- Replace whole trading day on re-upload (T-02)
- Sample CSV download (T-05)
- Settings UI at `/settings/term-rates` (OpenSpec `term-rates-settings-ux` / `term-rate-settings-ui`)

### Settings UI (trader-facing)

- **Workflow layout**: Prepare (sample download) → Upload (CSV file) → Review (day picker + hierarchical rates tree), with lede explaining morning sheet and whole-day replace.
- **Trading day discovery**: `GET /api/v1/settings/term-rates/days` drives quick-select chips; manual date control remains for days without uploads.
- **Day summary**: When rates exist for the selected day, show row count and last upload time (from row `uploadedAt`).
- **Replace-day guard**: Confirm before upload when the selected day already has rates.
- **Review tree**: Rates grouped institution → currency → tenor/rate; default **collapsed**; **Expand all** / **Collapse all**; institution header shows **displayName** from Institutions catalog (client join) plus code as secondary label when known; tenors in catalog order `1W` … `1Y` (no per-row `uploadedBy`).
- **Presentation**: Desk-aligned `settings.scss` tokens, elevated settings cards, styled file/date inputs.
- **Empty state**: Directs trader to download sample then upload.

## Programme decisions

| ID | Decision |
|----|----------|
| T-01 | Row grain = (institutionCode, currency, tenor) per tradingDate |
| T-02 | Re-upload replaces entire day |
| T-03 | Explicit `tradingDate` column; single date per file |
| T-04 | Trader-only reference (no BO/Kafka) |
| T-05 | Server-generated sample CSV + UI download |

## Out of scope

- Applying rates to order intake, execute, or pricing
- OnCall curves and rate events
- Back-office async ingest of Term CSV
