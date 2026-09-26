# Spec Delta

## ADDED Requirements

### Requirement: InstitutionUpdatedV1 JSON Schema has a single canonical file and is registry-governed

The `InstitutionUpdatedV1` JSON Schema SHALL live in exactly one canonical file, `contracts/004-institution-settings/schemas/InstitutionUpdatedV1.json`, which `contracts/004-institution-settings/asyncapi.yaml` references for the `mmx.institution.{legalEntityCode}` channel. The dev stack SHALL register the schema in the Redpanda Schema Registry on startup with `BACKWARD` compatibility, under subject `mmx.institution-value`, which is shared by every per-LegalEntity topic. An integration test SHALL validate a relayed payload against the canonical file.

#### Scenario: Single canonical file

- **WHEN** the contracts tree is searched for `InstitutionUpdatedV1` schema definitions
- **THEN** exactly one JSON Schema file defines it, and the AsyncAPI document references that file

#### Scenario: Registered on dev-stack startup

- **WHEN** the dev stack starts
- **THEN** subject `mmx.institution-value` exists in the Schema Registry with compatibility `BACKWARD`

#### Scenario: Relayed payload validates against the canonical schema

- **WHEN** the integration test onboards an institution and consumes the relayed `InstitutionUpdatedV1`
- **THEN** the payload validates against `contracts/004-institution-settings/schemas/InstitutionUpdatedV1.json`

---

### Requirement: OrderExecutedV1 counterparty account fields are backward-compatible

The `OrderExecutedV1` canonical schema SHALL add `institutionCode`, `counterpartyAccount`, and `clientCounterpartyAccount` (routing context) as **optional** properties. The Schema Registry SHALL accept the new version under subject `mmx.order.executed-value` with `BACKWARD` compatibility.

#### Scenario: New fields pass the compatibility gate

- **WHEN** the updated `OrderExecutedV1.json` is registered
- **THEN** the Schema Registry accepts it under `BACKWARD` compatibility
