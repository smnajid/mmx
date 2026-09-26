# Tasks

Strict TDD (no waiver): each "Write failing" task must be seen red before its "Implement … until green" partner. The implement step implies re-running that same scoped command. Commands run from `backend/` unless stated. `-Dsurefire.failIfNoSpecifiedTests=false` is needed whenever `-am` is used with `-Dtest`.

## 1. Contracts (contract-first, before any code)

- [x] 1.1 Update `contracts/004-institution-settings/openapi.yaml` and verify with the 1.6 codegen gate:
  - `InstitutionResponse`: add `termCounterpartyAccount`, `onCallCounterpartyAccount`, `closedToNewBusiness`, `hubLegalEntityCode`.
  - `OnboardInstitutionRequest`: add optional accounts; 200 on reopen, 201 on create.
  - Add `GET /api/v1/settings/institutions/granted` (`listGrantedInstitutions`), `PUT /api/v1/settings/institutions/{institutionCode}/counterparty-accounts` (`updateCounterpartyAccounts`), and `PUT /api/v1/settings/institutions/{institutionCode}/enablement/{currency}` (`updateClientEnablement`).
  - `InstitutionResponse.enablements[]`: per granted currency, `grantedTenors`, `grantedNoticePeriods`, `enabledTenors`, `enabledNoticePeriods`.
  - Document deactivate/activate as offboard/re-onboard for a ClientRepresentative.
- [x] 1.2 Mirror 1.1 in `contracts/004-institution-settings/api-v1.md` and remove "proxy" wording. Verify that every operationId and schema field in 1.1 appears in the prose.
- [x] 1.3 Create `contracts/004-institution-settings/schemas/InstitutionUpdatedV1.json`, `asyncapi.yaml` (channel `mmx.institution.{legalEntityCode}`, key `institutionCode`), and `asyncapi-v1.md` (latest-wins-by-`version` consumer contract, `closedToNewBusiness` semantics). Verify the schema is valid JSON Schema (draft used by `OrderExecutedV1.json`) and is the only file defining `InstitutionUpdatedV1` (`grep -rl InstitutionUpdatedV1 contracts`).
- [x] 1.4 Add optional `institutionCode`, `counterpartyAccount`, and routing-context `clientCounterpartyAccount` to `contracts/002-trader-orders-views/schemas/OrderExecutedV1.json`, and mirror them in `contracts/002-trader-orders-views/asyncapi-v1.md`. Verify that `required` is unchanged (`git diff` shows additions only).
- [x] 1.5 Update the cross-org and grants contracts. Verify with the 1.6 codegen gate:
  - `contracts/007-cross-org-routing/openapi.yaml` + `api-v1.md`: add required `AcceptRoutedOrderRequest.clientCounterpartyAccount`, and note that both deployments must be upgraded together.
  - `contracts/006-delegated-institution-grants/api-v1.md`: document the closed-to-new-business semantics and drop "proxy".
- [x] 1.6 Codegen gate: `mvn -q -pl mmx-adapter-in-rest -am compile -DskipTests` succeeds, then `npm run verify:contracts` in `frontend/` succeeds.

## 2. Domain: Institution aggregate and policies (`mmx-domain`)

- [x] 2.1 Write failing `CounterpartyAccountsTest`: blank or over-34-character accounts are rejected; values are trimmed; term and onCall are independent and each optional. `mvn test -pl mmx-domain -Dtest=CounterpartyAccountsTest`
- [x] 2.2 Implement the `CounterpartyAccounts` value object until green.
- [x] 2.3 Write failing `InstitutionTest`. `mvn test -pl mmx-domain -Dtest=InstitutionTest`. Cover:
  - `onboardFromGrant` derives "{name} via {hubLE}" and links `(hubLE, hubCode)`;
  - `offboard()`/`reopen()` flip `closedToNewBusiness` and report whether state changed;
  - `changeAccounts` reports no change when values are equal;
  - `version` increments only on an exported change.
