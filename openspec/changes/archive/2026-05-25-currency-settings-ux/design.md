## Context

`managed-currencies-settings` delivered REST + domain behaviour and minimal Angular routes under `/settings/currencies`. The shell hides desk tabs on `/settings/*` (`showDeskNav()`), and the header exposes only a **Currencies** link with no complementary **Desk** link. Currency components use inline `styles:` with light-theme fallbacks (`#0f766e`, `#e2e8f0`) while the desk uses `styles.scss` tokens (`--mmx-accent` gold on dark background).

The list template already binds `active` to a **Status** text column, but plain text on an unstyled table is easy to miss; traders also expect to see **which tenors/notices** are enabled without opening edit.

## Goals / Non-Goals

**Goals:**

- One-click return from settings to the prior desk queue (or sensible default).
- Currency settings screens visually match order list / order details (tokens, typography, buttons, tables).
- List row shows catalog **active** state and **enabled tenor/notice** summary at a glance.
- Header shows **Desk** and **Currencies** with clear active indication.

**Non-Goals:**

- New settings domains (institutions, rates).
- Backend changes or persistence.
- Redesign of desk queue tables or order details.

## Decisions

### 1. Remember last desk route when entering settings

**Choice:** On navigation to `/settings/*`, store the current URL (if it matches `/oncall|term/{queue}`) in a small `DeskReturnService` or `sessionStorage` key. **Desk** header link and list-page back use that URL; if missing, fall back to `/oncall/received`.

**Alternatives:**

- Always `/oncall/received` — simpler but ignores spec “restore prior context”.
- Router state only (no service) — lost on refresh; sessionStorage is acceptable for POC.

**Rationale:** Matches delta intent from `managed-currencies-settings` navigation spec without coupling settings components to `App` internals.

### 2. Centralize settings styling on `--mmx-*` tokens

**Choice:** Remove teal/slate fallbacks from currency components; use shared classes in `app.scss` or `styles.scss` (e.g. `.settings-panel`, `.mmx-table`, `.btn-primary` mirroring `.btn-action`). Reuse table hover and link styles from `order-table` where practical.

**Alternatives:**

- New `settings-theme.scss` partial — fine if `app.scss` grows too large.

**Rationale:** Single source of truth; prevents regression when global theme tweaks.

### 3. List status: badge + rules chips

**Choice:**

- **Status:** pill/badge — `Active` (accent-dim border) vs `Inactive` (muted).
- **Rules:** second column or sub-row with comma-separated enabled tenor codes and notice codes from API payload (no new fields).

**Alternatives:**

- Icon-only — less accessible.
- Hide inactive currencies — out of scope; inactive must remain visible for reactivation.

### 4. Header nav pair

**Choice:** Header `nav` contains **Desk** (routerLink to stored return URL) and **Currencies** (`/settings/currencies`). Apply `.header-link.active` when `router.url.startsWith('/settings')` vs desk routes.

**Rationale:** Symmetric escape hatch; no reliance on browser back alone.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Stale return URL after long session | Fall back to `/oncall/received`; optional clear on logout later |
| Table width on small viewports with rules column | Horizontal scroll on `.settings-table-wrap`; abbreviate notice labels |
| Duplicated CSS between order-table and settings | Extract minimal shared table primitives only if duplication exceeds ~30 lines |

## Migration Plan

Frontend-only deploy. No Flyway or API rollout. Verify with `npm run test` and manual click-through: desk → currencies → desk, and list badges for active/inactive EUR/USD.

## Open Questions

- Should **Desk** label read **Orders** instead? (Product copy — default **Desk** in implementation unless user prefers **Orders**.)
- Show disabled tenors as grey chips or omit from list summary? (Default: show **enabled only** to keep rows compact.)
