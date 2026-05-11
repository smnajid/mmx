## 1. Automated tests (red-first)

- [x] 1.1 **application** — Extend `ExecuteOrderServiceTest`: for an assigned **SUBSCRIPTION** order, assert `generateContractNumber()` is invoked once and execution `generatedContractNumber` matches the generator output (existing success path, tighten if needed).
- [x] 1.2 **application** — Add `ExecuteOrderServiceTest` coverage for **INCREASE** / **DECREASE** / **REDEMPTION**: assert `generateContractNumber()` is **never** called; execution `generatedContractNumber` equals persisted `sourceContractNumber`; `generateDealingReference()` still invoked.
- [x] 1.3 **application** — Add `ExecuteOrderServiceTest` for lifecycle execute when persisted `sourceContractNumber` is null: assert execute fails with a clear error and repository save for execution does not occur.
- [x] 1.4 **application** — Add `ReceiveOrderServiceTest`: for **SUBSCRIPTION**, when command carries a non-null `sourceContractNumber`, assert persisted order has **null** `sourceContractNumber` (normalization / ignored-if-present).
- [x] 1.5 **domain** (optional but recommended) — Add or extend domain tests if `MoneyMarketOrder.execute` gains a lifecycle guard for null `sourceContractNumber`.

## 2. Core implementation

- [x] 2.1 **application** — Update `ExecuteOrderService`: resolve contract number from `order.getOrderOperation()` — **SUBSCRIPTION** → `referenceGenerator.generateContractNumber()`; lifecycle → `order.getSourceContractNumber()` without calling `generateContractNumber()`.
- [x] 2.2 **application** — Update `ReceiveOrderService` (or the adapter command mapping feeding it): for **SUBSCRIPTION**, coerce `sourceContractNumber` to null before `MoneyMarketOrder.create` so intake noise is not stored or surfaced as contract identity.
- [x] 2.3 **domain** — If tests require it, add `MoneyMarketOrder.execute` precondition: lifecycle operations with null `sourceContractNumber` throw a domain/application-consistent error (align message with `ExecuteOrderService` validation if logic stays in application only).

## 3. Contract-first HTTP & Speckit parity

- [x] 3.1 Update `specs/002-trader-orders-views/contracts/openapi.yaml`: `description` (and related prose) for **`generatedContractNumber`** and **`sourceContractNumber`** reflecting subscription vs lifecycle semantics (subscription: optional/ignored source; lifecycle: source required; executed contract number behaviour).
- [x] 3.2 Update `specs/002-trader-orders-views/contracts/api-v1.md` in lockstep with OpenAPI.
- [x] 3.3 Regenerate REST server stubs/models: run OpenAPI codegen for **`mmx-adapter-in-rest`** (e.g. `mvn -pl mmx-adapter-in-rest openapi-generator:generate` from `backend/`) and fix any compile breaks in `mmx-adapter-in-rest`.
- [x] 3.4 Align **Speckit** feature docs for material behaviour: `specs/001-mm-order-processing/spec.md`, `data-model.md`, and PM/trader contract mirrors under `specs/001-mm-order-processing/contracts/` (`openapi.yaml`, `api-v1.md`) where execution or field semantics are stated.

## 4. Broader tests & verification

- [x] 4.1 Update **adapter-in-rest** tests (e.g. `OrderExecutionControllerTest`) if response expectations or fixtures assumed fresh UUID contract ids for lifecycle executes.
- [x] 4.2 Update **bootstrap** e2e or persistence integration tests (`TraderWorkflowE2ETest`, `JpaOrderRepositoryTest`, etc.) if they assert contract-number generation for lifecycle paths.
- [x] 4.3 Run backend suite from `backend/`: `mvn test` and fix regressions.

## 5. OpenSpec / design hygiene (same delivery)

- [x] 5.1 Reconcile `openspec/changes/contract-number-subscription-only/design.md` **Non-Goals** / intake wording with the agreed **subscription `sourceContractNumber` ignored-if-present** semantics (so design matches `specs/execution-contract-number/spec.md`).
