## Context

`OrderExecutedV1` is currently defined in two places:

1. **Inline inside `asyncapi.yaml`** — under `components/schemas/OrderExecutedV1`; the canonical async contract
2. **`mmx-bootstrap/src/test/resources/contracts/order-executed-v1-payload.schema.json`** — a hand-maintained duplicate with a `$comment` describing it as a "mirror"; loaded by `ExecutionHandoffKafkaIntegrationTest` via classpath to validate the outbox payload and the Kafka record

These two must be kept in sync manually. Redpanda Schema Registry (`:18081`) is already running but unregistered, so schema evolution compatibility is enforced only by test fixtures, not by the infrastructure.

## Goals / Non-Goals

**Goals:**

- Single canonical schema file under the spec contracts tree; `asyncapi.yaml` and the integration test both resolve to it (no hand-maintained mirror)
- `asyncapi.yaml` payload becomes an external `$ref` (no inline schema)
- `OrderExecutedV1.json` registered in Redpanda Schema Registry under subject `mmx.order.executed-value` with `BACKWARD` compatibility for local dev and CI
- No wire format change; producer serialization (`OrderExecutedV1PayloadMapper`) unchanged

**Non-Goals:**

- Schema Registry client in the producer path (no Confluent `KafkaAvroSerializer` / `KafkaJsonSchemaSerializer`); Registry is governance-only for this change
- Avro or Protobuf wire format
- Consumer implementation
- Schema evolution tooling beyond registering the initial subject + compatibility mode

## Decisions

### D1. Canonical file location — in the spec contracts tree

```
specs/002-trader-orders-views/contracts/schemas/OrderExecutedV1.json
```

The schema is a contract artifact, not a backend implementation detail. Placing it alongside `asyncapi.yaml` makes the relationship explicit and keeps OpenSpec's single-source-of-truth principle intact. The existing test resource file (`order-executed-v1-payload.schema.json`) is removed once the integration test is redirected.

**Alternatives considered:**
- _Keep test resource as canonical; $ref to it from asyncapi.yaml_ — asyncapi tooling expects schema refs relative to the spec tree, not deep inside a Maven module. Cross-tree `$ref` paths are fragile and confusing.
- _Two files, both "canonical"_ — rejected; the existing $comment proves manual sync fails.

### D2. asyncapi.yaml — external `$ref`, schemaFormat unchanged

```yaml
payload:
  $ref: './schemas/OrderExecutedV1.json'
```

`defaultContentType: application/json` and the default JSON Schema `schemaFormat` are unchanged. AsyncAPI 3 tools (AsyncAPI Studio, `@asyncapi/parser`) resolve external JSON file refs natively. No schema format annotation is required when the file extension is `.json`.

**Alternatives considered:**
- _`schemaFormat: application/vnd.aai.asyncapi+json` explicit annotation_ — unnecessary; default resolves correctly.

### D3. Integration test — classpath via Maven test-resource inclusion

`ExecutionHandoffKafkaIntegrationTest` loads the schema at:
```
SCHEMA_PATH = "/contracts/order-executed-v1-payload.schema.json"
```

The simplest migration: add the canonical schema directory as a Maven `<testResource>` in `mmx-bootstrap/pom.xml`, keeping the classpath path stable by placing the file under `contracts/` inside the schemas directory, and rename the constant to reflect the new source:

```xml
<testResource>
  <directory>${project.basedir}/../../specs/002-trader-orders-views/contracts/schemas</directory>
  <targetPath>contracts</targetPath>
</testResource>
```

The test constant becomes `SCHEMA_PATH = "/contracts/OrderExecutedV1.json"`. The old test resource file (`order-executed-v1-payload.schema.json`) is deleted.

**Alternatives considered:**
- _Filesystem path load via `Path.of(System.getProperty("user.dir"))` in the test_ — fragile; Maven working directory varies by invocation context (root vs module run).
- _Maven resources plugin `copy-resources` goal_ — adds a build step for a file that already exists; preferred to avoid generated test resources.

