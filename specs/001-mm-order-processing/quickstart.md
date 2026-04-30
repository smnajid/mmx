# Quickstart: Money Market Order Processing

## Prerequisites

| Tool | Version | Verify Command |
|------|---------|---------------|
| Java JDK | 21+ | `java --version` |
| Maven | 3.9+ | `mvn --version` (or use included `./mvnw`) |
| Node.js | 20 LTS+ | `node --version` |
| npm | 10+ | `npm --version` |
| Angular CLI | 18+ | `ng version` |
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |
| PostgreSQL | 16+ | Provided via Docker Compose |

## Repository Structure

```text
mmx/
├── backend/          ← Spring Boot multi-module Maven project
├── frontend/         ← Angular 18+ application
├── specs/            ← Spec Kit specifications and plans
├── .specify/         ← Spec Kit configuration
├── docker-compose.yml
└── docs/
```

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
./mvnw clean install
./mvnw spring-boot:run -pl mmx-bootstrap
```

The backend starts on `http://localhost:8080`.

Flyway migrations run automatically on startup, creating the `money_market_order` and `order_audit_log` tables.

### Verify the Backend

```bash
curl http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`

### OpenAPI / Swagger UI

Open `http://localhost:8080/swagger-ui.html` in a browser to explore the API interactively.

## 3. Build and Run the Frontend

From the repository root:

```bash
cd frontend
npm install
ng serve
```

The frontend starts on `http://localhost:4200` and proxies API calls to the backend at `http://localhost:8080`.

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

Expected: `201 Created` with `{ "orderId": "...", "status": "RECEIVED" }`

### List received Term orders

```bash
curl http://localhost:8080/api/v1/orders/term/received \
  -H "X-Trader-Id: trader-1"
```

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
./mvnw test -pl mmx-domain,mmx-application

# Integration tests (requires Docker for Testcontainers)
./mvnw verify -pl mmx-adapter-out-persistence

# All tests
./mvnw verify
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

Run with proxy: `ng serve --proxy-config proxy.conf.json`

## Troubleshooting

| Problem | Solution |
|---------|----------|
| Port 5432 in use | Stop existing PostgreSQL: `docker compose down` then `docker compose up -d postgres` |
| Flyway migration error | Drop and recreate the database: `docker compose down -v && docker compose up -d postgres` |
| Backend won't start | Check Java version: `java --version` (must be 21+) |
| Frontend can't reach backend | Ensure proxy config is active: `ng serve --proxy-config proxy.conf.json` |
| Tests fail with Docker error | Ensure Docker is running: `docker info` |
