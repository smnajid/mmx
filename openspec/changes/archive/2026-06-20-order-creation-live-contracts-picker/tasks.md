## 1. Contract-first HTTP surface

- [x] 1.1 Update `specs/002-trader-orders-views/contracts/openapi.yaml`: add `GET /api/v1/order-creation/contracts` (params `portfolioNumber` required, `orderType` required) under tag `OrderCreation`; add `LiveContract` and `LiveContractsResponse` schemas.
- [x] 1.2 Update `specs/002-trader-orders-views/contracts/api-v1.md` in lockstep (prose + liveness semantics, OnCall vs Term).
- [x] 1.3 Regenerate REST stubs/models for `mmx-adapter-in-rest` (OpenAPI codegen from `backend/`); compile-only gate `mvn -pl mmx-adapter-in-rest -am compile`.

## 2. Domain liveness (red-first)

- [x] 2.1 **domain** — Write failing liveness tests: `mvn test -pl mmx-domain -Dtest=ContractLivenessPolicyTest` covering OnCall (no redemption = live; redemption present = not live), Term (endDate in future = live; matured = not live), and tenor→duration mapping.
- [x] 2.2 **domain** — Implement the liveness policy/helpers (Term end-date from `valueDate` + tenor; OnCall "has redemption") until green.

## 3. Application use case (red-first)

- [x] 3.1 **application** — Write failing `ListLiveContractsServiceTest`: `mvn test -pl mmx-application -Dtest=ListLiveContractsServiceTest` — given executed Subscriptions for a portfolio/type, returns only live contracts; excludes redeemed OnCall and matured Term; maps reference fields.
- [x] 3.2 **application** — Add `ListLiveContractsUseCase` (`port/in`), result type under `application/ordercreation`, and `ListLiveContractsService`; extend `OrderRepository` (`port/out`) with the candidate + redemption-existence query. Implement until green.

## 4. Persistence query (red-first)

- [x] 4.1 **adapter-out-persistence** — Write failing query test: `mvn test -pl mmx-adapter-out-persistence -Dtest=OrderRepositoryLiveContractsTest` — executed Subscriptions returned by portfolio/type; a received OR executed REDEMPTION on the contract disqualifies it; a CANCELLED redemption does not.
- [x] 4.2 **adapter-out-persistence** — Implement `SpringDataOrderRepository` query method(s) and `JpaOrderRepository` mapping until green; add an index migration if the query needs one.

## 5. REST controller (red-first)

- [x] 5.1 **adapter-in-rest** — Write failing `OrderCreationOptionsController` test for the new operation (200 with `LiveContractsResponse`; required-param handling).
- [x] 5.2 **adapter-in-rest** — Implement the generated `listLiveContracts` method delegating to `ListLiveContractsUseCase`; add mapper conversions; wire the bean in `OrderCreationModuleConfiguration`. Implement until green.

## 6. Frontend host/playground picker

- [x] 6.1 **frontend** — Add `WizardApiService.listLiveContracts(portfolioNumber, orderType)` + response models.
- [x] 6.2 **frontend** — Replace the playground free-text contract field with a contract picker (select orderType → list live contracts → choose → set widget `contractNumber`); Term entries listed but non-actionable. Co-located Vitest spec asserts selecting an OnCall contract mounts the widget with `contractNumber` set.

## 7. Final verification

- [x] 7.1 Run full `cd backend && mvn test` — all modules green.
- [x] 7.2 Run `npm run test` in `frontend/` — green.
