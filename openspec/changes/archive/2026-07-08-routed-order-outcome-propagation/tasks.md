# Tasks — Routed-order pair outcome-propagation

TDD-ordered per `design.md` §Migration Plan. Layers named per hexagonal boundaries. No REST / OpenAPI / frontend / Flyway change in this delivery. The single full-reactor run lives in **Final verification**.

## 1. Lock current propagation behavior (characterization tests)

- [x] 1.1 **application** — Write characterization tests in `OrderLifecycleServiceTest` covering hub cancel/reject propagation to a routed client-side order (client reaches `CANCELLED` / `REJECTED`) **and** the current silent-skip when the linked client-side order is missing (assert no `save(client)` and no throw). Run: `mvn test -pl mmx-application -Dtest=OrderLifecycleServiceTest` — green against today's code (locks current behavior).
- [x] 1.2 **application** — Write characterization tests in `ExecuteOrderServiceTest` covering hub execute propagation (client reaches `EXECUTED`; subscription generates a new client contract number while lifecycle reuses `sourceContractNumber` — the two-numbers invariant) **and** the current double-fetch of the client-side order (verify `findRoutedClientOrderByRoutingId` invoked twice) **and** the current `InvalidOrderException` on missing client. Run: `mvn test -pl mmx-application -Dtest=ExecuteOrderServiceTest` — green against today's code.

## 2. Extract port (behavior-preserving refactor)

- [x] 2.1 **application** — Create `RoutedOrderOutcomePropagation` (port interface), `RoutedOrderOutcomePropagationService` (impl), and `ExecutionPropagationResult` record in `com.mmx.order.application.service`. Move the logic of `ExecuteOrderService.propagateHubOutcome` + `routingContextFor` into `propagateExecution` (returning `ExecutionPropagationResult(clientOrder, handoffContext)`) and `OrderLifecycleService.propagateHubCancelOrReject` into `propagateCancel` / `propagateReject`. **Preserve current error semantics exactly**: execute throws `InvalidOrderException` on missing client; cancel/reject silent `ifPresent` no-op on missing client. Wire port + impl `@Bean`s in `OrderModuleConfiguration`. Compile gate: `mvn -pl mmx-application -am compile`.
- [x] 2.2 **application** — Refactor `ExecuteOrderService` to inject `RoutedOrderOutcomePropagation`, call `propagateExecution`, use `result.handoffContext()` for the outbox, and delete `propagateHubOutcome` + `routingContextFor`. Refactor `OrderLifecycleService` to inject the port and call `propagateCancel` / `propagateReject`; delete `propagateHubCancelOrReject`. Compile gate: `mvn -pl mmx-application -am compile`.
- [x] 2.3 **application** — Re-run characterization tests to confirm the refactor is behavior-preserving: `mvn test -pl mmx-application -Dtest=ExecuteOrderServiceTest,OrderLifecycleServiceTest` — green (still locks silent-skip + double-fetch + `InvalidOrderException`).

## 3. New domain exception for pair-integrity violation

- [x] 3.1 **domain** — Add `RoutedOrderPairIntegrityException` to `com.mmx.order.domain.exception` (invariant-violation semantics, distinct from user-input `InvalidOrderException`). Compile gate: `mvn -pl mmx-domain compile`.

## 4. Flip error policy + dedicated port-impl test surface (TDD red → green)

- [x] 4.1 **application** — Write failing `RoutedOrderOutcomePropagationServiceTest` asserting: (a) execute propagates `EXECUTED` with copied rate/time/dealingReference and the two-numbers invariant (subscription → new client contract number; lifecycle → reuse `sourceContractNumber`); (b) cancel propagates `CANCELLED`; (c) reject propagates `REJECTED` with reason; (d) **missing client throws `RoutedOrderPairIntegrityException` on all three outcomes**; (e) `propagateExecution` returns an `ExecutionPropagationResult` whose `handoffContext` carries the client's `legalEntityCode`, `id`, `portfolioNumber`, and `counterparty`. Run: `mvn test -pl mmx-application -Dtest=RoutedOrderOutcomePropagationServiceTest` — red (impl still has old behavior).
- [x] 4.2 **application** — Implement until green: in `RoutedOrderOutcomePropagationService`, throw `RoutedOrderPairIntegrityException` on a missing client-side order for **all three** outcomes; remove the silent `ifPresent` on cancel/reject and replace the `InvalidOrderException` on execute. Re-run: `mvn test -pl mmx-application -Dtest=RoutedOrderOutcomePropagationServiceTest` — green.
- [x] 4.3 **application** — Update the characterization tests in `ExecuteOrderServiceTest` + `OrderLifecycleServiceTest` to the new error policy: missing-client now throws `RoutedOrderPairIntegrityException` (mock the port to throw it; assert the services surface it), and the execute double-fetch assertion is removed (single fetch now). Run: `mvn test -pl mmx-application -Dtest=ExecuteOrderServiceTest,OrderLifecycleServiceTest` — green.

## 5. Transactional wrapper for cancel / reject (atomic pair sync)

- [x] 5.1 **bootstrap** — Add `TransactionalOrderLifecycleUseCase` implementing `CancelOrderUseCase` + `RejectOrderUseCase`, `@Primary` `@Service` `@Transactional`, delegating to `OrderLifecycleService`. Rewire the `cancelOrderUseCase` / `rejectOrderUseCase` beans in `OrderModuleConfiguration` to the wrapper (mirror `TransactionalExecuteOrderUseCase`). Compile gate: `mvn -pl mmx-bootstrap -am compile`.
- [x] 5.2 **bootstrap** — Write a focused integration test asserting atomic pair sync on cancel/reject: cancel/reject a routed hub-side order, force the client-side save to fail, and assert the hub-side terminal transition rolled back (neither order is left terminal while the other is non-terminal). Run: `mvn test -pl mmx-bootstrap -Dtest=TransactionalOrderLifecycleAtomicityIntegrationTest` — green.

## 6. Spec–code parity confirmation

- [x] 6.1 Confirm the `order-routing` delta spec (`specs/order-routing/spec.md`) scenarios match the implemented behavior: execute/cancel/reject propagate synchronously in one transaction; missing client throws `RoutedOrderPairIntegrityException` on all three; no silent desync. Update the delta if any scenario drifted during implementation.

## 7. Final verification

- [x] 7.1 Run full `cd backend && mvn test` — all modules green (including `mmx-bootstrap` Testcontainers integration tests).
