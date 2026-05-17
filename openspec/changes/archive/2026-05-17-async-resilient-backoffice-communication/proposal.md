## Why

Outbound back-office notification today is a **synchronous, best-effort** call after the `EXECUTED` commit: if Kafka/back-office is unavailable, the event is logged and **never retried**, leaving mmx and back-office out of sync until manual intervention. That gap was explicitly deferred from `executed-orders-accounting`; traders also need to distinguish “handoff not yet delivered” from “delivered, awaiting accounting” (see `specs/002-trader-orders-views` FR-013). This change delivers **durable, asynchronous** integration using a **transactional outbox** and **Kafka**, with **idempotency** so at-least-once delivery does not corrupt lifecycle or double-book.

## What Changes

- Replace the post-commit synchronous `BackOfficeGateway.notifyExecution` handoff with **enqueueing an outbox record in the same database transaction** as persisting `EXECUTED` (atomicity: order state and intent to notify commit together).
- Add an **outbox relay** that publishes pending records to a **Kafka topic** consumed by the back-office system, with **automatic retry** on broker/consumer failures until published or moved to a dead-letter posture defined in design.
- Define an **idempotent outbound event** per `orderId` (stable message key / deduplication contract) so duplicate publishes or consumer replays do not cause duplicate accounting work on the back-office side; document consumer expectations.
- Track **handoff delivery state** per executed order (`PENDING`, `PUBLISHED`, `FAILED` or equivalent) for operations and for trader-visible distinction between transmission problems and books delay.
- **Preserve** the inbound `POST /api/v1/back-office/orders/{orderId}/accounted` contract and its existing **idempotent** `EXECUTED → ACCOUNTED` behaviour; no breaking change to callback semantics.
- **Remove** the “no automatic retry” requirement from outbound transmission; supersede best-effort swallow-only failure handling with durable outbox + relay.
- Introduce **Kafka** and outbox persistence as runtime dependencies (local/dev via Testcontainers or compose — settled in `design.md`).
- **Contract-first async**: canonical **AsyncAPI 3.0** (`specs/002-trader-orders-views/contracts/asyncapi.yaml` + `asyncapi-v1.md`) for `OrderExecutedV1`; all product/integration wire formats remain contract-first (**sync** OpenAPI, **async** AsyncAPI).
- Behavioural delivery follows **TDD** (red-green-refactor); any new **trader-facing REST** fields for handoff state follow **contract-first OpenAPI** + `api-v1.md` in the same delivery as code.

## Capabilities

### New Capabilities

- `back-office-outbound-messaging`: Transactional outbox schema and lifecycle, outbox-to-Kafka relay, topic/event envelope contract, publish retries, idempotent message identity (`orderId`-keyed), and operational visibility (metrics/logs) for stuck or failed publishes.

### Modified Capabilities

- `back-office-accounting-handoff`: Outbound handoff becomes **outbox enqueue after `EXECUTED` commit** (same transaction boundary as order persist), not synchronous gateway invocation; supersede “best-effort, no retry” with durable delivery guarantees; retain exactly-once **business** notification per execution via idempotency; keep inbound accounted callback rules unchanged.
- `trader-executed-queue`: Executed list rows expose **handoff/delivery state** so traders can tell transmission-not-succeeded from submitted-awaiting-accounting (aligns with FR-013 in feature 002); contract-first API extension in the same delivery.

## Impact

- **Backend (hexagonal)**: `mmx-application` (`BackOfficeGateway` or successor port), `ExecuteOrder` / controller orchestration (move from post-commit sync call to transactional outbox write); new `mmx-adapter-out-messaging` (or extend `mmx-adapter-out-integration`) for outbox JPA + Kafka producer; `mmx-bootstrap` wiring; Flyway migration for outbox table and optional delivery-state column on orders.
- **Infrastructure**: Kafka cluster (or embedded broker for tests); Spring Kafka / outbox relay scheduler configuration.
- **Product API**: Likely extension to executed-list summary DTOs (`handoffStatus` or similar) in `specs/002-trader-orders-views/contracts/openapi.yaml` and `api-v1.md` — not controller-first.
- **Frontend**: Angular executed views consume new handoff field when API ships.
- **Specs (SDD)**: Delta specs under this change for the three capabilities above; sync `specs/002-trader-orders-views/spec.md` if FR-013 wording is refined.
- **Tests**: Unit tests for outbox write + idempotency; integration tests with Kafka Testcontainers; existing execute/accounting tests updated for async handoff semantics.
- **Out of scope (this change)**: Replacing the inbound HTTP callback with Kafka consumption on the mmx side; production-grade callback authentication (remains POC posture); implementing the real back-office consumer (mmx owns publish contract only).
