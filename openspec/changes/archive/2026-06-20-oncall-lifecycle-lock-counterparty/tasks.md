## 1. Contract-first API (contract-info extension)

- [x] 1.1 Update `specs/002-trader-orders-views/contracts/openapi.yaml`: add required `institutionCode` and `counterparty` to `ContractInfoResponse`; bump API version note in `api-v1.md` prose for contract-info.
- [x] 1.2 Run OpenAPI codegen (`cd backend && mvn -pl mmx-adapter-in-rest -am compile`) until generated `ContractInfoResponse` includes new fields.

## 2. Backend — contract-info (red-first)

- [x] 2.1 **adapter-out-persistence / application** — Write failing `OnCallOrderCreationOptionsServiceTest` case: `getContractInfo` returns institutionCode and counterparty from executed subscription. `cd backend && mvn test -pl mmx-application -am -Dtest=OnCallOrderCreationOptionsServiceTest`
- [x] 2.2 **application + adapter-out-persistence + adapter-in-rest** — Extend `ExecutedSubscriptionContractInfo`, `ContractInfoResult`, JPA mapping, `OrderCreationRestMapper.toContractInfoResponse`, and `OnCallOrderCreationOptionsService.getContractInfo` until green.
- [x] 2.3 **bootstrap** — Write failing `OrderCreationOptionsRestApiIntegrationTest` assertion: contract-info 200 body includes `institutionCode` and `counterparty`. `cd backend && mvn test -pl mmx-bootstrap -Dtest=OrderCreationOptionsRestApiIntegrationTest#getOnCallContractInfo*`
- [x] 2.4 **bootstrap** — Implement integration expectation until green (re-run same `-Dtest` filter).

## 3. Backend — lifecycle intake enforcement (red-first)

- [x] 3.1 **application** — Write failing `ReceiveOrderServiceInstitutionTest` (or `ReceiveOrderServiceTest`) case: OnCall INCREASE with `sourceContractNumber` and mismatched `institutionCode` rejects; matching institution succeeds. `cd backend && mvn test -pl mmx-application -am -Dtest=ReceiveOrderServiceInstitutionTest`
- [x] 3.2 **application** — Implement institution-vs-contract check in `ReceiveOrderService` (lookup via `OrderRepository.findExecutedSubscriptionByContractNumber`) until green.
- [x] 3.3 **bootstrap** — Write failing REST integration test: `POST /api/v1/orders` lifecycle with wrong institution returns 4xx. `cd backend && mvn test -pl mmx-bootstrap -Dtest=OrderRestApiIntegrationTest` (scoped to new test method name once added).
- [x] 3.4 **bootstrap** — Implement until green (re-run same `-Dtest`).

## 4. Frontend — models and shortcut seeding

- [x] 4.1 **order-creation-widget** — Update `api-responses.model.ts` `ContractInfoResponse`; write failing `order-creation-wizard.component.spec.ts` case: contract-info response seeds locked institution in wizard state. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/order-creation-wizard.component.spec.ts' --no-watch`
- [x] 4.2 **order-creation-widget** — Extend `applyContractShortcut` / `WizardState` with `contractInstitutionCode` and `contractCounterparty`; wire `loadContractShortcut` until green.

## 5. Frontend — counterparty lock (red-first)

- [x] 5.1 **order-creation-widget** — Write failing `step-counterparty.component.spec.ts`: shortcut INCREASE filters to contract institution and blocks when no rate; shortcut DECREASE/REDEMPTION show locked institution and allow continue without rate. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/step-counterparty.component.spec.ts' --no-watch`
- [x] 5.2 **order-creation-widget** — Implement operation-split filter and outflow continue path in `step-counterparty.component.ts` until green.
- [x] 5.3 **order-creation-widget** — Write failing `wizard-state.service.spec.ts`: `isStepComplete(COUNTERPARTY)` true for outflow shortcut when institution set without rate; `clearDownstream` preserves `contractInstitutionCode` / `contractCounterparty` in shortcut mode.
- [x] 5.4 **order-creation-widget** — Guard `clearDownstream` and counterparty step completion rules until green.
- [x] 5.5 **order-creation-widget** — Write failing `order-creation-wizard.component.spec.ts` (or `step-review`): DECREASE shortcut end-to-end emits payload with institution but no rate dependency. Re-run targeted spec file.

## 6. Playground alignment

- [x] 6.1 **widget-playground** — Update `widget-playground.component.spec.ts` contract-info mock to include `institutionCode` and `counterparty` if tests assert on shortcut flow.

## 7. Final verification

- [x] 7.1 Run full `cd backend && mvn test` — all modules green.
- [x] 7.2 Run `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm run test` — green.
