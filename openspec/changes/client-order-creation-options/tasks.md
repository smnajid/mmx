# Tasks

Strict TDD, vertical slices: each "Write failing" task is one red test (or a small set for one behaviour) followed by its implement-until-green task re-running the same scoped command.

## 1. Contracts (contract-first) and codegen

- [ ] 1.1 `contracts/002-trader-orders-views/openapi.yaml`: add required `legalEntityCode` query parameter (3 chars) to `listTermCurrencies`, `listOnCallCurrencies`, `listTermTenors`, `listOnCallNoticePeriods`; add a shared `503 ServiceUnavailable` response to every `/api/v1/order-creation/` operation; mirror both in `api-v1.md`
- [ ] 1.2 `contracts/007-cross-org-routing/openapi.yaml`: add `GET /api/v1/cross-org/reference/term-rates/latest?currency&tenor` (returns `CrossOrgTermRateResponse[]`) and `GET /api/v1/cross-org/reference/oncall-segments?currency&noticePeriod[&valueDate]` (returns new `CrossOrgOnCallSegmentResponse[]`); state grant scoping on all rate reads including `/term-rates`; mirror in `api-v1.md`
- [ ] 1.3 Regenerate server stubs and confirm compile breaks only where controllers must adapt: `cd backend && mvn -q -pl mmx-adapter-in-rest -am compile -DskipTests`

## 2. Application — client option rule (`mmx-application`)

- [ ] 2.1 Write failing client-currency cases in `TermOrderCreationOptionsServiceTest`: CGD sees EUR through effective enablement while hub EUR has no enabled tenors; no client enablement → excluded; offboarded institution → excluded; hub LOC behaviour unchanged. `cd backend && mvn test -pl mmx-application -Dtest=TermOrderCreationOptionsServiceTest`
- [ ] 2.2 Add `LegalEntityCode` to `ListTermCurrenciesUseCase.listCurrencies` and implement the client branch (extract the candidate loop from `OrderCreationDelegatedCounterpartySupport.forClient` into a shared method) until 2.1 is green
- [ ] 2.3 Write failing client-tenor case (grant {1M,3M,6M} ∩ enablement {3M,6M} ∩ hub rates {1M,3M} → {3M}) in `TermOrderCreationOptionsServiceTest`. `cd backend && mvn test -pl mmx-application -Dtest=TermOrderCreationOptionsServiceTest`
- [ ] 2.4 Add `LegalEntityCode` to `listTenors` and implement the client branch until 2.3 is green
- [ ] 2.5 Write failing client cases in `OnCallOrderCreationOptionsServiceTest`: currency via PENDING_CONFIRMATION open segment and effective enablement; notice periods {24H,48H} hub segments ∩ effective {48H} → {48H}. `cd backend && mvn test -pl mmx-application -Dtest=OnCallOrderCreationOptionsServiceTest`
- [ ] 2.6 Add `LegalEntityCode` to OnCall `listCurrencies` / `listNoticePeriods` and implement until 2.5 is green; refactor shared client logic between Term and OnCall without changing behaviour
- [ ] 2.7 Add `HubReferenceDataUnavailableException` to `application.exception` (no behaviour on its own; exercised in 3.x and 4.x)

## 3. Adapter-out-integration — remote reads (`mmx-adapter-out-integration`)

- [ ] 3.1 Write failing cases in `RemoteReferenceDataAdapterTest`: a non-200, a connection failure and a timeout from the hub throw `HubReferenceDataUnavailableException` (currencies, grants, institutions, rates). `cd backend && mvn test -pl mmx-adapter-out-integration -am -Dtest=RemoteReferenceDataAdapterTest -Dsurefire.failIfNoSpecifiedTests=false`
- [ ] 3.2 Make `RemoteReferenceDataHttp.getList` throw instead of returning empty until 3.1 is green
- [ ] 3.3 Write failing cases: `RemoteTermRateRepository.findLatestRatePerInstitution` calls `/term-rates/latest` and maps rows; `findDistinctCurrenciesWithTermRates` throws `UnsupportedOperationException`. Same command as 3.1
- [ ] 3.4 Implement in `RemoteTermRateRepository` until 3.3 is green
- [ ] 3.5 Write failing cases for new `RemoteOnCallRateRepository`: open segments and covering-date reads call `/oncall-segments` with the right query and map to `OnCallRateSegment`; `findByInstitutionCode` returns empty; writes and `findDistinctCurrenciesWithOpenOnCallSegments` throw `UnsupportedOperationException`. Same command as 3.1
- [ ] 3.6 Implement `RemoteOnCallRateRepository` until 3.5 is green