- [x] 2.4 Fold `ThinProxyInstitution` into `Institution` (owning LE, optional `HubInstitutionLink`, accounts, version) until green, and delete `ThinProxyInstitution` and `ThinProxyInstitutionTest` (their behaviour is now covered by `InstitutionTest`).
- [x] 2.5 Write failing `NewBusinessPolicyTest`: SUBSCRIPTION and INCREASE add exposure; DECREASE and REDEMPTION do not; `requireOpenForNewBusiness` throws only for exposure-adding operations on a closed institution. `mvn test -pl mmx-domain -Dtest=NewBusinessPolicyTest`
- [x] 2.6 Implement `NewBusinessPolicy` until green.
- [x] 2.7 Write failing `CounterpartyAccountPolicyTest`: TERM requires the term account and ON_CALL the onCall account; it returns the account used as the snapshot and throws a clear counterparty-account error when missing. `mvn test -pl mmx-domain -Dtest=CounterpartyAccountPolicyTest`
- [x] 2.8 Implement `CounterpartyAccountPolicy` until green.
- [x] 2.9 Extend `OrderAgainstInstitutionPolicyTest`: execute of a Subscription on a closed institution is rejected, a Redemption on a closed institution is allowed, and a missing account for the OrderType is rejected. `mvn test -pl mmx-domain -Dtest=OrderAgainstInstitutionPolicyTest`
- [x] 2.10 Make `OrderAgainstInstitutionPolicy.validateExecute` delegate to both policies until green.
- [x] 2.11 Write failing `EffectiveEnablementTest`: the effective set is grant ∩ client enablement; an inactive grant yields empty; client-enabled-but-not-granted tenors are reported as such; enabling outside the current grant is rejected. `mvn test -pl mmx-domain -Dtest=EffectiveEnablementTest`
- [x] 2.12 Implement `ClientEnablement` and `EffectiveEnablement` until green.
- [x] 2.13 Architecture gate: `mvn test -pl mmx-domain -Dtest=DomainArchitectureTest` passes (the new domain types import no framework).

## 3. Persistence: V27 and JPA mapping (`mmx-bootstrap` migration, `mmx-adapter-out-persistence`)

- [x] 3.1 Write failing `JpaInstitutionRepositoryIntegrationTest` (`@Tag("integration")`). `mvn test -pl mmx-adapter-out-persistence -am -Dtest=JpaInstitutionRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`. Cover:
  - accounts and version round-trip;
  - a second onboarded row for the same `(LE, hubLE, hubCode)` violates the unique index;
  - an onboarded row whose hub institution and hub LegalEntity are absent locally persists (remote-client shape);
  - order `counterparty_account` and `client_counterparty_account` round-trip.
- [x] 3.2 Implement until green:
  - add `V27__institution_counterparty_accounts_and_export.sql` (per design D4: institution columns and partial unique index, drop `fk_institution_hub_institution`/`fk_institution_hub_entity`, order snapshot columns, `client_institution_enablement`, `institution_export_outbox`);
  - map the institution and order entities.
- [x] 3.3 Write failing `JpaClientEnablementRepositoryIntegrationTest` (`@Tag("integration")`): save/replace per currency, and read back an empty set when no row exists. `mvn test -pl mmx-adapter-out-persistence -am -Dtest=JpaClientEnablementRepositoryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`
- [x] 3.4 Declare the `ClientEnablementRepository` out-port in `mmx-application` and implement `JpaClientEnablementRepository` (entity + Spring Data repo in `mmx-adapter-out-persistence`) until green.

## 4. Application: institution settings use cases (`mmx-application`)

- [x] 4.1 Extend `OnboardInstitutionServiceTest`: rename the proxy tests to granted-institution vocabulary and add cases for:
  - optional accounts on onboard;
  - duplicate open onboarding → conflict;
  - re-onboarding an offboarded record reopens the same code (grant required);
  - onboarding a non-granted institution is rejected;
  - an export is scheduled on every success (in-memory `InstitutionExportOutbox` fake).

  `mvn test -pl mmx-application -Dtest=OnboardInstitutionServiceTest`
