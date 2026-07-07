## 1. Gap fix — routed lifecycle-institution match (TDD, application)

- [x] 1.1 (application) Write a failing test in `RouteOrderServiceTest`: a routed OnCall INCREASE for a TradingClient with a `sourceContractNumber` whose executed Subscription has a different proxy `institutionCode` MUST throw `InvalidOrderException` and create no order (no client-side order, no hub-side order). Run: `mvn test -pl mmx-application -Dtest=RouteOrderServiceTest` → red.
- [x] 1.2 (application) Implement: add `validateLifecycleInstitutionMatchesContract` to `RouteOrderService`, called after `resolveProxy` and before `resolveGrant`, reusing `findExecutedSubscriptionByContractNumber` (proxy-vs-proxy match). Re-run `mvn test -pl mmx-application -Dtest=RouteOrderServiceTest` until green.

## 2. Domain: relocate RoutedHubOrderDraft

- [x] 2.1 (domain) Move `RoutedHubOrderDraft` out of its nested record inside `OrderRoutingFieldMappingPolicy` into its own file under `domain.model` (it becomes the input to `MoneyMarketOrder.createHubSideFromRouting` and will be imported by `RoutedOrderIntake`). Compile gate: `mvn -pl mmx-domain -am compile`.

## 3. Application: introduce unified intake (behaviour-preserving)

- [x] 3.1 (application) Add `port/in/IntakeUseCase` with `Result(UUID orderId, OrderStatus status, boolean newlyCreated)` and `receive(ReceiveOrderCommand)`.
- [x] 3.2 (application) Add `RoutedOrderIntake` concrete class (no port) owning routing-specific creation: `resolveProxy`, `resolveGrant`, `resolveGlobalAccount`, `createClientOrder`, the hub-side draft (fold the field-copy from `OrderRoutingFieldMappingPolicy.mapToHubSide` into a private method here), and `markRouted`. Constructor takes the routing out-ports (`ProxyInstitutionRepository`, `DelegatedGrantDirectory`, `GlobalAccountDirectory`, `InstitutionRepository`, `OrderRepository`, `Clock`).
- [x] 3.3 (application) Add `IntakeService` implementing `IntakeUseCase`: shared intake core (idempotency lookup, PM-organisation + legal-entity validation, institution resolution, `validateLifecycleInstitutionMatchesContract`, `currencyPolicy.validateReceive`, `intakeSourceContractNumber`, audit) + the TradingClient-vs-TradingHub dispatch, delegating routing to `RoutedOrderIntake`. Preserve the D8 validation ordering on the routed path: institution-constraint checks first (throw `InvalidOrderException`, no order), then routing-failure checks (`rejectAtIntake` → client `REJECTED` order, no hub-side order). Audit `ORDER_RECEIVED` for hub path, `ORDER_ROUTED` / `ORDER_ROUTING_REJECTED` for routed path, `DUPLICATE_RECEIVE_IGNORED` for duplicates.
- [x] 3.4 (application) Write `IntakeServiceTest` at the `IntakeUseCase` seam, migrating `ReceiveOrderServiceTest` + `RouteOrderServiceTest` + `IntakeLegalEntityValidationTest` (including the gap-fix case from 1.1, now asserting through `IntakeUseCase.receive`). Run: `mvn test -pl mmx-application -Dtest=IntakeServiceTest` → green.

## 4. Bootstrap + REST wiring + retire old services

- [x] 4.1 (bootstrap) Add `TransactionalIntakeUseCase`
- [x] 4.2 (adapter-in-rest) Thin `OrderIntakeController`
- [x] 4.3 (application) Delete `port/in/ReceiveOrderUseCase`, `port/in/RouteOrderUseCase`, `ReceiveOrderService`, `RouteOrderService`.
- [x] 4.4 (domain) Delete `OrderRoutingFieldMappingPolicy`

## 5. Final verification

- [x] 5.1 Run full `cd backend && mvn test` — all modules green (includes `mmx-bootstrap` Testcontainers: `OrderRoutingIntakeIntegrationTest`, `OrderRestApiIntegrationTest`, and the ArchUnit `HexagonalArchitectureTest` asserting the controller no longer injects an out-port and depends only on `port.in`).
- [x] 5.2 Confirm the intake REST contract is unchanged: `contracts/001-mm-order-processing/openapi.yaml` and `contracts/002-trader-orders-views/openapi.yaml` `ReceiveOrderResponse` schema and `POST /api/v1/orders` operation are unmodified (no SDD contract delta required for this change).
