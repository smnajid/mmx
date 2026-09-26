## 1. Contract-first: OpenAPI and api-v1.md

- [x] 1.1 Update `specs/002-trader-orders-views/contracts/openapi.yaml`: add nine GET paths under `/api/v1/order-creation/` with tag `OrderCreation`, new schemas (`TermCurrenciesResponse`, `OnCallCurrenciesResponse`, `OperationsResponse`, `OperationOption`, `TenorsResponse`, `NoticePeriodsResponse`, `CounterpartiesResponse`, `CounterpartyOption`, `ContractInfoResponse`), and query parameters. Add `institutionCode` (required) to `ReceiveOrderRequest`, remove `desiredCounterpartyComment`. Remove `institutionCode` from `ExecuteOrderRequest` (keep only `executedRate`).
- [x] 1.2 Update `specs/002-trader-orders-views/contracts/api-v1.md`: add prose documentation for all nine order creation endpoints, update intake and execute sections to match OpenAPI changes.
- [x] 1.3 Run codegen: `cd backend && mvn compile -pl mmx-adapter-in-rest -am` — verify new `OrderCreationApi` interface is generated and existing `IntakeApi` / `OrdersApi` reflect schema changes.

## 2. Application layer — port/out repository extensions

- [x] 2.1 Write failing `TermRateRepositoryQueryTest` (`mmx-adapter-out-persistence`): test `findLatestRatePerInstitution(currency, tenor)` returns most recent rate per institution, excludes inactive institutions. `mvn test -pl mmx-adapter-out-persistence -am -Dtest=TermRateRepositoryQueryTest`
- [x] 2.2 Add `findLatestRatePerInstitution(String currency, Tenor tenor)` to `TermRateRepository` (`mmx-application` port/out) and implement in `JpaTermRateRepository` / `SpringDataTermRateRepository` (`mmx-adapter-out-persistence`) until green.
- [x] 2.3 Write failing `OnCallRateRepositoryQueryTest` (`mmx-adapter-out-persistence`): test `findOpenSegmentsByCurrencyAndNoticePeriod(currency, noticePeriod)` returns VALID and PENDING_CONFIRMATION open segments, test `findSegmentCoveringDate(currency, noticePeriod, valueDate)` returns segments where `valueDate <= requested <= endDate`. `mvn test -pl mmx-adapter-out-persistence -am -Dtest=OnCallRateRepositoryQueryTest`
- [x] 2.4 Add `findOpenSegmentsByCurrencyAndNoticePeriod(String currency, NoticePeriod noticePeriod)` and `findSegmentsCoveringDate(String currency, NoticePeriod noticePeriod, LocalDate valueDate)` to `OnCallRateRepository` (`mmx-application` port/out) and implement in JPA adapter until green.
- [x] 2.5 Write failing `OrderRepositoryContractLookupTest` (`mmx-adapter-out-persistence`): test `findExecutedSubscriptionByContractNumber(contractNumber)` returns currency and noticePeriod from executed Subscription; returns empty for non-Subscription or non-executed. `mvn test -pl mmx-adapter-out-persistence -am -Dtest=OrderRepositoryContractLookupTest`
- [x] 2.6 Add `findExecutedSubscriptionByContractNumber(String contractNumber)` to `OrderRepository` (`mmx-application` port/out) and implement in `JpaOrderRepository` / `SpringDataOrderRepository` until green.
- [x] 2.7 Add `findDistinctCurrenciesWithTermRates()` and `findDistinctCurrenciesWithOpenOnCallSegments()` queries to `TermRateRepository` and `OnCallRateRepository` respectively (used by currency filter). Implement in JPA adapters with tests alongside 2.2/2.4.

## 3. Application layer — use cases and services

- [x] 3.1 Write failing `TermOrderCreationOptionsServiceTest` (`mmx-application`): test currencies filtered by active managed currency + enabled tenors + rate existence; test tenors filtered by enabled + rate existence; test counterparties return latest rate per institution with rateDate and indicative flag, sorted by rate desc; test operations return SUBSCRIPTION with minSubscriptionAmount. `mvn test -pl mmx-application -am -Dtest=TermOrderCreationOptionsServiceTest`
- [x] 3.2 Create `TermOrderCreationOptionsService` implementing use case interfaces (`ListTermCurrenciesUseCase`, `ListTermOperationsUseCase`, `ListTermTenorsUseCase`, `ListTermCounterpartiesUseCase`) in `mmx-application` until green.
- [x] 3.3 Write failing `OnCallOrderCreationOptionsServiceTest` (`mmx-application`): test currencies filtered by active managed currency + enabled notice periods + open segment existence; test notice periods filtered; test counterparties return segment rate for valueDate, sorted by rate desc; test operations return all four with correct min amounts; test contract-info lookup. `mvn test -pl mmx-application -am -Dtest=OnCallOrderCreationOptionsServiceTest`
- [x] 3.4 Create `OnCallOrderCreationOptionsService` implementing use case interfaces (`ListOnCallCurrenciesUseCase`, `ListOnCallOperationsUseCase`, `ListOnCallNoticePeriodsUseCase`, `ListOnCallCounterpartiesUseCase`, `GetContractInfoUseCase`) in `mmx-application` until green.

