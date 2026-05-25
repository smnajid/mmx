## Why

Managed currency settings shipped with correct API and domain behaviour, but the Angular screens feel disconnected from the desk: traders cannot easily return to order queues, the UI uses ad-hoc colours instead of the MMx desk theme, and catalog status/rules are hard to scan on the list. This change polishes the **trader settings UX** so configuration feels part of the same application as ON-CALL / Term desks.

## What Changes

- Add **explicit return to desk** from currency settings (header control and/or in-page back) that restores the last visited desk queue when possible.
- **Unify visual design** of currency list, onboard, and edit screens with global `--mmx-*` tokens (dark desk theme, gold accent, mono labels) used by order queues and order details.
- Improve **currency list readability**: clear **Active / Inactive** indication (badge or chip), and a concise summary of **enabled tenors** and **notice periods** per row.
- Style the header **Currencies** entry consistently with the desk shell (active state on settings routes).
- Extend **Vitest** coverage for navigation and list rendering.

**Out of scope:**

- Backend or OpenAPI changes (no new REST operations).
- Institution/rates settings (future programme phases).
- Functional changes to onboard/edit validation or intake policy.

No **BREAKING** API or routing changes; only frontend presentation and navigation ergonomics.

## Capabilities

### New Capabilities

- `currency-settings-ui`: Presentation and interaction standards for managed-currency list and edit flows (theme, status display, rules summary).

### Modified Capabilities

- `trader-desk-navigation`: Header navigation between desk and currency settings, including return-to-desk behaviour and styled settings entry (delta on main spec).

## Impact

- **Frontend (`frontend/`)**: `app.html` / `app.scss`, `currency-settings-list`, `currency-settings-edit`, possible shared `settings` or table styles; `app.spec.ts` and currency-settings component tests.
- **OpenSpec main specs**: Delta to `trader-desk-navigation` when archived; new `currency-settings-ui` capability.
- **Backend**: None.
- **Tests**: Vitest only (`npm run test`).
