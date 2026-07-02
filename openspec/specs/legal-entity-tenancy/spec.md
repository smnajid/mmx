# legal-entity-tenancy Specification

## Purpose

Tenancy model for MMX: an **Organisation** (4-character `OrganisationCode`) owning one or more **LegalEntities** (3-character `LegalEntityCode`), each with a hub/client role. Establishes `LegalEntityCode` as the data partition dimension for tenant-scoped aggregates.

## Requirements

### Requirement: Organisation and LegalEntity model

The system SHALL model tenancy as an **Organisation** (identified by a 4-character `OrganisationCode`, e.g. `LODH`, `HSBC`) owning one or more **LegalEntities**. Each LegalEntity SHALL be identified by a 3-character **`LegalEntityCode`** (e.g. `LON`, `PAR`, `LOC`) and SHALL belong to exactly one Organisation. An MMX deployment SHALL be configured with the `OrganisationCode` it serves and SHALL serve every LegalEntity of that Organisation.

#### Scenario: LegalEntity belongs to one Organisation

- **WHEN** a LegalEntity `PAR` is created within Organisation `LODH`
- **THEN** `PAR` is persisted with `organisationCode = LODH` and cannot belong to another Organisation

#### Scenario: Deployment serves its Organisation's entities

- **WHEN** an MMX deployment configured for `LODH` starts
- **THEN** all LegalEntities of `LODH` (e.g. `LOC`, `PAR`, `SIN`) are available within that deployment

---

### Requirement: LegalEntityCode is globally unique across organisations

`LegalEntityCode` SHALL be unique across the entire system — no two LegalEntities SHALL share the same code, even when they belong to different Organisations. `OrganisationCode` SHALL be unique across organisations.

#### Scenario: Duplicate LegalEntityCode rejected even in another organisation

- **WHEN** an attempt is made to create a LegalEntity with code `PAR` in Organisation `HSBC` while `PAR` already exists in `LODH`
- **THEN** the system rejects the creation with a uniqueness error

---

### Requirement: TradingHub or TradingClient role, mutually exclusive

Each LegalEntity SHALL have exactly one **role**: **TradingHub** (operates a trading desk) or **TradingClient** (no desk; orders routed to a hub). A LegalEntity SHALL NOT be both in V1. The role SHALL be configurable reference data.

#### Scenario: Hub role assigned

- **WHEN** LegalEntity `LOC` is configured as a TradingHub
- **THEN** `LOC` exposes the Desk to its users and may execute its own orders

#### Scenario: Client role assigned

- **WHEN** LegalEntity `PAR` is configured as a TradingClient
- **THEN** `PAR` has no desk and its users see Settings only

#### Scenario: Both roles rejected

- **WHEN** an attempt is made to mark a LegalEntity as both TradingHub and TradingClient
- **THEN** the system rejects the configuration

---

### Requirement: TradingClient connected to one TradingHub in the same Organisation

A TradingClient SHALL be connected to exactly one TradingHub within the **same Organisation**. Cross-organisation hub–client connections SHALL NOT be allowed in V1. The hub connection of a TradingClient MAY be reassigned to another TradingHub in the same Organisation; flipping a hub into a client (or vice versa) is deferred to V2.

#### Scenario: Client connected to a same-organisation hub

- **WHEN** TradingClient `PAR` (Organisation `LODH`) is connected to a TradingHub
- **THEN** the connected TradingHub (e.g. `LOC`) belongs to `LODH`

#### Scenario: Cross-organisation connection rejected

- **WHEN** an attempt is made to connect TradingClient `PAR` (`LODH`) to a TradingHub in `HSBC`
- **THEN** the system rejects the connection

#### Scenario: Hub connection reassigned within the organisation

- **WHEN** `PAR` is reassigned from hub `LOC` to hub `SIN` (both in `LODH`)
- **THEN** `PAR` is persisted as connected to `SIN` and new routing targets `SIN`

---

### Requirement: LegalEntity is the data partition dimension

Tenant-scoped aggregates SHALL carry their owning `LegalEntityCode`. A session scoped to one LegalEntity SHALL only read or mutate aggregates whose owning `LegalEntityCode` matches the active scope. Orders are the first tenant-scoped aggregate; reference-data LegalEntity scoping is deferred to a later change.

#### Scenario: Scoped query excludes other entities' data

- **WHEN** a session scoped to `PAR` queries tenant-scoped data
- **THEN** only aggregates owned by `PAR` are returned; aggregates owned by `LOC` are excluded