- [x] 4.2 Implement the `InstitutionExportOutbox` port and the onboarding/re-onboarding changes in `OnboardInstitutionService` until green.
- [x] 4.3 Extend `ManageInstitutionSettingsServiceTest`. `mvn test -pl mmx-application -Dtest=ManageInstitutionSettingsServiceTest`. Cover:
  - hub deactivate/activate schedules DEACTIVATED/REACTIVATED exports;
  - ClientRepresentative deactivate = offboard (OFFBOARDED export, accounts kept, idempotent);
  - a Trader cannot offboard a client institution.
- [x] 4.4 Implement until green.
- [x] 4.5 Write failing `UpdateCounterpartyAccountsServiceTest`: owning-scope role only (Trader on hub, ClientRepresentative on client); cross-scope → not found; unchanged values schedule no export; changed values schedule ACCOUNTS_CHANGED; allowed while closed to new business; clearing an account is rejected while any tenor of that OrderType is client-enabled. `mvn test -pl mmx-application -Dtest=UpdateCounterpartyAccountsServiceTest`
- [x] 4.6 Implement `UpdateCounterpartyAccountsUseCase` and its service until green.
- [x] 4.7 Write failing `ListGrantedInstitutionsServiceTest`: it joins active grants (any currency) with hub display names from a fake `HubInstitutionCatalog` and the client's onboarded institutions; revoked-everywhere institutions drop out; a Trader is rejected. `mvn test -pl mmx-application -Dtest=ListGrantedInstitutionsServiceTest`
- [x] 4.8 Implement the `HubInstitutionCatalog` out-port, `ListGrantedInstitutionsUseCase`, and its service until green.

- [x] 4.9 Write failing `ManageClientEnablementServiceTest`. `mvn test -pl mmx-application -Dtest=ManageClientEnablementServiceTest`. Cover:
  - ClientRepresentative only (a Trader is rejected);
  - enabling outside the current grant is rejected;
  - enabling a Term tenor without the Term account (or OnCall without OnCall) is rejected;
  - a full replacement per currency persists;
  - no institution export is scheduled.
- [x] 4.10 Implement `ManageClientEnablementUseCase`, and its service (with an in-memory `ClientEnablementRepository` fake in tests) until green.

## 5. Application: order paths (`mmx-application`)

- [x] 5.1 Extend `DelegatedGrantDirectoryTest`: resolution takes an `OrderOperation`; DECREASE/REDEMPTION are permitted on an inactive or missing grant; SUBSCRIPTION/INCREASE behave as before. `mvn test -pl mmx-application -Dtest=DelegatedGrantDirectoryTest`
- [x] 5.2 Add the `OrderOperation` parameter to the `DelegatedGrantDirectory` port and its fakes until green.
- [x] 5.3 Extend `IntakeServiceTest` (hub native and local routed). `mvn test -pl mmx-application -Dtest=IntakeServiceTest`. Cover:
  - Subscription on a deactivated institution is rejected and Redemption is accepted;
  - a missing account for the OrderType is rejected;
  - a local routed order to a not-onboarded or offboarded institution (Subscription/Increase) → client-side REJECTED;
  - a local routed Decrease on an offboarded institution is routed;
  - a local routed Subscription on a granted tenor the client has not enabled → REJECTED, while a Redemption on a switched-off notice period is routed;
  - a grant reduction caps the effective set, and restoring it re-admits the tenor with no client action;
  - a missing hub account → REJECTED routing failure;
  - the hub-side order carries the `clientCounterpartyAccount` snapshot.
