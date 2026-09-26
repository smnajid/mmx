## Why

Desk navigation today uses two stacked link rows in the header labeled “On-call workspace” and “Term workspace,” which reads like separate destinations rather than a single desk with two product modes. Traders already think in **ON-CALL** vs **Term** tabs with **Received / Assigned / Executed** sub-tabs (per feature 002); aligning the chrome with that model reduces cognitive load and matches the original spec intent without changing routing or API behaviour.

## What Changes

- Replace header workspace buttons with **primary tabs** labeled **ON-CALL** and **Term** (no “Workspace” in trader-facing chrome).
- Keep **sub-tabs** **Received**, **Assigned**, and **Executed** under the active primary tab; both tiers remain visually distinguished on every desk queue screen and on order details when `?ws=&queue=` context is present (unchanged behaviour, new placement).
- Move primary + sub tab strips from the top bar into **main content**, directly below the brand row (screen-oriented layout); top bar retains brand and trader identity only.
- Standardize trader-facing copy to **ON-CALL** (all caps, hyphenated) for the OnCall product mode; align page titles and help text that still say “On call” / “workspace.”
- Introduce a **responsive** tab layout for narrow viewports: horizontal scroll for tab rows (no wrap that breaks hierarchy), touch-friendly hit targets, and preserved two-tier visibility (no collapsing sub-tabs behind primary).
- Update feature **002** navigation acceptance wording to describe tab placement and labels (no REST or domain changes).

## Capabilities

### New Capabilities

_None — UX-only change within existing trader desk capability._

### Modified Capabilities

- `trader-desk-navigation`: Delta updates navigation chrome requirements (tab labels **ON-CALL** / **Term**, nested sub-tabs, placement in main content, mobile scroll behaviour, order-details highlight rules unchanged in substance).

## Impact

- **Frontend**: `app.html`, `app.scss`, `app.ts`, `app.spec.ts`; optional list component headings (`oncall-order-list`, `assigned-order-list`, `executed-order-list`) for label consistency.
- **Specs (SDD)**: `specs/002-trader-orders-views/spec.md` (User Story 1 navigation clauses); no OpenAPI, Flyway, or backend modules.
- **Tests**: Angular unit tests for nav highlighting and selectors; Cypress/quickstart manual checks if e2e covers desk nav.
- **Out of scope**: URL paths (`/oncall/received`, etc.), default OnCall session entry, API workspace scoping, order-detail query contract.