### D4. Schema Registry registration — REST API, not producer interceptor

Register `OrderExecutedV1.json` against Redpanda Schema Registry directly via its REST API (Confluent-compatible, port `:18081`):

```
POST /subjects/mmx.order.executed-value/versions
Content-Type: application/vnd.schemaregistry.v1+json

{ "schemaType": "JSON", "schema": "<escaped JSON Schema content>" }
```

Compatibility mode set on the subject to `BACKWARD` (additive-only evolution):
```
PUT /config/mmx.order.executed-value
{ "compatibility": "BACKWARD" }
```

A `scripts/register-schemas.sh` script wraps these calls for local dev. For CI, the integration test can optionally assert the schema is registered (read-only check), but registration itself is not yet wired into the Maven build (a follow-up concern).

**Why BACKWARD?** Allows adding optional fields in future `OrderExecutedV1` versions without breaking consumers that were compiled against an older version. FULL would also require consumers to tolerate field removal — overkill for a known single consumer.

**Alternatives considered:**
- _FORWARD compatibility_ — protects producer from consumer schema drift; less relevant when mmx owns both sides of this contract.
- _Schema Registry client in `OrderExecutedV1PayloadMapper`_ — adds producer dependency on registry availability at runtime; rejected for POC simplicity.

### D5. Content schema file — keep draft-07, align `$id`

The existing test resource already uses `$schema: http://json-schema.org/draft-07/schema#` (draft-07), which matches AsyncAPI 3's default schema dialect. Keep draft-07 in the extracted file and add a stable `$id`:

```json
"$id": "https://mmx.local/schemas/OrderExecutedV1.json"
```

This gives the schema a stable identity for Schema Registry subject correlation without requiring a live URI.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| asyncapi.yaml `$ref` breaks tooling that only handles inline schemas | `@asyncapi/parser` and AsyncAPI Studio both support external file refs natively; verify with a quick parse check |
| Maven test-resource directory path uses `../..` traversal | Accepted for POC; path is relative to `mmx-bootstrap` which is predictably two levels below the repo root |
| Schema Registry unavailable in CI (no Redpanda Testcontainer) | Registration script is decoupled from the test; integration test validates schema shape via networknt, not via Registry; CI still green without Registry |
| Schema drift between `OrderExecutedV1.json` and `OrderExecutedV1PayloadMapper` | No change to producer logic; the golden-fixture integration test (`ExecutionHandoffKafkaIntegrationTest`) continues to validate the payload against the schema file on every test run |

## Migration Plan

1. Create `specs/002-trader-orders-views/contracts/schemas/OrderExecutedV1.json` — extract the inline schema from `asyncapi.yaml`, add `$id`, align field-level constraints with test resource (no behavioral delta)
2. Update `asyncapi.yaml` `payload` → `$ref: './schemas/OrderExecutedV1.json'`; remove the inline `components/schemas/OrderExecutedV1` block
3. Add `<testResource>` to `mmx-bootstrap/pom.xml` pointing to the schemas directory; update `SCHEMA_PATH` constant in the test; delete `order-executed-v1-payload.schema.json`
4. Run `mvn test -pl mmx-bootstrap` — integration test must stay green
5. Add `scripts/register-schemas.sh` — wraps the two `curl` calls (set compatibility + POST version); document in quickstart

No rollback complexity: if the `$ref` breaks a tool, reverting to inline is a one-line YAML change. No data migration or runtime behaviour change.

## Open Questions — Resolved

- **`mmx-start.sh` auto-registration**: `register-schemas.sh` SHALL be invoked automatically from `mmx-start.sh` after the Redpanda healthcheck passes, before the backend starts. Registration is idempotent (Redpanda returns the existing schema ID on duplicate content), so re-runs are safe.
