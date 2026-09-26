# currency-settings-ui Specification (delta)

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Settings hub sub-nav uses desk theme

The in-settings sub-navigation strip (**Currencies** | **Institutions**) SHALL use the same typography and colour tokens as other settings chrome and SHALL visually integrate with the settings shell below the application header.

#### Scenario: Sub-nav readable on dark settings background

- **WHEN** the trader views any `/settings` route
- **THEN** the active sub-nav section is distinguishable from inactive sections using MMx theme tokens
