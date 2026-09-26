## Why

The `OrderExecutedV1` JSON Schema is defined inline inside `asyncapi.yaml`, making it invisible to Redpanda Schema Registry (already running on `:18081` but never used). This means there is no single canonical schema file shared between the AsyncAPI contract, the golden fixture integration test, and the registry — schema drift across those three surfaces is only caught manually.

## What Changes

- Extract the inline `OrderExecutedV1` JSON Schema from `asyncapi.yaml` into a standalone external file (`specs/002-trader-orders-views/contracts/schemas/OrderExecutedV1.json`)
- Update `asyncapi.yaml` `payload` to use `$ref: '../schemas/OrderExecutedV1.json'` instead of the inline definition
- Register the schema file in Redpanda Schema Registry under subject `mmx.order.executed-value` (JSON Schema type, BACKWARD compatibility) for local dev and CI
- Update the golden fixture reference in the messaging integration test to resolve against the same external file
- Wire format stays JSON and the `defaultContentType` stays `application/json` — **no breaking change** for any consumer

## Capabilities

### New Capabilities

- `async-schema-registry-governance`: Governance rules for keeping the `OrderExecutedV1` JSON Schema authoritative across AsyncAPI contract, Schema Registry, and integration test fixture; including subject naming, compatibility mode, and registration workflow for local and CI environments.

### Modified Capabilities

- `back-office-outbound-messaging`: The "no ad-hoc Kafka payloads" requirement gains a stronger enforcement point — the canonical schema file is now also registered in Redpanda Schema Registry, making registry compatibility checks the runtime gate rather than test-only validation.

## Impact

- `specs/002-trader-orders-views/contracts/asyncapi.yaml` — `payload` section replaced with external `$ref`
- New file: `specs/002-trader-orders-views/contracts/schemas/OrderExecutedV1.json`
- `backend/mmx-adapter-out-messaging` — integration test golden fixture path updated to resolve against the external schema file
- Redpanda Schema Registry (`:18081`) — subject `mmx.order.executed-value` registered; no producer serializer change (stays Jackson JSON)
- `docker-compose.yml` / local dev tooling — no structural change; Schema Registry already exposed
- No OpenAPI, frontend, or domain model changes
