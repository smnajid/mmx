## 1. Contract and feature spec parity (SDD)

- [x] 1.1 Extend `specs/002-trader-orders-views/contracts/openapi.yaml`: query param on `listReceivedTermOrders` / `listReceivedOnCallOrders` (default near-term behaviour documented).
- [x] 1.2 Mirror `specs/002-trader-orders-views/contracts/api-v1.md` sections for both endpoints (parameters, semantics, examples).
- [x] 1.3 Update `specs/002-trader-orders-views/tasks.md`: add Phase for US3 or mark deferred block as active with checkboxes aligned to this OpenSpec change.
- [x] 1.4 Run `mvn -pl mmx-adapter-in-rest -am compile` after OpenAPI codegen path picks up contract changes.

## 2. Backend — persistence and queries

- [x] 2.1 Add repository method(s) to filter `RECEIVED` ∧ `OrderType` with optional `valueDate` range (`LocalDate start`, `LocalDate end` inclusive) suitable for paging (or `@Query`).
- [x] 2.2 Implement in `JpaOrderRepository` / Spring Data adapter; map enums consistently with existing methods.
- [x] 2.3 Use injected `Clock` + business `ZoneId` to compute `{today, today+2}` inclusive window for **near_term** mode.

## 3. Backend — application and REST

- [x] 3.1 Extend `OrderQueryService` (and generated API delegate) so Received list operations accept mode from contract and call the new finder; **default** path uses window; **all** path matches current unfiltered semantics.
- [x] 3.2 Ensure pagination is applied **after** filter at DB boundary (avoid loading full RECEIVED tables).
- [x] 3.3 Implement controller mapping from codegen DTO/param to domain mode.

## 4. Automated tests

- [x] 4.1 Unit tests: date window boundaries (inclusive), timezone day rollover (fixed `Clock`).
- [x] 4.2 Integration tests: `OrderRestApiIntegrationTest` — default excludes far `valueDate`; `all` includes it.
- [x] 4.3 Regression: workspace switchdoes not implicitly reset mode — covered at API contract level if param sent per request; optionally add SPA e2e.

## 5. Frontend

- [x] 5.1 Session-scoped Received mode state (Angular service) default **near_term**.
- [x] 5.2 Toggle UX on Term + OnCall Received list screens; passes query param via `OrderApiService` methods.
- [x] 5.3 Preserve mode when navigating **Term ↔ OnCall** (same service instance for session).

## 6. OpenSpec closure

- [x] 6.1 `openspec validate received-near-term-window` passes.
- [x] 6.2 After merge-ready implementation, `openspec archive received-near-term-window` (or project archive procedure) and ensure `openspec/specs/` gains permanent `trader-received-queue` baseline if adopting OpenSpec archive flow.
