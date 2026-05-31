## Context

[`term-rate-daily-upload`](../term-rate-daily-upload/) delivered CSV ingest, persistence, and a functional Angular screen at `/settings/term-rates`. The screen reuses U-02 primitives (`settings-panel`, `DeskReturnService`, `mmx-table`) but diverges in practice:

- Upload block uses unstyled `<input type="file">` and `<input type="date">` outside `.settings-form` styling.
- Two sibling `<section>` elements without the elevated `settings-table-wrap` surface used for catalog tables.
- `TermRateSettingsApiService.listTradingDays()` exists; the component never calls it (task 7.2 intent unfulfilled).
- Local component CSS duplicates `.btn-secondary` and uses ad-hoc error colour (`#f87171`) vs global `.settings-error`.

[`currency-settings-ux`](../archive/2026-05-25-currency-settings-ux/) established the pattern: presentation capability (`currency-settings-ui`) separate from domain/API specs, shared `settings.scss`, desk tokens.

Desk queues use `feature-head` + **lede** copy; Term rates is the only settings screen that is an **operations workflow** (not catalog CRUD).

## Goals / Non-Goals

**Goals:**

- Term rates screen visually and structurally consistent with MMx settings + desk theme.
- Morning workflow readable at a glance: prepare (sample) → upload → review (day + table).
- Discover trading days with existing `GET /days` API.
- Prevent accidental T-02 whole-day overwrite via confirm when rates already exist for the file’s date (or selected day after parse).
- Day summary (count + last upload timestamp) without new REST fields.
- Vitest locks key UX behaviours.

**Non-Goals:**

- OpenAPI / backend changes.
- Institution `displayName` column (requires API or client join — defer).
- Drag-and-drop file zone, table filtering, or split Settings IA (“Catalog” vs “Daily sheets”).
- Cypress e2e unless explicitly requested later.

## Decisions

### 1. Workflow layout over catalog layout

**Choice:** Single page with three elevated **cards** (shared visual recipe with `settings-table-wrap`: border, `var(--mmx-surface)`, radius, shadow):

1. **Prepare** — lede + download sample (secondary action).
2. **Upload** — styled file input + primary Upload; success/error inline.
3. **Review** — day picker + days quick-select + summary strip + `mmx-table`.

Page header: `h1` + **lede** paragraph (desk `feature-head` pattern) explaining replace-day semantics.

**Alternatives:**

- Tabs for Upload vs Review — extra clicks for a linear daily task.
- Keep flat sections — preserves current incoherence.

**Rationale:** Matches trader mental model; distinguishes Term rates from Currencies/Institutions lists without new routes.

### 2. Shared settings control styling

**Choice:** Extend `frontend/src/app/settings.scss` with:

- `.settings-file-input` / `.settings-upload-row` (wrap native file input; hide default chrome where practical via opacity overlay or consistent padding/border on wrapper).
- Reuse `.settings-form` label + input rules for `type="date"` (apply class on date control).
- `.settings-card` — shared wrapper extracting border/surface/shadow from `settings-table-wrap` (table wrap may compose `settings-card` + overflow).

Remove duplicate `.btn-secondary` from component; add `.btn-secondary` to `settings.scss` if not present (mirror `.btn-primary`).

**Rationale:** One token source; matches currency-settings-ux decision #2.

### 3. Trading days from `GET /days`

**Choice:** On init (and after successful upload), call `listTradingDays()`. Render **quick-select chips** (newest first, API order) above or beside the date input. Clicking a chip sets `tradingDate` and reloads rates. Manual date input remains for days not in the list (future dates, empty days).

**Alternatives:**

- `<select>` only — poor when many days; chips scale to ~20 POC days.
- Drop manual date — blocks “what if today has no file yet”.

**Rationale:** Fulfils original task 7.2 / design §GET /days without API change.

### 4. Day summary strip

**Choice:** When `rates().length > 0`, show: `{tradingDate} · {n} rates · last upload {max(uploadedAt)}` formatted for desk locale (ISO slice or `Date` pipe). Use row data; after upload success, optional immediate use of `uploadedAt` from response until reload completes.

**Rationale:** Traders see sheet status without scanning rows; no new API field.

### 5. Replace-day confirmation (T-02 guard)

**Choice:** Before `upload()` POST, if `rates().length > 0` for the **currently selected** `tradingDate()` **or** if we can infer from filename only after selection — use selected date path: when table has rows for `tradingDate()`, `window.confirm` (or inline dialog component if project already has one; default **confirm** for POC) with copy: re-upload replaces all rates for that day.

If user cancels, do not POST.

**Note:** CSV `tradingDate` may differ from picker; on success API returns actual date and view jumps — confirm when **current view** has data; if upload targets a different date in file, server still replaces that date (existing behaviour). Optional enhancement: after file pick, parse first row date client-side — **out of scope** for v1; confirm based on selected view day only.

**Rationale:** design.md risk “Replace-day deletes prior work accidentally”; low-cost mitigation.

### 6. Table columns

**Choice:** Keep `institutionCode`, `currency`, `tenor`, `rate`. **Hide `uploadedBy` from default columns** (omit column); `uploadedAt` only in day summary, not per row — reduces noise.

**Alternatives:**

- Keep audit columns — clutters scan grid.

### 7. Empty state

**Choice:** When no rates for selected day: message + link/button **Download sample CSV** (same handler as header) + short hint to upload after editing.

### 8. Spec layering

**Choice:**

- New OpenSpec capability `term-rate-settings-ui` (presentation requirements).
- Delta `term-rate-daily-upload` MODIFIED requirement “Term rate settings UI under Settings hub” to reference workflow + `/days` + replace-day confirm.
- On implementation, sync bullets into `specs/005-term-rate-settings/spec.md` (SDD Principle VI).

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| `window.confirm` feels dated | Accept for POC; swap for shared dialog when app has one |
| Confirm only checks view date, not CSV date | Document in lede; post-upload navigates to API `tradingDate` |
| Chip list grows long | Horizontal scroll row; API already newest-first |
| Styled file input accessibility | Keep native input focusable; style label/wrapper only |
| Duplicate CSS with `settings-table-wrap` | Extract `.settings-card` once |

## Migration Plan

Frontend-only deploy. No Flyway or contract rollout.

Manual smoke: Settings → Term rates → download sample → upload → chips switch days → re-upload shows confirm → desk return still works.

## Open Questions

- **Confirm copy:** Product-approved string for replace-day dialog?
- **Institution display names:** Worth a follow-up change with optional `displayName` on list DTO or client-side institution cache join?
- **Chip vs dropdown** for days: default chips; revisit if traders report clutter.
