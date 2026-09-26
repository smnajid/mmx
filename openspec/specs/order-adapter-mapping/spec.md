# order-adapter-mapping Specification

## Purpose

Automated tests and encoding rules for mapping the **Money Market Order** aggregate between domain, JPA `OrderEntity`, and OpenAPI-generated REST DTOs.

## Requirements

### Requirement: Persistence mapper round-trip fidelity

`OrderPersistenceMapper` SHALL map every persisted `OrderEntity` field used by the Money Market Order aggregate to and from `MoneyMarketOrder` without loss for test fixtures covering RECEIVED, ASSIGNED, and EXECUTED (with `HandoffStatus`) orders.

#### Scenario: RECEIVED order round-trips

- **WHEN** a RECEIVED Term order with tenor and notice period is mapped to entity and back
- **THEN** the domain order equals the source on id, status, tenor, notice period, and amount (`BigDecimal`)

#### Scenario: EXECUTED order preserves handoff and execution

- **WHEN** an EXECUTED order with execution details and `HandoffStatus.PENDING` is mapped to entity and back
- **THEN** execution fields and handoff status match the source aggregate

### Requirement: REST mapper encoding rules

`OrderRestMapper` SHALL apply stable encoding rules documented by tests:

- Tenor and notice period on **summary/details** responses use domain **codes** (e.g. `3M`, `24H`), not Java enum names.
- Amounts and rates on wire use `double` converted from domain `BigDecimal` without unintended rounding for fixture values.
- `handoffStatus` appears on **OrderSummary** only when status is EXECUTED and handoff is non-null; it MUST NOT appear on details mapping unless OpenAPI adds the property.

#### Scenario: Summary exposes handoff for executed row

- **WHEN** `toSummary` maps an EXECUTED order with `HandoffStatus.PENDING`
- **THEN** the summary DTO includes handoff status `PENDING`

#### Scenario: Tenor code on wire

- **WHEN** `toSummary` maps an order with `Tenor._3M`
- **THEN** the summary tenor string is `3M`

### Requirement: Dedicated mapper unit tests

The codebase SHALL include automated unit tests for `OrderRestMapper` and `OrderPersistenceMapper` in their respective adapter modules, independent of controller or repository integration tests.

#### Scenario: Mapper tests run in CI

- **WHEN** backend unit tests execute in `mmx-adapter-in-rest` and `mmx-adapter-out-persistence`
- **THEN** mapper test classes fail if field mapping or encoding rules regress
