## ADDED Requirements

### Requirement: Term rates are hub-owned and client-read-only

Term rates SHALL be owned and managed by the TradingHub LegalEntity. A user with the Trader role on a TradingHub SHALL upload and replace term rate sets. A `ClientRepresentative` on a TradingClient SHALL read the connected hub's term rates for granted institutions (in-process, same deployment) and SHALL NOT upload, replace, or otherwise mutate term rates. A TradingClient's indicative-rate views for a proxy institution SHALL resolve to the hub's term rates for the referenced native institution.

#### Scenario: ClientRepresentative reads the hub's term rates

- **WHEN** a `ClientRepresentative` on `PAR` requests term rates for a trading day for proxy `BNP via LOC`
- **THEN** the system returns `LOC`'s term rates for native institution `BNP` for the granted currencies (read-only)

#### Scenario: ClientRepresentative cannot upload term rates

- **WHEN** a `ClientRepresentative` on `PAR` attempts a term rate upload
- **THEN** the system rejects the request with an authorisation error and no term rates are changed