## 4. Intake changes — institutionCode at reception

- [x] 4.1 Write failing `ReceiveOrderServiceInstitutionTest` (`mmx-application`): test intake with valid active institutionCode sets counterparty from displayName; test intake with unknown institutionCode is rejected; test intake with inactive institutionCode is rejected; test intake without institutionCode is rejected. `mvn test -pl mmx-application -am -Dtest=ReceiveOrderServiceInstitutionTest`
- [x] 4.2 Modify `ReceiveOrderService` (`mmx-application`): validate `institutionCode` against `InstitutionRepository`, resolve `displayName` → `counterparty`, persist both on the order at reception. Remove `desiredCounterpartyComment` handling. Implement until green.

## 5. Execute changes — rate-only execution

- [x] 5.1 Write failing `ExecuteOrderServiceRateOnlyTest` (`mmx-application`): test execute uses institution already on order (not from request); test execute rejects if institution became inactive since intake; test execute request with only `executedRate`. `mvn test -pl mmx-application -am -Dtest=ExecuteOrderServiceRateOnlyTest`
- [x] 5.2 Modify `ExecuteOrderService` (`mmx-application`): read `institutionCode` from the order instead of from the execute request; keep active-institution validation at execute time; remove `institutionCode` parameter from execute command/use case interface. Implement until green.
- [x] 5.3 Update existing `ExecuteOrderServiceTest` to reflect new execute signature (rate-only). `mvn test -pl mmx-application -am -Dtest=ExecuteOrderServiceTest`

## 6. REST adapter — OrderCreationOptionsController

- [x] 6.1 Create `OrderCreationOptionsController` in `mmx-adapter-in-rest` implementing generated `OrderCreationApi` interface. Delegate all nine endpoints to the corresponding application-layer use cases. Create `OrderCreationRestMapper` for DTO ↔ domain mapping.
- [x] 6.2 Update `OrderIntakeController` to handle new `institutionCode` field from generated `ReceiveOrderRequest` and pass it to the modified `ReceiveOrderService`.
- [x] 6.3 Update `OrderManagementController` execute method to pass only `executedRate` to the modified `ExecuteOrderService` (no `institutionCode` from request).
- [x] 6.4 Compile check: `cd backend && mvn compile -pl mmx-adapter-in-rest -am` — verify all controllers compile against updated generated interfaces.

## 7. Bootstrap wiring

- [x] 7.1 Add `OrderCreationModuleConfiguration` in `mmx-bootstrap` wiring use case beans for the order creation options services. Update existing configurations if `ReceiveOrderService` / `ExecuteOrderService` constructor signatures changed.

## 8. Integration tests

- [x] 8.1 Write `OrderCreationOptionsRestApiIntegrationTest` (`mmx-bootstrap`): test `GET /api/v1/order-creation/term/currencies` returns filtered currencies; test `GET .../term/counterparties?currency=EUR&tenor=3M` returns institutions with rates sorted by best rate; test `GET .../oncall/counterparties?currency=EUR&noticePeriod=24H&valueDate=...` returns segment rates; test `GET .../oncall/contract-info?contractNumber=...` returns 200 with data or 404; test endpoints work without X-Trader-Id header.
- [x] 8.2 Update `OrderRestApiIntegrationTest` (`mmx-bootstrap`): update intake test cases to include `institutionCode` (required) and remove `desiredCounterpartyComment`; update execute test cases to send only `executedRate`.

## 9. SDD spec sync

- [x] 9.1 Update `specs/001-mm-order-processing/spec.md`: update FR-017 (execute no longer requires counterparty from request), FR-022 (remove desiredCounterpartyComment), add note about institutionCode at intake.
- [x] 9.2 Verify `specs/002-trader-orders-views/contracts/openapi.yaml` and `api-v1.md` are consistent with implemented behaviour (cross-check from task 1.1/1.2).

## 10. Final verification

- [x] 10.1 Run full `cd backend && mvn test` — all modules green.
