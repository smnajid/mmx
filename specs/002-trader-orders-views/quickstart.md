# Quickstart: User Story 1 — Term vs OnCall workspace

**Goal**: Verify workspace-scoped lists and default **OnCall** entry without cross-type leakage.

## Preconditions

- Backend and DB running per [001 quickstart](../001-mm-order-processing/quickstart.md) (same stack). On **GitHub Codespaces**, use the **GitHub Codespaces** section there (dev container + forwarded ports).
- OpenAPI codegen in `mmx-adapter-in-rest` points at `specs/002-trader-orders-views/contracts/openapi.yaml` after implementation tasks (see [plan.md](plan.md)).

## 1. Seed two orders (Term + OnCall)

Use Portfolio intake (`POST /api/v1/orders`) twice with the same baseline payload shape, varying only `orderType` (`TERM` vs `ON_CALL`) and required tenor/notice fields.

## 2. API checks (Trader header)

Replace `BASE` and supply a valid `X-Trader-Id`.

```bash
export BASE=http://localhost:8080
export TID=alice

curl -s "$BASE/api/v1/orders/term/received?page=0&size=20"  -H "X-Trader-Id: $TID" | jq '.content[].orderType' | uniq
# Expect only TERM

curl -s "$BASE/api/v1/orders/oncall/received?page=0&size=20" -H "X-Trader-Id: $TID" | jq '.content[].orderType' | uniq
# Expect only ON_CALL

# After implementation of 002 list endpoints:
curl -s "$BASE/api/v1/orders/term/assigned?page=0&size=20"   -H "X-Trader-Id: $TID" | jq '.content[].orderType' | uniq
curl -s "$BASE/api/v1/orders/oncall/assigned?page=0&size=20" -H "X-Trader-Id: $TID" | jq '.content[].orderType' | uniq
```

**Accept**: No response body mixes `TERM` and `ON_CALL` in `content[]` for a single workspace-scoped URL.

### User Story 2 — desk-wide Assigned

After assigning an order as `alice`, the same row MUST appear on `GET .../term/assigned` or `.../oncall/assigned` when called with a **different** `X-Trader-Id` (e.g. `bob`). Legacy `GET /api/v1/orders/assigned` remains assignee-scoped.

```bash
# Assign as alice, then list as bob — order id should still appear in the workspace Assigned response.
```

## 3. Angular checks

1. Open the app root — **without** manually choosing **Term**, the first screen MUST land on the **ON-CALL** primary tab and **Received** sub-tab (default route).
2. Use desk tabs in the main content area: primary **ON-CALL** / **Term**, sub-tabs **Received** / **Assigned** / **Executed** (no “Workspace” in tab labels).
3. Switch to **Term** — Received (and Assigned / Executed) MUST show **only** Term rows.
4. Switch back to **ON-CALL** — lists MUST show **only** OnCall rows; Term rows MUST NOT linger from the prior view (same session).
5. Full page reload — default entry MUST be **ON-CALL** > **Received** again (no persisted last primary tab).
6. Narrow viewport (~320px): both tab rows remain visible; horizontal scroll reaches **Executed** on **ON-CALL**.

## 4. Regression

- Legacy `GET /api/v1/orders/assigned` is **deprecated** in OpenAPI v1.1.0; once clients migrate, remove usage from the SPA.

---

## 5. Back-office Kafka handoff (FR-013)

### Kafka topic

The relay publishes executed-order events to a single topic:

```
mmx.order.executed
```

**Topic creation** is handled automatically by Redpanda in dev-container mode. For production or a dedicated Kafka cluster create it manually before deploying:

```bash
# Using rpk (Redpanda CLI) against the local container
docker exec mmx-redpanda rpk topic create mmx.order.executed --partitions 1 --replicas 1

# Using kafka-topics.sh against a standard Kafka cluster
kafka-topics.sh --bootstrap-server <host>:9092 \
  --create --topic mmx.order.executed \
  --partitions 1 --replication-factor 1
```

### Config keys (`mmx.backoffice.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `mmx.backoffice.kafka.topic` | `mmx.order.executed` | Topic the outbox relay publishes to |
| `mmx.backoffice.outbox.relay-enabled` | `true` | Set `false` to disable the scheduler (override in `application.yml` if needed) |
| `mmx.backoffice.outbox.max-publish-attempts` | `5` | Attempts before a row transitions to `FAILED` |
| `mmx.backoffice.outbox.poll-interval-ms` | `1000` | Outbox polling interval in ms |

Spring Kafka producer is configured with `spring.kafka.producer.acks=all` (strongest durability guarantee).

### Schema Registry (governance)

Schema registration is **governance-only** — the outbox relay publishes Jackson JSON and does not call Schema Registry at runtime. A broken registry blocks `scripts/register-schemas.sh` and deploy-time compatibility checks, not live order handoff.

| Environment | Registration | Readiness |
|-------------|--------------|-----------|
| Local dev | `mmx-start.sh` runs `scripts/register-schemas.sh` after Docker healthchecks pass | Redpanda healthcheck includes `GET /subjects` on the internal registry (`:8081`) |
| Production | Run `scripts/register-schemas.sh` against the cluster registry URL before or during deploy | Probe `GET /subjects` (expect HTTP 2xx); do not rely on broker health alone |

**Production checklist**

1. At provision time, confirm the internal `_schemas` topic uses `cleanup.policy=compact` (not `delete`). Wrong retention can cause `offset_out_of_range` and HTTP 400 (`error_code: 40002`) from the registry API.
2. Pin the Redpanda version; test upgrades in staging with `register-schemas.sh`.
3. Alert on registry API failures in readiness probes.
4. If the registry fails in production, escalate to Redpanda support — do not delete broker volumes or the `_schemas` topic.

**Local dev recovery** (when `register-schemas.sh` reports fetch/offset errors):

```bash
docker compose down
docker volume rm mmx_redpanda-data
docker compose up -d
```

### Local dev

1. Start infrastructure (Postgres + Redpanda + Redpanda Console):
   ```bash
   docker compose up -d
   ```
2. Run the application:
   ```bash
   cd backend && mvn -pl mmx-bootstrap spring-boot:run
   ```
3. Browse the Kafka topic at **http://localhost:8081** (Redpanda Console → Topics → `mmx.order.executed`).

The application connects to Redpanda on `localhost:19092` (external listener). With default config, the outbox relay is enabled so executed orders move from **Queuing** (`PENDING`) to **Sent** (`PUBLISHED`) within about a second.

### CI / Testcontainers

`ExecutionHandoffKafkaIntegrationTest` (`mmx-bootstrap`) is a full-stack integration test that spins up both PostgreSQL and Kafka automatically via **Testcontainers** — no external infrastructure required:

- `@Testcontainers(disabledWithoutDocker = true)` — skips the test gracefully when Docker is unavailable (e.g. some restricted CI runners).
- A `KafkaContainer` (Apache Kafka image via Testcontainers) and a `PostgreSQLContainer` are started once per test class.
- `@DynamicPropertySource` injects `spring.kafka.bootstrap-servers` and `spring.datasource.url` so the application context picks up the ephemeral container addresses at test startup.
- The test executes an order via HTTP, asserts the outbox row payload validates against the canonical schema `contracts/schemas/OrderExecutedV1.json` (same file referenced by `asyncapi.yaml`), then awaits the relay publishing the message and validates record key = `orderId`.

To run this test locally with Docker available:

```bash
cd backend
mvn test -pl mmx-bootstrap -Dtest=ExecutionHandoffKafkaIntegrationTest
```
