## 1. Contracts and SDD alignment

- [x] 1.1 Extend `OrderSummaryResponse` with `handoffStatus` (`PENDING` | `PUBLISHED` | `FAILED`) in `specs/002-trader-orders-views/contracts/openapi.yaml`; mirror field semantics in `specs/002-trader-orders-views/contracts/api-v1.md`.
- [x] 1.2 Confirm `OrderExecutedV1` in `specs/002-trader-orders-views/contracts/asyncapi.yaml` matches the thin handoff field list in `design.md` D4; adjust AsyncAPI + `asyncapi-v1.md` if gaps appear during mapping.
- [x] 1.3 Update `specs/002-trader-orders-views/spec.md` (FR-013 and related AC) so trader-facing behaviour matches the delivered `handoffStatus` contract — same delivery as code.

## 2. OpenAPI codegen and REST scaffolding

- [x] 2.1 Regenerate REST server stubs/models from canonical OpenAPI (`mmx-adapter-in-rest` per existing codegen workflow).
- [x] 2.2 Add failing REST/controller or mapper tests that assert executed-list responses include `handoffStatus` once persistence mapping exists (narrowest layer that fails for wrong reasons).

## 3. Persistence — orders and outbox

- [x] 3.1 Add Flyway migration: `handoff_status` column on `orders` (nullable → backfill strategy per `design.md` migration plan); document chosen backfill in migration comment or ops note.
- [x] 3.2 Add Flyway migration: `back_office_outbox` table per `design.md` D10 (`order_id` UNIQUE, payload, status, `publish_attempts`, timestamps, indexes).
- [x] 3.3 Map `HandoffStatus` on `MoneyMarketOrder` in `mmx-domain` (integration VO / enum as per design — not `OrderStatus`).
- [x] 3.4 Extend `mmx-adapter-out-persistence`: JPA mapping for `handoff_status`; ensure `OrderRepository.save` participates in the same transaction as outbox write.

## 4. Application layer — ports and execute flow

- [x] 4.1 Add port `ExecutionHandoffOutbox` in `mmx-application` (`schedule(MoneyMarketOrder)`); remove or supersede `BackOfficeGateway` / `NoOpBackOfficeGateway` usage from the execute path.
- [x] 4.2 Write failing unit test: `ExecuteOrderService` after successful execute calls `ExecutionHandoffOutbox.schedule` exactly once and sets order `handoffStatus` to `PENDING` before transaction boundaries behave as tested (mock port).
- [x] 4.3 Implement `ExecuteOrderService` changes: after `orderRepository.save` for `EXECUTED`, set handoff state and call `executionHandoffOutbox.schedule(order)`; keep audit logging order consistent with design D1.

## 5. Module `mmx-adapter-out-messaging`

- [x] 5.1 Create Maven module `mmx-adapter-out-messaging`; add to backend parent `pom.xml`; depend on `mmx-application`, Spring Data JPA (outbox entity), Spring Kafka, and test scope Testcontainers.
- [x] 5.2 Implement `ExecutionHandoffOutbox`: build contract-shaped `OrderExecutedV1` payload (map from domain → AsyncAPI-aligned DTO), serialise JSON into outbox row, enforce uniqueness on `order_id`.
- [x] 5.3 Implement `BackOfficeOutboxRelay`: `@Scheduled` poll with locking strategy from design D6; publish with `KafkaTemplate`, key = `orderId`; on producer ack set outbox `SENT` and order `handoffStatus` `PUBLISHED`; increment `publish_attempts` and retry until max; terminal `FAILED` + WARN log with `orderId`.
- [ ] 5.4 Unit tests: relay retry and FAILED at `max-publish-attempts` with mocked `KafkaTemplate`; scheduling writes expected payload shape (golden or schema validation fixture).

## 6. Hexagonal wiring and HTTP execute path

- [x] 6.1 `mmx-bootstrap`: wire `ExecutionHandoffOutbox`, Kafka producer properties (`acks` aligned with D6), `mmx.backoffice.outbox.*` and `mmx.backoffice.kafka.topic`; enable relay scheduler; remove `BackOfficeGateway` bean if obsolete.
- [x] 6.2 `mmx-adapter-in-rest`: remove `BackOfficeGateway` dependency and post-execute `notifyExecution` try/catch from `OrderManagementController`; adjust constructor and tests (`OrderExecutionControllerTest`, lifecycle tests that stub gateway).
- [x] 6.3 `OrderRestMapper` / query mapping: populate generated `OrderSummaryResponse.handoffStatus` from domain for Term/OnCall executed endpoints only as appropriate.

## 7. Integration and contract verification

- [x] 7.1 Integration test (Testcontainers PostgreSQL + Kafka): execute order → assert outbox row + payload validates against AsyncAPI schema → relay runs → consume topic and assert key + body.
- [ ] 7.2 Add or extend automated checks so OpenAPI and AsyncAPI specimens stay in sync with golden fixtures or codegen (project’s existing contract-test pattern).
- [x] 7.3 Run `mvn test` for backend modules touched; fix regressions in accounting callback and execute flows.

## 8. Frontend — trader executed lists

- [x] 8.1 Regenerate or update Angular API client from OpenAPI if the project uses generated clients; otherwise extend services/models to include `handoffStatus`.
- [x] 8.2 Display `handoffStatus` on Term and OnCall executed list rows (distinct styling or labels for `PENDING` / `PUBLISHED` / `FAILED` per FR-013).
- [ ] 8.3 Add or extend Vitest/Cypress coverage for executed list showing the three states when fixtures allow.

## 9. Ops and documentation

- [ ] 9.1 Document Kafka topic creation, config keys (`mmx.backoffice.*`), and local/CI Testcontainers usage for developers (`plan.md` or feature quickstart if this repo expects it).
- [ ] 9.2 Resolve open questions from `design.md` (existing `EXECUTED` backfill choice; manual FAILED replay note) with concrete implementation defaults or follow-up tickets.