- [x] 5.4 Implement in `IntakeService` and `RoutedOrderIntake` using `NewBusinessPolicy`/`CounterpartyAccountPolicy` until green.
- [x] 5.5 Extend `RemoteRoutedOrderIntakeTest`: the client refuses a not-onboarded institution, a closed institution or a tenor outside its effective enablement (Subscription/Increase), or a missing account, with REJECTED routing failure and no leg-A send; `RemoteRoutingRequest` carries the hub-native code and `clientCounterpartyAccount`. `mvn test -pl mmx-application -Dtest=RemoteRoutedOrderIntakeTest`
- [x] 5.6 Implement in `RemoteRoutedOrderIntake` and `RemoteRoutingRequest` until green.
- [x] 5.7 Extend `AcceptRoutedHubOrderUseCaseTest`: Redemption on a revoked grant is accepted; Subscription on a revoked grant or deactivated hub institution is rejected; a missing hub account is rejected; `clientCounterpartyAccount` is stored on the hub-side order. `mvn test -pl mmx-application -Dtest=AcceptRoutedHubOrderUseCaseTest`
- [x] 5.8 Implement the operation-aware checks in `AcceptRoutedHubOrderService` until green.
- [x] 5.9 Extend `ExecuteOrderServiceTest`: execute stamps the `counterpartyAccount` snapshot; a cleared account blocks execute (order stays ASSIGNED); a Redemption on a closed institution executes. `mvn test -pl mmx-application -Dtest=ExecuteOrderServiceTest`
- [x] 5.10 Implement in `ExecuteOrderService` until green.
- [x] 5.11 Extend `TermOrderCreationOptionsServiceTest` and `OnCallOrderCreationOptionsServiceTest`. Counterparties must:
  - exclude institutions without the OrderType's account (at the client and at the linked hub institution);
  - exclude offboarded and granted-but-not-onboarded institutions;
  - exclude tenors/notice periods outside the client's effective enablement;
  - for a client, come from onboarded institutions only.

  `mvn test -pl mmx-application -Dtest='TermOrderCreationOptionsServiceTest,OnCallOrderCreationOptionsServiceTest'`
- [x] 5.12 Implement in the order-creation counterparty support until green.

## 6. Adapters out: messaging and integration

- [x] 6.1 Write failing `InstitutionUpdatedV1PayloadMapperTest`: full state including hub link (client) or none (hub), nullable accounts, `changeReason`, `version`, and constant `eventType`; the payload validates against `contracts/004-institution-settings/schemas/InstitutionUpdatedV1.json`. `mvn test -pl mmx-adapter-out-messaging -Dtest=InstitutionUpdatedV1PayloadMapperTest`
- [x] 6.2 Implement `InstitutionUpdatedV1PayloadMapper` and `InstitutionExportOutboxAdapter` (entity + Spring Data repo) until green.
- [x] 6.3 Write failing `InstitutionExportRelayWorkerTest`, modelled on `BackOfficeOutboxRelayWorkerTest`. `mvn test -pl mmx-adapter-out-messaging -Dtest=InstitutionExportRelayWorkerTest`. Cover:
  - publishes to `{prefix}.{legalEntityCode}` with key `institutionCode`;
  - marks the row SENT only after ack;
  - retries, then goes to terminal FAILED at max attempts.
- [x] 6.4 Implement `InstitutionExportRelay` and its worker until green.
- [x] 6.5 Extend `OrderExecutedV1PayloadMapperTest`: every message carries `institutionCode` and `counterpartyAccount` from the order snapshot; routed messages carry `clientCounterpartyAccount`; native messages omit routing context. `mvn test -pl mmx-adapter-out-messaging -Dtest=OrderExecutedV1PayloadMapperTest`
- [x] 6.6 Implement in `OrderExecutedV1PayloadMapper` until green.
- [x] 6.7 Extend `RemoteRoutingGatewayRestAdapterTest`: the leg-A request body includes `clientCounterpartyAccount`. `mvn test -pl mmx-adapter-out-integration -Dtest=RemoteRoutingGatewayRestAdapterTest`
- [x] 6.8 Implement in the REST adapter until green, and add the remote-backed `HubInstitutionCatalog` adapter over `/cross-org/reference/institutions`.

## 7. Adapter in: REST controllers (`mmx-adapter-in-rest`)

