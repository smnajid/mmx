# currency-settings-ui Specification

## Purpose

Presentation and interaction standards for managed-currency list, onboard, and edit flows in the Angular trader application: desk-aligned theme, status display, rules summary, and local navigation.

## Requirements

### Requirement: Settings screens use desk visual theme

Currency settings, institution settings, and the shared **Settings** hub shell SHALL use the same global design tokens as desk order views (`--mmx-bg`, `--mmx-surface`, `--mmx-border`, `--mmx-text`, `--mmx-text-muted`, `--mmx-accent`, `--mmx-accent-dim`, `--font-display`, `--font-mono`). Primary actions, tables, links, form controls, and sub-navigation SHALL NOT rely on unrelated palette fallbacks (e.g. teal accent on light gray borders) that are absent from desk queues.

#### Scenario: List matches desk chrome

- **WHEN** the trader opens the managed currency list
- **THEN** page background, text colour, and accent highlights are visually consistent with the ON-CALL / Term order list screens

#### Scenario: Institution list matches desk chrome

- **WHEN** the trader opens the institution settings list
- **THEN** page background, text colour, and accent highlights are visually consistent with the currency settings list and desk queues

#### Scenario: Primary action uses MMx accent

- **WHEN** the trader views **Onboard currency** or **Onboard institution** on a list screen
- **THEN** the control uses the same accent colour family as desk action buttons (gold `--mmx-accent`), not a separate default palette

---

### Requirement: Settings hub sub-nav uses desk theme

The in-settings sub-navigation strip (**Currencies** | **Institutions**) SHALL use the same typography and colour tokens as other settings chrome and SHALL visually integrate with the settings shell below the application header.

#### Scenario: Sub-nav readable on dark settings background

- **WHEN** the trader views any `/settings` route
- **THEN** the active sub-nav section is distinguishable from inactive sections using MMx theme tokens

---

### Requirement: Currency list shows catalog status clearly

The managed currency list SHALL display each currency’s catalog **active** state so traders can distinguish intake-allowed (**Active**) from deactivated (**Inactive**) entries without opening edit. The indication SHALL be visually distinct from body text alone (e.g. status badge or chip).

#### Scenario: Active currency shows Active badge

- **WHEN** the list includes a currency with `active: true`
- **THEN** the row shows an **Active** status indication that is immediately visible in the list layout

#### Scenario: Inactive currency shows Inactive badge

- **WHEN** the list includes a currency with `active: false`
- **THEN** the row shows an **Inactive** status indication distinct from **Active**

---

### Requirement: Currency list summarizes enabled rules

The list SHALL show, for each currency, which **tenors** and **notice periods** are currently enabled (as configured on the catalog entry), so traders can scan desk rules without opening edit. Only enabled values SHALL be listed; disabled values MAY be omitted from the summary.

#### Scenario: Term-enabled tenors visible on list

- **WHEN** EUR has tenors `1M` and `3M` enabled in the catalog
- **THEN** the EUR row displays a summary that includes `1M` and `3M` (or equivalent readable labels)

#### Scenario: OnCall notice periods visible on list

- **WHEN** a currency has `24H` notice enabled and `48H` disabled
- **THEN** the row summary includes `24H` and does not imply `48H` is enabled

---

### Requirement: Edit screen retains local back navigation

The currency edit and onboard screens SHALL continue to provide a control that returns to the currency list. Styling of that control SHALL follow the desk theme.

#### Scenario: Back to list from edit

- **WHEN** the trader is on `/settings/currencies/EUR` (edit)
- **THEN** a visible control navigates to `/settings/currencies`
