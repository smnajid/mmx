# async-schema-registry-governance Specification

## Purpose

Governance and auto-setup rules for the `OrderExecutedV1` schema registry integration. Ensures single source of truth for the schema, and registers it in the Redpanda Schema Registry with backward compatibility checks.

## Requirements

### Requirement: OrderExecutedV1 JSON Schema has a single canonical file

The `OrderExecutedV1` schema SHALL be defined in exactly one file: `contracts/002-trader-orders-views/schemas/OrderExecutedV1.json`. All other representations (inline schema in `asyncapi.yaml`, test resource copies) SHALL be removed in favour of references to this file. The file SHALL include a `$schema` declaration (draft-07) and a stable `$id`.

#### Scenario: No inline schema in asyncapi.yaml

- **WHEN** `contracts/002-trader-orders-views/asyncapi.yaml` is inspected
- **THEN** the `OrderExecutedV1` payload is a `$ref` to `./schemas/OrderExecutedV1.json` and no inline schema exists under `components/schemas`

#### Scenario: No hand-maintained mirror in test resources

- **WHEN** the backend test resources are inspected
- **THEN** `order-executed-v1-payload.schema.json` no longer exists and the integration test loads the schema from the canonical spec file

#### Scenario: Schema file is valid draft-07

- **WHEN** `OrderExecutedV1.json` is parsed by a JSON Schema validator
- **THEN** it validates cleanly against the draft-07 meta-schema with no errors

---

### Requirement: OrderExecutedV1 JSON Schema is registered in Redpanda Schema Registry

The canonical `OrderExecutedV1.json` SHALL be registered in the Redpanda Schema Registry under subject `mmx.order.executed-value` with schema type `JSON` and compatibility mode `BACKWARD`. Registration SHALL be idempotent — re-registering the same schema content returns the existing schema ID without error.

#### Scenario: Subject exists after registration

- **WHEN** `scripts/register-schemas.sh` is executed against a running Redpanda instance
- **THEN** `GET /subjects/mmx.order.executed-value/versions/latest` returns a `200` response with the registered schema

#### Scenario: Compatibility mode is BACKWARD

- **WHEN** the subject compatibility is queried via `GET /config/mmx.order.executed-value`
- **THEN** the response contains `"compatibility": "BACKWARD"`

#### Scenario: Re-registration is safe

- **WHEN** `register-schemas.sh` is executed twice against the same Redpanda instance with unchanged schema content
- **THEN** both executions succeed and the subject has exactly one schema version

---

### Requirement: Dev stack registers schema automatically on startup

The `mmx-start.sh` launcher SHALL invoke `scripts/register-schemas.sh` automatically after the Redpanda healthcheck passes and before the backend process starts. This ensures every local dev environment has the schema registered without manual steps.

#### Scenario: Schema registered before backend starts

- **WHEN** `mmx-start.sh` is run from a clean Redpanda state
- **THEN** the subject `mmx.order.executed-value` exists in the Schema Registry before the Spring Boot process begins

#### Scenario: Stack startup succeeds even if schema was already registered

- **WHEN** `mmx-start.sh` is run a second time without stopping Redpanda
- **THEN** the registration step completes without error and the backend starts normally

---

### Requirement: Integration test validates payload against canonical schema file

The `ExecutionHandoffKafkaIntegrationTest` SHALL load the `OrderExecutedV1` schema from the canonical spec file (via Maven test-resource inclusion) and validate the outbox payload and the Kafka record value against it. The test MUST NOT maintain a local copy of the schema.

#### Scenario: Integration test passes with canonical schema

- **WHEN** `mvn test -pl mmx-bootstrap` is run after this change
- **THEN** `ExecutionHandoffKafkaIntegrationTest` loads `OrderExecutedV1.json` from the spec contracts tree and all assertions pass

#### Scenario: Schema drift is caught by the test

- **WHEN** `OrderExecutedV1PayloadMapper` produces a payload missing a required field
- **THEN** the integration test fails with a schema validation error identifying the missing field
