## Context

mmx today notifies the back-office **synchronously** from `OrderManagementController` immediately after `ExecuteOrderService.execute(...)` commits: `backOfficeGateway.notifyExecution(order)` runs in a try/catch, failures are logged and swallowed, and there is **no retry**. The archived `executed-orders-accounting` change explicitly deferred transactional outbox + Kafka to this work.

Traders still need FR-013: distinguish **handoff/integration problems** from **successful handoff, awaiting accounting** on Executed list rows. Feature 002 also defines FR-014 (staleness / sort) — **out of scope** for this change; design only ensures `handoffStatus` and existing timestamps do not block FR-014 later.

Inbound `POST /api/v1/back-office/orders/{orderId}/accounted` remains HTTP, idempotent, and unchanged in shape.

## Goals / Non-Goals

**Goals:**

- **Transactional outbox**: persist handoff intent in the **same database transaction** as `EXECUTED` (order save + outbox row + `handoffStatus` update).
- **Async delivery**: `@Scheduled` relay publishes to **Kafka** via **Spring Kafka**; mark outbox **SENT** only after **Kafka producer ack**.
- **Resilience**: automatic publish retries; **`FAILED`** handoff after **configurable** max attempts (default **5**).
- **Idempotency**: one logical execution handoff per `orderId` (domain forbids re-execute); Kafka message **key = `orderId`**; consumer dedupes replays.
- **Trader visibility**: expose all handoff status codes on executed-list summaries (contract-first OpenAPI).
- **Contract-first integration (async)**: canonical **AsyncAPI 3** for `OrderExecutedV1` Kafka messages; producer serialises from contract-shaped types, not ad-hoc domain JSON.
- **Hexagonal placement**: handoff scheduling inside **`ExecuteOrderService`** (not controller); relay in new outbound adapter module.
- **TDD** and **Testcontainers** (Kafka + DB) for integration tests.

**Non-Goals:**

- Inbound accounted callback over Kafka (HTTP callback stays).
- Real back-office consumer implementation (mmx owns topic contract + publish only).
- Production callback authentication.
- FR-014 staleness UX and default sort.
- Debezium / CDC outbox relay.
- Changing `OrderStatus` enum for integration (handoff is a separate concern).

## Decisions

### D1. Schedule handoff inside `ExecuteOrderService` (same transaction)

`ExecuteOrderService` gains a dependency on outbound port **`ExecutionHandoffOutbox`** (see D5). After `orderRepository.save(order)` for `EXECUTED`, it calls `executionHandoffOutbox.schedule(order)` **before the transaction returns**.

```
Trader POST /execute
        │
        ▼
┌─────────────────────────────────────────────────────────┐
│  ExecuteOrderService @Transactional                     │
│    order.execute(...)                                   │
│    orderRepository.save(order)                          │
│    order.setHandoffStatus(PENDING)   // integration VO  │
│    executionHandoffOutbox.schedule(order)  → INSERT outbox│
│    auditLogger.log(ORDER_EXECUTED)                      │
└─────────────────────────────────────────────────────────┘
        │ commit
        ▼
   HTTP 200 (no sync Kafka call in controller)
```

**Remove** controller `try/catch` around `BackOfficeGateway.notifyExecution`.

**Why not post-commit in controller.** Couples REST to integration and breaks atomicity with order state. The archived post-commit sync pattern is **superseded** by outbox-in-tx + relay-after-commit.

**Alternative considered.** `@TransactionalEventListener(AFTER_COMMIT)` to insert outbox — rejected; explicit use-case orchestration is easier to test and matches existing style.

### D2. `HandoffStatus` on the order (integration concern, not `OrderStatus`)

Keep lifecycle pure: `OrderStatus` remains `EXECUTED → ACCOUNTED` only. Add **`HandoffStatus`** (application/domain-facing enum, persisted on `orders`):

| Value | Meaning | Trader signal (FR-013) |
|-------|---------|-------------------------|
| `PENDING` | Outbox row written; not yet acked to Kafka | Handoff in progress |
| `PUBLISHED` | Kafka producer ack received | Submitted toward back-office; awaiting accounting |
| `FAILED` | Relay exhausted publish attempts | Integration/handoff problem |

**All three codes** are exposed on executed-list API rows (`handoffStatus` on `OrderSummaryResponse` in `specs/002-trader-orders-views/contracts/openapi.yaml`).

Relay transitions: `PENDING → PUBLISHED` on successful publish; `PENDING → FAILED` when `publish_attempts >= maxPublishAttempts`.

**Alternative considered.** Derive status only from outbox table — rejected; executed-list reads would join outbox on every query.

### D3. Publish retries and configurable `FAILED` threshold

Outbox rows track `publish_attempts` (incremented each relay try). Configuration:

