## Why

Term rate settings (`/settings/term-rates`) shipped with correct ingest API and minimal UI, but the screen feels disconnected from the MMx desk: native file/date controls, split sections without elevated surfaces, and catalog-style layout for a **daily morning workflow**. The backend already exposes `GET /term-rates/days` for date discovery, yet the Angular screen does not use it. Traders need a coherent “prepare → upload → review” experience aligned with `currency-settings-ui` and desk queue patterns.

**Programme context:** Builds on completed [`term-rate-daily-upload`](../term-rate-daily-upload/) (T-01–T-05, U-02). No change to ingest rules or REST contracts unless noted in design.

## What Changes

- Reframe Term rates as a **guided morning workflow** (lede, step-oriented sections, card/surface layout) rather than two anonymous sub-headings on a flat page.
- **Style upload and date controls** with shared `settings.scss` tokens (mono labels, borders, focus ring) — no browser-default chrome on primary paths.
- **Wire `GET /api/v1/settings/term-rates/days`** into the day picker (chips or quick-select for dates that have uploads).
- Add **day summary** above the rates table (row count, last upload time derived from loaded rates or last upload response).
- **Replace-day confirmation** when uploading would overwrite existing rates for the trading day (T-02 guardrail).
- **Actionable empty state** (prompt to download sample, then upload).
- Consolidate error styling (`.settings-error`, shared row-error list) and move component-local button styles into shared settings CSS where duplicated.
- Extend **Vitest** coverage for days picker, replace-day confirm, day summary, and styled controls.

**Out of scope:**

- Backend or OpenAPI changes (no new fields on `TermRate`; institution `displayName` in table deferred).
- Applying rates to orders, OnCall curves, or Kafka handoff.
- Drag-and-drop upload (optional future polish).

No **BREAKING** API or routing changes.

## Capabilities

### New Capabilities

- `term-rate-settings-ui`: Presentation and interaction standards for the Term rates settings workflow (theme, workflow layout, day discovery, replace-day guard, day summary).

### Modified Capabilities

- `term-rate-daily-upload`: Strengthen UI requirements in the Term rate settings screen requirement (days endpoint usage, replace-day confirm, workflow presentation) — delta only; ingest and REST behaviour unchanged.

## Impact

- **Frontend (`frontend/`)**: `term-rate-settings.component.ts`, `settings.scss`, possibly small shared helpers; `term-rate-settings.component.spec.ts`.
- **Feature specs (SDD)**: Update `specs/005-term-rate-settings/spec.md` user-facing UX bullets when implementing (parity with OpenSpec delta).
- **OpenSpec main specs**: New `term-rate-settings-ui` capability on archive; delta to `term-rate-daily-upload` when that capability is merged to `openspec/specs/`.
- **Backend**: None.
- **Tests**: Vitest only (`npm run test` in Final verification).