- [x] 7.1 Write failing `InstitutionSettingsControllerTest`: maps the new response fields (including `enablements[]`), `listGrantedInstitutions`, `updateCounterpartyAccounts`, `updateClientEnablement`, and onboard 201 (create) vs 200 (reopen); uses only `port.in` and `application.exception`. `mvn test -pl mmx-adapter-in-rest -Dtest=InstitutionSettingsControllerTest -DskipOpenApiGenerate=true`
- [x] 7.2 Implement in `InstitutionSettingsController` until green.
- [x] 7.3 Extend `RoutedOrderAcceptControllerTest`: `clientCounterpartyAccount` is mapped into the accept command, and a request missing it gets 400. `mvn test -pl mmx-adapter-in-rest -Dtest=RoutedOrderAcceptControllerTest -DskipOpenApiGenerate=true`
- [x] 7.4 Implement in the cross-org accept controller until green.

## 8. Bootstrap wiring and integration tests (`mmx-bootstrap`)

- [ ] 8.1 Wire the new beans:
  - `*ModuleConfiguration`: new use cases, `InstitutionExportOutbox`, relay (`mmx.institution.outbox.relay-enabled`, `mmx.institution.kafka.topic-prefix`), and transaction wrapping;
  - `CrossOrgRoutingModuleConfiguration`: the client uses local JPA `InstitutionRepository`, and `HubInstitutionCatalog` is remote on CGEG and in-process otherwise;
  - `application*.yml`: the new keys.

  Verify with `mvn test -pl mmx-bootstrap -am -Dtest=HexagonalArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false`.
- [ ] 8.2 Update seed data so existing integration tests describe the new model:
  - `RestTestInstitutionBootstrap`: seeded institutions get both counterparty accounts;
  - the SQL-seeding tests (`OrderRoutingIntakeIntegrationTest`, `OrderRestApiIntegrationTest`, `OrderCreationOptionsRestApiIntegrationTest`, `TermRateRestApiIntegrationTest`, `TransactionalOrderLifecycleAtomicityIntegrationTest`, `RoutedExecutionHandoffKafkaIntegrationTest`, `CrossOrgClientDeploymentIntegrationTest`): insert accounts and onboarded client institutions, replacing proxy wording;
  - client-role contexts: set `mmx.institution.outbox.relay-enabled=false`.

  Verify with `mvn test -pl mmx-bootstrap -am -Dtest=OrderRoutingIntakeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`.
- [ ] 8.3 Extend `RoleScopedSettingsRestApiIntegrationTest`. `mvn test -pl mmx-bootstrap -am -Dtest=RoleScopedSettingsRestApiIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`. Cover:
  - ClientRepresentative granted list → onboard (nothing enabled) → set accounts → enable tenors → offboard → re-onboard;
  - enabling without the account, or outside the grant, is rejected;
  - a Trader sets hub accounts;
  - a cross-scope account change is rejected;
  - each mutation commits exactly one `institution_export_outbox` row.
- [ ] 8.4 Implement any wiring or mapping gaps until green.
- [ ] 8.5 Extend `OrderRoutingIntakeIntegrationTest`. `mvn test -pl mmx-bootstrap -am -Dtest=OrderRoutingIntakeIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`. Cover:
  - a Redemption on a deactivated grant is routed;
  - a Subscription on an offboarded institution → REJECTED;
  - a Subscription on a granted tenor the client has not enabled → REJECTED;
  - a missing hub account → REJECTED routing failure, with no hub-side order.
- [ ] 8.6 Implement any gaps until green.
- [ ] 8.7 Extend `CrossOrgClientDeploymentIntegrationTest`. `mvn test -pl mmx-bootstrap -am -Dtest=CrossOrgClientDeploymentIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false`. Cover:
  - the client deployment persists an onboarded institution with no local hub LE/institution row;
  - its institution list and client enablement are served and stored locally, with no hub write;
  - leg A carries `clientCounterpartyAccount`.
