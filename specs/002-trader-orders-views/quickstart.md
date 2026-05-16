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

1. Open the app root — **without** manually choosing Term, the first screen MUST land in the **OnCall** workspace (default route).
2. Switch to **Term** — Received (and Assigned / Executed when wired) MUST show **only** Term rows.
3. Switch back to **OnCall** — lists MUST show **only** OnCall rows; Term rows MUST NOT linger from the prior view (same session).
4. Full page reload — default entry MUST be **OnCall** again (no persisted last workspace).

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
| `mmx.backoffice.outbox.relay-enabled` | `true` | Set `false` to disable the scheduler (e.g. `application-local.yml`) |
| `mmx.backoffice.outbox.max-publish-attempts` | `5` | Attempts before a row transitions to `FAILED` |
| `mmx.backoffice.outbox.poll-interval-ms` | `1000` | Outbox polling interval in ms |

Spring Kafka producer is configured with `spring.kafka.producer.acks=all` (strongest durability guarantee).

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

The application connects to Redpanda on `localhost:19092` (external listener). The `application-local.yml` profile disables the relay (`relay-enabled: false`) for offline development.

### CI / Testcontainers

`ExecutionHandoffKafkaIntegrationTest` (`mmx-bootstrap`) is a full-stack integration test that spins up both PostgreSQL and Kafka automatically via **Testcontainers** — no external infrastructure required:

- `@Testcontainers(disabledWithoutDocker = true)` — skips the test gracefully when Docker is unavailable (e.g. some restricted CI runners).
- A `KafkaContainer` (Apache Kafka image via Testcontainers) and a `PostgreSQLContainer` are started once per test class.
- `@DynamicPropertySource` injects `spring.kafka.bootstrap-servers` and `spring.datasource.url` so the application context picks up the ephemeral container addresses at test startup.
- The test executes an order via HTTP, asserts the outbox row payload validates against the `order-executed-v1-payload.schema.json` golden fixture (AsyncAPI contract mirror), then awaits the relay publishing the message and validates record key = `orderId`.

To run this test locally with Docker available:

```bash
cd backend
mvn test -pl mmx-bootstrap -Dtest=ExecutionHandoffKafkaIntegrationTest
```