```yaml
mmx:
  backoffice:
    outbox:
      max-publish-attempts: 5   # default; env-overridable
      poll-interval-ms: 1000    # @Scheduled fixed delay, design default
```

While `publish_attempts < maxPublishAttempts`, relay **retries** on broker/send errors. When attempts reach the limit, set outbox status **`FAILED`**, order `handoffStatus = FAILED`, log WARN with `orderId`.

**No** silent swallow after execute — trader always gets 200; failure is observable via `handoffStatus` and ops logs.

**Alternative considered.** Infinite retry — rejected; ops needs a terminal FAILED for support and FR-013.

### D4. One execution handoff per order; thin handoff DTO payload (no re-execute)

Domain already prevents executing twice from non-`ASSIGNED` states. Spec and implementation enforce:

- **Unique** outbox constraint on `order_id` (one row per execution event).
- Kafka record key = **`orderId`** (UUID string).
- Event type **`OrderExecutedV1`** with a versioned **thin handoff DTO** — only fields required for back-office booking, **frozen at schedule time** and serialised into the outbox row (same bytes relayed to Kafka). Not `orderId`-only, and not the full `MoneyMarketOrder` aggregate.

**`OrderExecutedV1` handoff fields (illustrative):**

`orderId`, `executedAt`, `orderType`, `orderOperation`, `portfolioNumber`, `currency`, `amount`, `valueDate`, `executedRate`, `counterparty`, `dealingReference`, `contractNumber`, `externalOrderReference`, `tenor` (nullable), `noticePeriod` (nullable).

Back-office consumer **MUST** treat duplicate deliveries with the same `orderId` as idempotent (at-least-once Kafka semantics) and **MUST NOT** require a follow-up mmx read API to obtain execution facts.

**Alternatives considered.**

- _Minimal (`orderId` + envelope only)._ Rejected — forces BO pull from mmx; no read API for BO in scope; outbox replay is not self-sufficient.
- _Fat snapshot (full order serialised)._ Rejected — blurs integration contract with domain shape; over-ships internal fields; harder schema governance for the same booking outcome.
- _Hybrid (minimal event + `GET` order on consume)._ Rejected — dual failure mode (Kafka + HTTP); Trader-authenticated product API is the wrong surface for BO pull in this POC.

### D5. Port and component naming (replace `BackOfficeGateway`)

| Old | New | Responsibility |
|-----|-----|----------------|
| `BackOfficeGateway.notifyExecution` | **`ExecutionHandoffOutbox.schedule(MoneyMarketOrder)`** | Write outbox + set `PENDING` in application tx |
| (none) | **`BackOfficeOutboxRelay`** | `@Scheduled` poll pending rows, publish, update statuses |
| `NoOpBackOfficeGateway` | **Remove** | Replaced by real outbox adapter |

Port lives in `mmx-application/port/out/ExecutionHandoffOutbox.java`. Implementation in **`mmx-adapter-out-messaging`** (new module): JPA outbox entity + repository, Spring Kafka `KafkaTemplate`, relay scheduler.

`mmx-bootstrap` wires `ExecutionHandoffOutbox` bean and Kafka producer properties.

### D6. Spring Kafka + `@Scheduled` relay + Testcontainers

- **Producer**: Spring Kafka `KafkaTemplate` with `acks=all` (or broker default aligned with “ack before SENT”).
- **Topic**: `mmx.order.executed` (configurable `mmx.backoffice.kafka.topic`).
- **Relay**: `@Scheduled(fixedDelayString = "${mmx.backoffice.outbox.poll-interval-ms}")` selects `PENDING` / retryable rows with `FOR UPDATE SKIP LOCKED` or equivalent optimistic locking for single-instance POC.
- **SENT semantics**: update outbox to `SENT` and order to `PUBLISHED` **only in** `whenComplete` / callback after successful `kafkaTemplate.send(...).get()` (or reactive equivalent with sync ack in integration tests).
- **Local / CI tests**: **Testcontainers** for PostgreSQL (existing) + **Kafka**; no committed docker-compose required for POC if Testcontainers suffices.

**Alternative considered.** Debezium CDC — rejected for POC complexity.

### D7. Inbound path unchanged

`POST /api/v1/back-office/orders/{orderId}/accounted` — same status-based idempotency as today. `handoffStatus` may remain `PUBLISHED` or be ignored once `ACCOUNTED`; order leaves Executed lists via status filter.

### D8. Contract-first sync (OpenAPI) and async (AsyncAPI 3)

**Synchronous (Trader product + inbound BO HTTP)** — unchanged constitution pattern:

- Canonical: `specs/002-trader-orders-views/contracts/openapi.yaml` + prose `api-v1.md`.
- Workflow: change OpenAPI → regenerate → implement controllers / DTO mapping.

