## ADDED Requirements

### Requirement: OnCall rate curves are hub-owned and client-read-only

OnCall rate curves SHALL be owned and managed by the TradingHub LegalEntity. A user with the Trader role on a TradingHub SHALL add and cancel OnCall rate segments. A `ClientRepresentative` on a TradingClient SHALL read the connected hub's OnCall curves for granted institutions (in-process, same deployment) and SHALL NOT add, cancel, or otherwise mutate OnCall rate segments. A TradingClient's indicative-rate views for a proxy institution SHALL resolve to the hub's OnCall curves for the referenced native institution.

#### Scenario: ClientRepresentative reads the hub's OnCall curves

- **WHEN** a `ClientRepresentative` on `PAR` requests the OnCall curve for proxy `BNP via LOC` for a currency/notice period
- **THEN** the system returns `LOC`'s OnCall curve for native institution `BNP` for that currency/notice period (read-only)

#### Scenario: ClientRepresentative cannot add or cancel OnCall rates

- **WHEN** a `ClientRepresentative` on `PAR` attempts to add or cancel an OnCall rate segment
- **THEN** the system rejects the request with an authorisation error and no OnCall curve is changed
