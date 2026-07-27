## MODIFIED Requirements

### Requirement: TradingClient connected to one TradingHub in the same Organisation

A TradingClient SHALL be connected to exactly one TradingHub. The connected TradingHub MAY be in the **same Organisation** (local routing — same deployment, synchronous and atomic) or in a **different Organisation** (remote routing — cross-deployment, eventually consistent). The TradingClient relationship is a **hub-owned membership that may span organisations**: a TradingHub's client list may include LegalEntities from other Organisations, and a TradingClient's `connectedHubCode` may point at a hub in another Organisation. The connection SHALL be **bidirectional**: the client deployment holds `connectedHubCode` pointing at the hub, and the hub deployment holds the client in the hub's TradingClient list. The hub connection of a TradingClient MAY be reassigned to another TradingHub; flipping a hub into a client (or vice versa) is deferred to V2. Local vs remote is **derived** from whether the connected hub's `OrganisationCode` matches the deployment's own — not a stored flag.

#### Scenario: Client connected to a same-organisation hub

- **WHEN** TradingClient `PAR` (Organisation `LODH`) is connected to a TradingHub
- **THEN** the connected TradingHub (e.g. `LOC`) belongs to `LODH` and routing is local (synchronous, same-deployment)

#### Scenario: Client connected to a cross-organisation hub

- **WHEN** TradingClient `CGD` (Organisation `CGED`) is connected to TradingHub `LOC` (Organisation `LODH`)
- **THEN** the connection is bidirectional (CGED holds `connectedHubCode=LOC`; LODH holds CGD in LOC's client list) and routing is remote (cross-deployment, eventually consistent)

#### Scenario: Hub connection reassigned

- **WHEN** a TradingClient is reassigned from one hub to another
- **THEN** the client is persisted as connected to the new hub and new routing targets it

#### Scenario: Local vs remote is derived

- **WHEN** a TradingClient's `connectedHubCode` is resolved
- **THEN** the system derives `isLocalHub` or `isRemoteHub` by comparing the connected hub's `OrganisationCode` to the deployment's own `OrganisationCode`, not by reading a stored flag
