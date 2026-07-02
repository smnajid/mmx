## MODIFIED Requirements

### Requirement: Header provides Desk and Settings navigation

The application header SHALL expose trader-facing navigation entries: one that returns to the **desk** (order queues) and one that opens **settings**. Both SHALL use consistent header styling (typography, colour, hover, active state) aligned with the MMx shell. On any route under `/settings`, the **Settings** entry SHALL appear active; on desk queue routes, the **Desk** entry SHALL appear active. The header MUST NOT show a separate **Currencies** link (currencies are reached via the Settings hub). The **Desk** entry SHALL be shown only when the active session role is **Trader** (i.e. the active LegalEntity is a TradingHub); when the active role is **ClientRepresentative** (a TradingClient), the header SHALL show **Settings** only and SHALL NOT show the **Desk** entry.

#### Scenario: Desk link visible on settings

- **WHEN** a Trader (TradingHub) is on `/settings/institutions` or `/settings/currencies`
- **THEN** a **Desk** header entry is visible and navigates away from settings

#### Scenario: Settings link active on settings routes

- **WHEN** the user is on any `/settings` route (including `/settings/currencies` or `/settings/institutions`)
- **THEN** the **Settings** header entry is visually indicated as active

#### Scenario: Desk link active on queue routes

- **WHEN** a Trader is on `/term/received`
- **THEN** the **Desk** header entry is visually indicated as active and **Settings** is not active

#### Scenario: Desk entry hidden for ClientRepresentative

- **WHEN** the active role is `ClientRepresentative` (a TradingClient)
- **THEN** the header shows **Settings** only and does not show a **Desk** entry

## ADDED Requirements

### Requirement: Role-gated desk route access

The application SHALL gate desk queue routes (`/oncall/{queue}` and `/term/{queue}`) to sessions whose active role is **Trader** on a TradingHub. A session whose active role is **ClientRepresentative** SHALL be blocked from desk routes and redirected to Settings. Desk queue tab strips SHALL not render for a ClientRepresentative.

#### Scenario: ClientRepresentative redirected from desk

- **WHEN** a ClientRepresentative navigates to `/oncall/received`
- **THEN** the application blocks the route and redirects to the Settings entry

#### Scenario: Trader reaches desk queue

- **WHEN** a Trader on a TradingHub navigates to `/term/assigned`
- **THEN** the desk queue renders with the two-tier tabs as specified
