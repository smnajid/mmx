# Tasks: async-schema-registry-governance

**Input**: [proposal.md](proposal.md), [design.md](design.md), [specs/](specs/)  
**Scope**: Externalize `OrderExecutedV1` JSON Schema, consolidate AsyncAPI + integration test on one file, register in Redpanda Schema Registry, auto-register on `mmx-start.sh`. No producer serializer or wire-format change.

**Format**: `[ID] [P?] Description with file path`

---

## Phase 1: Canonical schema file (contract-first)

**Purpose**: Single source of truth under the spec contracts tree before wiring references.

- [x] 1.1 Create `specs/002-trader-orders-views/contracts/schemas/OrderExecutedV1.json` — extract from inline `asyncapi.yaml` `components/schemas/OrderExecutedV1`; align with `backend/mmx-bootstrap/src/test/resources/contracts/order-executed-v1-payload.schema.json` (draft-07, same required/properties); add `$schema`, `$id` (`https://mmx.local/schemas/OrderExecutedV1.json`).

- [x] 1.2 Update `specs/002-trader-orders-views/contracts/asyncapi.yaml`: message `OrderExecutedV1` `payload` → `$ref: './schemas/OrderExecutedV1.json'`; remove inline `components/schemas/OrderExecutedV1` block.

- [x] 1.3 [P] SDD mirror: update `specs/002-trader-orders-views/contracts/asyncapi-v1.md` to document external schema path; update `specs/002-trader-orders-views/quickstart.md` golden-fixture reference from `order-executed-v1-payload.schema.json` to `contracts/schemas/OrderExecutedV1.json`.

---

## Phase 2: Integration test — canonical classpath (TDD gate)

**Purpose**: Tests load the spec file; remove hand-maintained mirror.

- [x] 2.1 Add `<testResource>` in `backend/mmx-bootstrap/pom.xml` mapping `specs/002-trader-orders-views/contracts/schemas` → classpath `contracts` (per design D3).

- [x] 2.2 Update `ExecutionHandoffKafkaIntegrationTest`: `SCHEMA_PATH = "/contracts/OrderExecutedV1.json"` in `backend/mmx-bootstrap/src/test/java/com/mmx/order/e2e/ExecutionHandoffKafkaIntegrationTest.java`.

- [x] 2.3 Delete `backend/mmx-bootstrap/src/test/resources/contracts/order-executed-v1-payload.schema.json`.

- [x] 2.4 Run `mvn test -pl mmx-bootstrap -Dtest=ExecutionHandoffKafkaIntegrationTest` from `backend/` — must pass (schema validation on outbox + Kafka record unchanged in behaviour).

---

## Phase 3: Schema Registry registration + dev stack

**Purpose**: Governance via Redpanda Schema Registry; idempotent local registration.

- [x] 3.1 Create `scripts/register-schemas.sh` — `PUT` subject compatibility `BACKWARD` on `mmx.order.executed-value`; `POST` JSON schema from `specs/002-trader-orders-views/contracts/schemas/OrderExecutedV1.json` (`schemaType: JSON`); default registry URL `http://localhost:18081`; exit non-zero on failure; idempotent on re-run.

- [x] 3.2 Update `mmx-start.sh` — after Redpanda healthcheck, invoke `scripts/register-schemas.sh` before starting the backend.

- [x] 3.3 [P] Manual smoke: with Docker stack up, run `./scripts/register-schemas.sh` then `curl -s http://localhost:18081/subjects/mmx.order.executed-value/versions/latest` — expect `200` and schema body matching canonical file.

---

## Phase 4: Validation & spec parity

**Purpose**: Confirm contracts parse; OpenSpec main specs synced on delivery.

- [x] 4.1 [P] Verify AsyncAPI resolves external ref (e.g. parse `asyncapi.yaml` with project tooling or AsyncAPI CLI if available).

- [x] 4.2 Run `mvn test` from `backend/` for modules touched (`mmx-bootstrap` minimum).

- [x] 4.3 On archive: sync delta `openspec/changes/async-schema-registry-governance/specs/` into `openspec/specs/async-schema-registry-governance/spec.md` (new) and `openspec/specs/back-office-outbound-messaging/spec.md` (delta merge).

---

## Dependencies

```
Phase 1 → Phase 2 → Phase 3 → Phase 4
```

- **1.1–1.2** before **2.1–2.4** (test needs canonical file on disk).
- **3.1** before **3.2** (start script calls register script).
- **2.4** before claiming Phase 2 complete.

## Parallel opportunities

- **1.3** and **4.1** can run in parallel after **1.2**.
- **4.2** and **4.3** at end in parallel.

## Task summary

| Metric | Value |
|--------|------:|
| Total tasks | 12 |
| Contract / SDD | 3 |
| Backend test wiring | 4 |
| Ops / dev stack | 3 |
| Validation | 2 |
