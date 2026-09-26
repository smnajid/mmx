## Why

Order intake is smeared across two near-parallel application services (`ReceiveOrderService` for TradingHub intake, `RouteOrderService` for TradingClient routed intake) plus a REST controller that owns the TradingClient-vs-TradingHub branch and reaches into a persistence out-port to make it. The two services duplicate ~70% of intake validation (idempotency, PM-organisation + legal-entity checks, currency policy, `intakeSourceContractNumber`, audit constants), and the routing path silently skips a spec-mandated validation that the hub path performs — so a TradingClient OnCall lifecycle order (INCREASE/DECREASE/REDEMPTION) can be accepted with an `institutionCode` that does not match its source contract. Unifying intake into one deep module closes the gap, removes the duplication, and makes the REST adapter genuinely thin.

## What Changes

- Introduce a single application entry **`IntakeUseCase`** that owns intake for both TradingHub-native and TradingClient-routed orders, replacing `ReceiveOrderUseCase` and `RouteOrderUseCase`.
- Move the TradingClient-vs-TradingHub routing decision out of `OrderIntakeController` into the application layer; the controller stops injecting `LegalEntityRepository` and stops branching.
- Extract a concrete `RoutedOrderIntake` collaborator (internal to the intake module, not a public port) that owns routing-specific creation: proxy institution resolution, delegated-grant resolution, global-account resolution, client-side order creation, hub-side order draft, and `markRouted`.
- Close the spec-violation gap: enforce the lifecycle-institution-match check on the routed path (OnCall INCREASE/DECREASE/REDEMPTION with a non-null `sourceContractNumber`), throwing `InvalidOrderException` and creating no order — matching the hub path and the `order-institution-constraints` requirement.
- Fold the shallow `OrderRoutingFieldMappingPolicy` (pure field-copy, no rules) into `RoutedOrderIntake`; delete the policy class. `RoutedHubOrderDraft` stays in `mmx-domain` as the domain factory input.
- Add a single `TransactionalIntakeUseCase` wrapper in `mmx-bootstrap` covering both paths (the routed path writes two records atomically per ADR-0002; the hub path gains a transaction it currently lacks).
- `IntakeUseCase.Result` carries `(orderId, status, newlyCreated)` only; the `hubOrderId` that `RouteOrderUseCase.Result` carried is dropped (it was dead — the controller discarded it and the contract has no such field).
- No REST contract change: `POST /api/v1/orders` and `ReceiveOrderResponse` are unchanged.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `order-institution-constraints`: Add an explicit scenario asserting that the lifecycle-institution-match requirement (OnCall INCREASE/DECREASE/REDEMPTION with a non-null `sourceContractNumber` — submitted `institutionCode` MUST equal the source subscription's institution) applies to **TradingClient (routed) intake** as well as TradingHub intake, with the same "no order SHALL be created" outcome on mismatch. The requirement is already unconditional in the spec; this delta locks the previously-violated routed-path case as an explicit acceptance criterion.

## Impact

- **Backend — `mmx-application`**: new `IntakeUseCase` port-in + `IntakeService` + `RoutedOrderIntake`; retire `ReceiveOrderUseCase` / `RouteOrderUseCase` ports and `ReceiveOrderService` / `RouteOrderService`; migrate `intakeSourceContractNumber`, PM-org/LE validation, currency policy, and the lifecycle-institution-match check into the shared intake core.
- **Backend — `mmx-domain`**: delete `OrderRoutingFieldMappingPolicy`; keep `RoutedHubOrderDraft` (move to `domain.model` if not already there) and `MoneyMarketOrder.createHubSideFromRouting` unchanged.
- **Backend — `mmx-adapter-in-rest`**: thin `OrderIntakeController` to map → call `IntakeUseCase` → respond; remove `LegalEntityRepository` injection and the `isTradingClient()` branch.
- **Backend — `mmx-bootstrap`**: replace `TransactionalRouteOrderUseCase` and the `receiveOrderUseCase`/`routeOrderUseCase` beans with one `TransactionalIntakeUseCase` + `IntakeService` + `RoutedOrderIntake` wiring in `OrderModuleConfiguration` / `OrderRoutingModuleConfiguration`.
- **Tests**: migrate `ReceiveOrderServiceTest`, `RouteOrderServiceTest`, `IntakeLegalEntityValidationTest` to a single `IntakeServiceTest` at the `IntakeUseCase` seam; add a red-first test for the routed lifecycle-institution-mismatch rejection; keep `OrderRoutingIntakeIntegrationTest` / `OrderRestApiIntegrationTest` green (contract unchanged); `HexagonalArchitectureTest` asserts the controller no longer injects an out-port.
- **REST contract**: none (`contracts/001-mm-order-processing` and `contracts/002-trader-orders-views` intake paths unchanged).
- **ADRs**: ADR-0002 (two linked records created synchronously at intake) respected — the two-record creation moves behind the internal seam, the record model is untouched.