**Asynchronous (mmx → back-office Kafka)** — same discipline, different artifact:

- Canonical: `specs/002-trader-orders-views/contracts/asyncapi.yaml` (AsyncAPI **3.0.0**) + prose `asyncapi-v1.md`.
- Defines channel `mmx.order.executed`, message `OrderExecutedV1`, thin handoff payload schema, Kafka key = `orderId`.
- Workflow: change AsyncAPI → update generated or hand-maintained messaging DTOs in `mmx-adapter-out-messaging` → map domain → contract payload → serialise to outbox → relay publishes bytes unchanged.
- **Tests**: contract tests validate golden JSON fixtures against AsyncAPI schemas (and integration tests consume from Testcontainers topic).

**Cross-cutting rule (encoded in specs):** all **product and integration wire formats** for this feature are contract-first — **sync via OpenAPI, async via AsyncAPI 3** — in the same SDD delivery as behaviour; no code-first payloads on HTTP or Kafka.

Generated messaging types MUST live in adapter modules, not `mmx-domain`.

**Alternatives considered.** JSON Schema only without AsyncAPI wrapper — rejected; channel/topic binding and send operation documentation belong in one machine-readable doc. Avro + registry — deferred.

### D9. REST extensions for trader Executed list (OpenAPI)

Extend `OrderSummaryResponse` in `specs/002-trader-orders-views/contracts/openapi.yaml`:

```yaml
handoffStatus:
  type: string
  enum: [PENDING, PUBLISHED, FAILED]
  description: Back-office handoff delivery state for EXECUTED orders
```

Regenerate codegen; map from persisted `HandoffStatus` in `mmx-adapter-in-rest`. Mirror in `api-v1.md`. Only present / meaningful for rows in `EXECUTED` executed-list endpoints:

- `GET /api/v1/orders/term/executed`
- `GET /api/v1/orders/oncall/executed`

### D10. Outbox table (Flyway)

Table `back_office_outbox` (illustrative):

| Column | Notes |
|--------|--------|
| `id` | UUID PK |
| `order_id` | UUID, **UNIQUE** |
| `payload` | JSON/text serialized `OrderExecutedV1` |
| `status` | `PENDING`, `SENT`, `FAILED` |
| `publish_attempts` | int, default 0 |
| `created_at` | instant |
| `last_attempt_at` | nullable |

Indexes: `(status, created_at)` for relay poll.

### D11. Testing strategy (TDD)

| Layer | Focus |
|-------|--------|
| **Unit** | `ExecuteOrderService` persists order + calls `ExecutionHandoffOutbox`; one schedule per execute |
| **Unit** | Relay retry logic and FAILED at max attempts (mock template) |
| **Integration** | Testcontainers: execute → outbox row → relay → message on topic with key `orderId` |
| **REST** | Update `OrderExecutionControllerTest`: remove `BackOfficeGateway` sequencing; assert no controller handoff |
| **Contract** | OpenAPI + AsyncAPI conformance tests; generated models include `handoffStatus` |

Red-green-refactor per project default; test tasks mirrored in `tasks.md` when generated.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Relay marks SENT but BO consumer never runs | Trader sees `PUBLISHED`; ops monitors consumer lag; out of mmx scope |
| Single-instance `@Scheduled` poller | Accept for POC; document scale-out needs partition locking later |
| `FAILED` after 5 attempts leaves order stuck `EXECUTED` | Ops replay via manual outbox reset / future admin API; WARN logs with `orderId` |
| Handoff DTO schema drift | Version event `OrderExecutedV1`; additive fields where possible; breaking changes → `OrderExecutedV2` or compatibility policy in specs |
| Kafka unavailable at dev time | Testcontainers in CI; optional profile to skip Kafka in local-only runs if needed |

## Migration Plan

1. Flyway: `handoff_status` column on `orders` (default nullable → backfill `PUBLISHED` for existing `EXECUTED` without outbox, or `PENDING` if conservative).
2. Deploy outbox + relay with Kafka topic created.
3. Deploy application code: `ExecuteOrderService` schedules outbox; remove controller gateway call.
4. Deploy OpenAPI + frontend for `handoffStatus`.
5. **Rollback**: revert app; outbox rows remain but are harmless; sync gateway path not restored without code rollback.

## Open Questions

- **Backfill** for orders already `EXECUTED` before migration: treat as `PUBLISHED` (assume handoff done) vs `FAILED` (force visibility) — decide during implementation / ops input.
- **Manual replay** of `FAILED` outbox rows: admin API deferred; document ops SQL or follow-up change.
- **DLQ topic** for poison payloads: optional; not required if FAILED row + logs suffice for POC.
