## 1. Feature spec and contracts (003)

- [x] 1.1 Create `specs/003-managed-currency-settings/spec.md` and `data-model.md` aligned with OpenSpec capability specs
- [x] 1.2 Author `specs/003-managed-currency-settings/contracts/openapi.yaml` (list, get, onboard, patch rules, disable)
- [x] 1.3 Mirror REST in `specs/003-managed-currency-settings/contracts/api-v1.md`
- [x] 1.4 Run OpenAPI codegen for settings API into `mmx-adapter-in-rest`; verify generated interfaces compile (fix duplicate generated-sources paths if `mvn clean test` fails)

## 2. Persistence (`mmx-adapter-out-persistence`)

- [x] 2.1 Add Flyway migration `managed_currency` (code, active, min amounts, tenor/notice flags)
- [x] 2.2 Add JPA entity, Spring Data repository, and `JpaManagedCurrencyRepository` implementing `ManagedCurrencyRepository` port
- [x] 2.3 Write `JpaManagedCurrencyRepositoryTest` (save, find, duplicate code, list active/inactive)

## 3. Domain policy (`mmx-domain`) — TDD

- [x] 3.1 Add `ManagedCurrency` snapshot type and `OpenContractPosition` value used by policy (no Spring)
- [x] 3.2 Add `OrderAgainstCurrencyPolicy` with `validateReceive(...)` and `validateAmountUpdate(...)` entry points
- [x] 3.3 Write failing `OrderAgainstCurrencyPolicyTest`: unknown/inactive currency, empty catalog, disabled tenor/notice, subscription/lifecycle minimums
- [x] 3.4 Extend policy tests: Decrease floor (`B - A < S`), position currency mismatch
- [x] 3.5 Implement policy until domain tests pass (green)

## 4. Application ports and services (`mmx-application`) — TDD

- [x] 4.1 Add `ManagedCurrencyRepository` and `OpenPositionPort` in `port/out`
- [x] 4.2 Add inbound ports for settings (`ManageCurrencySettingsUseCase`)
- [x] 4.3 Write failing `ManageCurrencySettingsServiceTest`: onboard, duplicate reject, last-tenor/last-notice reject, disable with open orders
- [x] 4.4 Implement `ManageCurrencySettingsService` and domain validation for ISO 4217 + last-enabled guards
- [x] 4.5 Write failing `ReceiveOrderServiceTest` cases: empty catalog, disabled tenor, below minimum, Decrease floor (mock ports)
- [x] 4.6 Wire `ReceiveOrderService` to load currency + position (Decrease only) and invoke policy before `MoneyMarketOrder.create`
- [x] 4.7 Write failing `UpdateOrderServiceTest` cases: below minimum, inactive currency; wire policy on amount update
- [x] 4.8 Ensure duplicate receive path does not invoke policy (explicit test in `ReceiveOrderServiceTest`)

## 5. PositionApi adapter (`mmx-adapter-out-integration`)

- [x] 5.1 Add `InMemoryOpenPositionPort` for unit/application tests
- [x] 5.2 Implement `PositionApiOpenPositionAdapter` (HTTP client) implementing `OpenPositionPort` by contract number; fail closed on errors
- [x] 5.3 Add configuration properties for PositionApi base URL; wire bean in `mmx-bootstrap` (in-memory default for POC)
- [x] 5.4 Add adapter test with WireMock/stub HTTP for happy path and 5xx/timeout → fail closed

## 6. Settings REST (`mmx-adapter-in-rest`)

- [x] 6.1 Implement generated `CurrencySettingsApi` controller + mapper (no business rules in adapter)
- [x] 6.2 Write `CurrencySettingsControllerTest` for list, onboard, patch, disable, validation errors (4xx)
- [x] 6.3 Map domain validation failures to contract-aligned problem responses (`GlobalExceptionHandler`)

## 7. Order intake delta (`specs/001` + existing REST)

- [x] 7.1 Update `specs/001-mm-order-processing/spec.md` FRs for currency catalog, disabled tenor/notice, amount floors, Decrease position rule, error semantics
- [x] 7.2 Add/adjust intake integration tests: reject unknown currency, disabled `3M`, below minimum, Decrease breach (with `InMemoryOpenPositionPort` in test profile)
- [x] 7.3 Run `mvn test` on backend reactor — all modules green

## 8. Frontend settings and navigation (`frontend/`)

- [x] 8.1 Add API client/service for `003` settings endpoints (match OpenAPI paths)
- [x] 8.2 Add `features/currency-settings/` routes: list, onboard form, edit form (tenor/notice toggles, min amounts)
- [x] 8.3 Write Vitest tests: list render, last-tenor/notice control disabled when sole enabled, save success/error
- [x] 8.4 Add header settings entry in `app.html` / `app.ts`; route to currency settings without breaking desk tabs
- [x] 8.5 Write Vitest or component test: desk two-tier tabs still correct on `/oncall/received` after header change
- [x] 8.6 Run `npm run test` (or `ng test`) in `frontend/` — green

## 9. Bootstrap, seeds, and docs

- [x] 9.1 Register settings beans, repositories, policy, and adapters in `CurrencySettingsModuleConfiguration` + update `OrderModuleConfiguration`
- [x] 9.2 Update `scripts/seed-demo-orders.sh` to onboard required currencies via settings API before posting orders
- [x] 9.3 Update `specs/003-managed-currency-settings/quickstart.md` for strict cold start onboarding step
- [x] 9.4 Smoke manual path: onboard EUR → receive Term/OnCall orders → disable tenor → verify rejection

## 10. Final verification

- [x] 10.1 Run full `mvn test` from `backend/`
- [x] 10.2 Run frontend unit tests
- [x] 10.3 Confirm OpenAPI for `003` matches runtime (no drift vs `api-v1.md`)
