# trader-desk-navigation Specification (delta)

## ADDED Requirements

### Requirement: Header provides Desk and Currencies navigation

The application header SHALL expose two trader-facing navigation entries: one that returns to the **desk** (order queues) and one that opens **currency settings**. Both SHALL use consistent header styling (typography, colour, hover, active state) aligned with the MMx shell. On settings routes, the Currencies entry SHALL appear active; on desk queue routes, the Desk entry SHALL appear active.

#### Scenario: Desk link visible on settings list

- **WHEN** the trader is on `/settings/currencies`
- **THEN** a **Desk** (or equivalent orders) header entry is visible and navigates away from settings

#### Scenario: Currencies link active on settings

- **WHEN** the trader is on any `/settings/currencies` route
- **THEN** the Currencies header entry is visually indicated as active

#### Scenario: Desk link active on queue routes

- **WHEN** the trader is on `/term/received`
- **THEN** the Desk header entry is visually indicated as active and Currencies is not active

---

### Requirement: Return from settings restores desk context

When the trader navigates from a desk queue screen to currency settings, the application SHALL remember that desk URL. Activating the Desk header entry or an explicit **Back to desk** control on the currency list SHALL navigate to the remembered URL. If no desk URL was remembered, navigation SHALL fall back to `/oncall/received`.

#### Scenario: Return to Term Assigned after visiting settings

- **WHEN** the trader was on `/term/assigned`, opened Currencies from the header, then activates Desk
- **THEN** the application navigates to `/term/assigned`

#### Scenario: Default desk when no prior queue

- **WHEN** the trader opens Currencies directly (no prior desk route in the session)
- **THEN** activating Desk navigates to `/oncall/received`

#### Scenario: List offers back to desk

- **WHEN** the trader views the currency settings list
- **THEN** a visible control (header Desk and/or in-page link) returns to the desk without requiring browser back
