# Quickstart: Money Market Order Processing

## Prerequisites

| Tool | Version | Verify Command |
|------|---------|---------------|
| Java JDK | 25 (matches `backend/pom.xml`) | `java --version` |
| Maven | 3.9+ | `mvn --version` |
| Node.js | 22 LTS+ | `node --version` |
| npm | 10+ | `npm --version` |
| Angular CLI | 21+ | `ng version` |
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |
| PostgreSQL | 16+ | Provided via Docker Compose |

## Repository Structure

```text
mmx/
├── backend/          ← Spring Boot multi-module Maven project
├── frontend/         ← Angular 21 application
├── specs/            ← Spec Kit specifications and plans
├── .specify/         ← Spec Kit configuration
├── docker-compose.yml
├── .devcontainer/      ← GitHub Codespaces / Dev Containers (Java, Node, Docker)
└── docs/
```

## GitHub Codespaces

The repo includes [`.devcontainer/devcontainer.json`](../../.devcontainer/devcontainer.json). Creating a Codespace (or opening the folder in a Dev Container locally) will:

1. Install **Java 25 (Temurin)**, **Maven**, **Node 22**, the **Docker CLI** (Compose v2), and optional **SSHD**.
2. Run **`.devcontainer/post-create.sh` once**: `docker compose up -d postgres`, wait for `pg_isready`, **`npm ci`** in `frontend/`, then **`mvn -B -DskipTests install`** in `backend/`.
3. On each container start, **`postStartCommand`** runs `docker compose up -d postgres` so the DB is up after stop/start.

Run the app the same as below, but bind Angular to all interfaces so forwarded ports work:

```bash
cd backend && mvn spring-boot:run -pl mmx-bootstrap
```

```bash
cd frontend && npm run start -- --proxy-config proxy.conf.json --host 0.0.0.0 --port 4200
```

Then use the **Ports** tab (forward **8080** and **4200**) in the browser or desktop client.

## 1. Start the Database

```bash
docker compose up -d postgres
```

This starts a PostgreSQL 16 container on port `5432` with:
- Database: `mmx`
- Username: `mmx`
- Password: `mmx`

Verify it's running:

```bash
docker compose ps
```

## 2. Build and Run the Backend

From the repository root:

```bash
cd backend
mvn clean install
mvn spring-boot:run -pl mmx-bootstrap
```

The backend starts on `http://localhost:8080`.

**Without Docker Postgres (optional):** activate the `local` profile so the app uses the H2 definition in [`application-local.yml`](../../../backend/mmx-bootstrap/src/main/resources/application-local.yml):

```bash
mvn spring-boot:run -pl mmx-bootstrap -Dspring-boot.run.profiles=local
```

In the IDE, set VM options `-Dspring.profiles.active=local` (or program args `--spring.profiles.active=local`) on `MmxApplication`.

Run these commands from the `backend` directory (the reactor POM lives there). With `local`, Hibernate uses `ddl-auto: none` so startup does not fail on H2: Flyway still creates the schema, but JSONB-mapped columns are stored as JSON in H2 and do not pass Hibernate’s `validate` check.

Flyway migrations run automatically on startup, creating the `money_market_order` and `order_audit_log` tables.

### Verify the Backend

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`

### OpenAPI / Swagger UI

Open `http://localhost:8080/swagger-ui.html` in a browser to explore the API interactively.

### OpenAPI contract maintenance (REST models)

The HTTP contract is defined **contract-first** in [`contracts/openapi.yaml`](contracts/openapi.yaml). That file is the source of truth for request/response shapes, enums, and error payloads on the wire.

[`contracts/api-v1.md`](contracts/api-v1.md) is the prose companion: keep it aligned when you change behavior, field meanings, or examples so reviewers and integrators can follow the API without reading YAML only.

The `mmx-adapter-in-rest` module uses the **OpenAPI Generator** Maven plugin twice: **Java** models (package `com.mmx.order.adapter.in.rest.generated.model`) under `target/generated-sources/openapi/`, and **Spring API interfaces** (`com.mmx.order.adapter.in.rest.generated.api`, e.g. `IntakeApi`, `OrdersApi`) under `target/generated-sources/openapi-spring/`. Controllers **implement** those interfaces; hand-written duplicate request/response types under `dto/` are **not** used when generated types suffice. Generated sources are **not** checked into Git; they are produced during `generate-sources` / compile.

**Workflow when you change the API**

1. Edit `openapi.yaml` (and update `api-v1.md` where it documents the same surface).
2. Regenerate models and compile the REST adapter:

   ```bash
   cd backend
   mvn generate-sources -pl mmx-adapter-in-rest
   mvn compile -pl mmx-adapter-in-rest
   ```