## 4. Adapter-in-rest (`mmx-adapter-in-rest`)

- [ ] 4.1 Write failing cases in `OrderCreationOptionsControllerTest`: `legalEntityCode` is passed to the four list use cases; missing → 400; `HubReferenceDataUnavailableException` → 503 with the standard error body. `cd backend && mvn test -pl mmx-adapter-in-rest -am -Dtest=OrderCreationOptionsControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
- [ ] 4.2 Update `OrderCreationOptionsController` and map the exception in `GlobalExceptionHandler` until 4.1 is green
- [ ] 4.3 Write failing cases in `CrossOrgReferenceDataControllerTest`: `/term-rates`, `/term-rates/latest` and `/oncall-segments` return only rows whose `(institution, currency)` is granted to the proven client; covering-date variant honours `valueDate`. `cd backend && mvn test -pl mmx-adapter-in-rest -am -Dtest=CrossOrgReferenceDataControllerTest -Dsurefire.failIfNoSpecifiedTests=false`
- [ ] 4.4 Implement the new operations and grant scoping in `CrossOrgReferenceDataController` (+ `CrossOrgReferenceDataMapper`) until 4.3 is green

## 5. Bootstrap wiring and integration (`mmx-bootstrap`)

- [ ] 5.1 Write failing assertion in `CrossOrgClientDeploymentIntegrationTest`: the `OnCallRateRepository` bean is the remote one when `reference-data-remote=true`, and CGD's `GET /api/v1/order-creation/term/currencies?legalEntityCode=CGD` returns EUR against a stubbed hub (and 503 when the stub is down). `cd backend && mvn test -pl mmx-bootstrap -am -Dtest=CrossOrgClientDeploymentIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
- [ ] 5.2 Register `RemoteOnCallRateRepository` as `@Primary` in `CrossOrgRoutingModuleConfiguration` until 5.1 is green
- [ ] 5.3 Update `OrderCreationOptionsRestApiIntegrationTest` hub calls to send `legalEntityCode` and assert hub behaviour unchanged. `cd backend && mvn test -pl mmx-bootstrap -am -Dtest=OrderCreationOptionsRestApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`

## 6. Frontend — order-creation widget (`frontend/projects/order-creation-widget`)

- [ ] 6.1 Regenerate widget API types: `cd frontend && npm run generate:api`
- [ ] 6.2 Write failing Vitest cases: `wizard-api.service.spec.ts` sends `legalEntityCode` on currencies/tenors/notice-periods; `step-currency.component.spec.ts` shows the error + Retry (not the empty message) on 503. `cd frontend && npx ng test order-creation-widget --include='**/wizard-api.service.spec.ts'` and `--include='**/step-currency.component.spec.ts'`
- [ ] 6.3 Update `WizardApiService` and the currency / tenor-notice-period steps to pass `legalEntityCode` from host config until 6.2 is green
- [ ] 6.4 Confirm Settings screens served by remote reads (currency list, institution list/onboard/detail) show the HTTP error on 503: existing `error` handling verified by reading each component; add a Vitest case only where a component swallows the error

## 7. Specs and docs alignment

- [ ] 7.1 `openspec validate client-order-creation-options` passes
- [ ] 7.2 Open a `.scratch` ticket for client-side Settings rate views (term-rate trading days, on-call segments per client institution) left out of scope

## 8. Final verification

- [ ] 8.1 Run full `cd backend && mvn test` — all modules green
- [ ] 8.2 Run `npm run test` in `frontend/` — green
- [ ] 8.3 `cd frontend && npm run verify:contracts` — green
