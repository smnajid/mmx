## 1. Dead repository query (point 5)

- [x] 1.1 Remove `findByAssignedTraderIdAndStatusAndOrderType` from `OrderRepository`, `SpringDataOrderRepository`, `JpaOrderRepository`
- [x] 1.2 Grep backend; run `mvn test` — green

## 2. Mapper unit tests (point 4, TDD)

- [x] 2.1 Add `OrderPersistenceMapperTest` — RECEIVED, ASSIGNED, EXECUTED+handoff round-trips; assert tenor stored as enum name, reloaded via `Tenor.valueOf`
- [x] 2.2 Add `OrderRestMapperTest` — `toSummary`/`toDetails` encoding (tenor code `3M`, handoff on summary only, assignment/execution fields)
- [x] 2.3 Add receive/execute command mapping smoke cases if not covered
- [x] 2.4 Optional refactor: dedupe shared fields in `OrderRestMapper` between `toSummary` and `toDetails` with tests green
- [x] 2.5 Run `mvn test` in `backend/`

## 3. Received list shell (point 7)

- [x] 3.1 Add `format-http-error.ts` in `frontend/src/app/core/http/` (if consolidating errors in this change)
- [x] 3.2 Create `received-order-list.component.ts` (+ spec) with `WorkspaceKind`, `ReceivedViewModeService`, assign + show-all toolbar
- [x] 3.3 Add thin `term-received-order-list` / `oncall-received-order-list` shells OR route `data.workspace` (mirror Assigned/Executed)
- [x] 3.4 Update `app.routes.ts` / `term-orders.routes.ts` / `oncall-orders.routes.ts`
- [x] 3.5 Delete `term-order-list` and `oncall-order-list` components and obsolete specs
- [x] 3.6 Run `npm run test` in `frontend/`

## 4. Order detail action policy (point 8)

- [x] 4.1 Add `order-detail-actions.ts` + `order-detail-actions.spec.ts` (RECEIVED / ASSIGNED assignee / EXECUTED cases)
- [x] 4.2 Refactor `order-details.component.ts` to use policy for action visibility; keep forms and dialogs
- [x] 4.3 Extend `order-details.component.spec.ts` for at least one policy-driven visibility case
- [x] 4.4 Run `npm run test` in `frontend/`

## 5. Documentation

- [x] 5.1 Note dead-query removal in `specs/001-mm-order-processing/plan.md` or `data-model.md` if repository methods are documented (SDD sync)
- [x] 5.2 Verify no OpenAPI/`api-v1.md` changes required