3. Fix compilation and mapping code: update `OrderRestMapper`, `GlobalExceptionHandler`, and controller implementations of generated `*Api` interfaces if enum names, required fields, or types changed.
4. Run tests for affected modules (for example `mvn verify -pl mmx-adapter-in-rest,mmx-bootstrap -am`).

A normal `mvn install` or `compile` from `backend/` runs generation automatically for `mmx-adapter-in-rest`, so you only need the explicit `generate-sources` step when iterating on the YAML alone.

## 3. Build and Run the Frontend

From the repository root:

```bash
cd frontend
npm ci
npm run start -- --proxy-config proxy.conf.json
```

The frontend starts on `http://localhost:4200` and proxies API calls to the backend at `http://localhost:8080`. For **GitHub Codespaces**, add `--host 0.0.0.0` to the `npm run start` command so port forwarding can reach the dev server (see [GitHub Codespaces](#github-codespaces) above).

## 4. Test a Complete Workflow

### Receive an order (simulating Portfolio Management)

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{
    "externalOrderReference": "PM-2026-00001",
    "orderType": "TERM",
    "orderOperation": "SUBSCRIPTION",
    "portfolioNumber": "PF-001",
    "currency": "EUR",
    "amount": 5000000.00,
    "valueDate": "2026-05-02",
    "minimumRate": 3.25000000,
    "tenor": "3M"
  }'
```

`minimumRate` may be omitted when the Portfolio Manager does not set an execution-floor indication; reception still succeeds without it.

Expected: `201 Created` with `{ "orderId": "...", "status": "RECEIVED" }`

### List received Term orders

```bash
curl http://localhost:8080/api/v1/orders/term/received \
  -H "X-Trader-Id: trader-1"
```

Each element of `content` is an `OrderSummaryResponse` and includes **`tenor`** for Term rows (e.g. `"3M"`) and **`noticePeriod": null`**; see OpenAPI. The Angular Term received screen displays **Tenor** as a column.

### List received OnCall orders

```bash
curl http://localhost:8080/api/v1/orders/oncall/received \
  -H "X-Trader-Id: trader-1"
```

Each element includes **`noticePeriod`** for OnCall rows (e.g. `"24H"`) and **`tenor": null`**. The Angular OnCall received screen displays **Notice period** as a column.

### Assign the order

```bash
curl -X POST http://localhost:8080/api/v1/orders/{orderId}/assign \
  -H "X-Trader-Id: trader-1"
```

### Execute the order

```bash
curl -X POST http://localhost:8080/api/v1/orders/{orderId}/execute \
  -H "X-Trader-Id: trader-1" \
  -H "Content-Type: application/json" \
  -d '{
    "executedRate": 3.50000000,
    "counterparty": "BankCo International"
  }'
```

Expected: `200 OK` with the order in `EXECUTED` status, including generated `dealingReference` and `generatedContractNumber`.

## 5. Running Tests

### Backend Tests

```bash
cd backend

# Unit tests only (domain + application)
mvn test -pl mmx-domain,mmx-application

# Integration tests (requires Docker for Testcontainers)
mvn verify -pl mmx-adapter-out-persistence

# All tests
mvn verify
```

### Frontend Tests

```bash
cd frontend

# Unit and component tests
ng test

# End-to-end tests (requires backend running)
npx cypress run
```

## 6. Docker Compose (Full Stack)

A `docker-compose.yml` at the repository root provides the full stack:

```yaml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: mmx
      POSTGRES_USER: mmx
      POSTGRES_PASSWORD: mmx
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
```

For V1, the backend and frontend run locally against the Dockerized database. A full containerized setup can be added in V2.

## Environment Configuration

### Backend (`backend/mmx-bootstrap/src/main/resources/application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mmx
    username: mmx
    password: mmx
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
server:
  port: 8080
```

### Frontend Proxy (`frontend/proxy.conf.json`)

```json
{
  "/api": {
    "target": "http://localhost:8080",
    "secure": false
  }
}
```

Run with proxy: `npm run start -- --proxy-config proxy.conf.json` (from `frontend/`).

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Port 5432 in use | Stop existing PostgreSQL: `docker compose down` then `docker compose up -d postgres` |
| Flyway migration error | Drop and recreate the database: `docker compose down -v && docker compose up -d postgres` |
| Backend won't start | Check Java version: `java --version` (must match `java.version` in `backend/pom.xml`, currently 25) |
| Frontend can't reach backend | Ensure proxy config is active: `npm run start -- --proxy-config proxy.conf.json` (add `--host 0.0.0.0` in Codespaces) |
| Tests fail with Docker error | Ensure Docker is running: `docker info` |