- [ ] 8.8 Implement any gaps until green.
- [ ] 8.9 Write failing `InstitutionExportKafkaIntegrationTest` (`@Tag("e2e")`): onboarding at `PAR` publishes one `InstitutionUpdatedV1` to `mmx.institution.PAR` (and none to `mmx.institution.LOC`), and the payload validates against the canonical schema. Also extend `ExecutionHandoffKafkaIntegrationTest` and `RoutedExecutionHandoffKafkaIntegrationTest` to assert `counterpartyAccount` / `clientCounterpartyAccount`. `mvn test -pl mmx-bootstrap -am -Dtest='InstitutionExportKafkaIntegrationTest,ExecutionHandoffKafkaIntegrationTest,RoutedExecutionHandoffKafkaIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false`
- [ ] 8.10 Implement any relay or config gaps until green.

## 9. Scripts and dev stack

- [ ] 9.1 In `scripts/register-schemas.sh`, register `contracts/004-institution-settings/schemas/InstitutionUpdatedV1.json` under subject `mmx.institution-value` with `BACKWARD`. Verify that `./mmx-start.sh`, then `curl` of `/subjects/mmx.institution-value/versions`, returns a version.
- [ ] 9.2 In `scripts/cross-org-smoke.sh`, set LOC counterparty accounts, onboard the granted institution at CGEG with accounts, and use the CGEG onboarded institution for intake. Verify that `./mmx-cross-org-start.sh`, then `./scripts/cross-org-smoke.sh`, completes the flow end to end.
- [ ] 9.3 In `scripts/seed-demo-orders.sh`, set counterparty accounts on the institutions it uses. Verify that the script runs against `./mmx-start.sh` without intake rejections.

## 10. Frontend (`frontend/`, standalone components + existing API service conventions)

- [ ] 10.1 Extend `institution-settings-api.service.ts` with `listGrantedInstitutions`, `updateCounterpartyAccounts`, and `updateClientEnablement` using the generated types, with a spec next to the service. `npx ng test --include='src/app/core/api/institution-settings-api.service.spec.ts'`
- [ ] 10.2 Update `institution-settings-list.component.ts` (single "Institutions" heading, open/closed badge, accounts columns, no "proxy" text) and its spec, red-first. `npx ng test --include='src/app/features/institution-settings/institution-settings-list.component.spec.ts'`
- [ ] 10.3 Update `institution-settings-onboard.component.ts` and its spec, red-first. `npx ng test --include='src/app/features/institution-settings/institution-settings-onboard.component.spec.ts'`. The client picks from granted institutions not yet open, with optional accounts; the Trader form is unchanged apart from optional accounts.
- [ ] 10.4 Update `institution-settings-detail.component.ts` and its spec, red-first. `npx ng test --include='src/app/features/institution-settings/institution-settings-detail.component.spec.ts'`. Add an accounts edit form for both roles and offboard/re-onboard actions for a ClientRepresentative. The grant panel becomes per-currency client-enablement toggles: tenors outside the grant are disabled, "enabled, not granted" is flagged, and saving calls `updateClientEnablement`.
- [ ] 10.5 Type gate: `npm run typecheck` succeeds.

## 11. Docs and agent memory

- [ ] 11.1 Update `docs/agents/codebase-map.md` with the new outbox/relay, V27, client enablement (`ClientEnablement`/`EffectiveEnablement`), `HubInstitutionCatalog`, the institution-export contracts, and the retired `ThinProxyInstitution`. Verify that every path it names exists.
- [ ] 11.2 Update the Serena memories (`mem:backend/core`, `mem:frontend/core` as relevant): the remote client now stores onboarded institutions, the new relay flag gotcha for client-role tests, and "closed to new business" operation-awareness. Verify by reading the memories back.
- [ ] 11.3 Check the ADRs and glossary: ADR 0008 and `CONTEXT.md` match the delivered behaviour (accounts, export triggers, snapshot fields). Verify by searching the codebase for any leftover "proxy" in UI strings or error messages (`grep -rni proxy frontend/src/app backend/*/src/main`), apart from the dev-server proxy config.

## 12. Final verification

- [ ] 12.1 Run full `cd backend && mvn test` — all modules green
- [ ] 12.2 Run `npm run test` in `frontend/` — green
- [ ] 12.3 `openspec validate client-institution-onboarding --strict` passes, and the contract prose mirrors (`api-v1.md`, `asyncapi-v1.md`) match their YAML (SDD parity).
