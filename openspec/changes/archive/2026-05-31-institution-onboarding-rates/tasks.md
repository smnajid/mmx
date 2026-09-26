## 1. Feature specs and contracts (004 + 002 delta)

- [x] 1.1 Create `specs/004-institution-settings/spec.md`, `data-model.md`, and `plan.md` aligned with OpenSpec `institution-onboarding` capability
- [x] 1.2 Author `specs/004-institution-settings/contracts/openapi.yaml` (list, get, onboard with `displayName` only, activate, deactivate; optional `activeOnly` query)
- [x] 1.3 Mirror institution settings REST in `specs/004-institution-settings/contracts/api-v1.md`
- [x] 1.4 Delta `specs/002-trader-orders-views/contracts/openapi.yaml`: `ExecuteOrderRequest` requires `institutionCode`; remove client-supplied `counterparty` on execute input; expose `institutionCode` on response/detail where applicable
- [x] 1.5 Update `specs/002-trader-orders-views/contracts/api-v1.md` execute section to match OpenAPI (breaking change documented)
- [x] 1.6 Sync main feature specs: delta `specs/002-trader-orders-views/spec.md` for execute institution rules; archive or merge OpenSpec capability deltas per SDD gate
- [x] 1.7 Run OpenAPI codegen for `004` settings API and regenerated `002` execute types in `mmx-adapter-in-rest`; verify `mvn compile` on reactor

## 2. Persistence (`mmx-adapter-out-persistence`)

- [x] 2.1 Add Flyway migration `institution` (`institution_code` PK, `display_name`, `active`, timestamps)
- [x] 2.2 Add optional Flyway column `institution_code` on order/execution persistence if design adopts audit field (recommended in `design.md`)
- [x] 2.3 Add JPA entity, Spring Data repository, and `JpaInstitutionRepository` implementing `InstitutionRepository` port (`findByInstitutionCode`, `listActive`, `maxSuffixForAcronym`, `existsAny`, `save`)
- [x] 2.4 Write `JpaInstitutionRepositoryTest`: save/list, suffix query for `HSBC-%`, empty catalog

## 3. Domain (`mmx-domain`) — TDD

- [x] 3.1 Add `Institution` catalog snapshot type (no Spring)
- [x] 3.2 Add `InstitutionCodeAcronym` (or domain service) with `deriveAcronym(displayName)` per design rules
- [x] 3.3 Write failing `InstitutionCodeAcronymTest`: single word, multi-word cap at 6, `INST` fallback
- [x] 3.4 Add `OrderAgainstInstitutionPolicy` with `validateCatalogNotEmpty` and `validateExecute(institutionCode, Institution)`
- [x] 3.5 Write failing `OrderAgainstInstitutionPolicyTest`: empty catalog, unknown code, inactive institution
- [x] 3.6 Implement acronym + policy until domain tests pass (green)

## 4. Application ports and services (`mmx-application`) — TDD

- [x] 4.1 Add `InstitutionRepository` in `port/out`
- [x] 4.2 Add inbound port `ManageInstitutionSettingsUseCase` and `ManageInstitutionSettingsService`
- [x] 4.3 Write failing `ManageInstitutionSettingsServiceTest`: first `HSBC` → `HSBC-01`, second → `HSBC-02`, multi-word `BCI-01`, blank name reject, suffix overflow `INST-99`
- [x] 4.4 Implement onboard (derive acronym → allocate suffix → persist in one transaction) until settings service tests pass
- [x] 4.5 Extend `ExecuteOrderCommand` with `institutionCode`; remove free-text counterparty from command input
- [x] 4.6 Write failing `ExecuteOrderServiceTest`: empty catalog reject, unknown/inactive institution, success sets `counterparty` from institution `displayName` (and `institutionCode` on order if persisted)
- [x] 4.7 Wire `ExecuteOrderService`: load institution, run policy, call aggregate execute with derived counterparty
- [x] 4.8 Confirm intake/amount paths do **not** invoke institution policy (explicit test or documented absence in existing receive/update tests)

## 5. Settings REST (`mmx-adapter-in-rest`)

- [x] 5.1 Implement generated `InstitutionSettingsApi` controller + mapper (DTO mapping only; no business rules)
- [x] 5.2 Write `InstitutionSettingsControllerTest`: list empty, onboard 201 with generated code, get by code, activate/deactivate, validation 4xx
- [x] 5.3 Map institution domain failures to contract-aligned problem responses in `GlobalExceptionHandler`

## 6. Execute REST delta (`mmx-adapter-in-rest` + `specs/002`)

- [x] 6.1 Update `OrderRestMapper` / `ExecuteOrderCommand` mapping: require `institutionCode` from generated `ExecuteOrderRequest`
- [x] 6.2 Write failing `OrderExecutionControllerTest`: 400 missing/unknown/inactive institution; 400/503 empty catalog; 201 success with `counterparty` on response
- [x] 6.3 Update `OrderExecutedV1PayloadMapperTest`: handoff `counterparty` equals institution `displayName` after execute
- [x] 6.4 Run `mvn test` on `mmx-adapter-in-rest` and `mmx-application` — green

## 7. Frontend Settings hub and navigation (`frontend/`)

- [x] 7.1 Add `features/settings/settings-shell` with sub-nav **Currencies | Institutions**; route `/settings` → default `/settings/currencies`
- [x] 7.2 Replace standalone **Currencies** header link with **Settings** (`routerLink="/settings"`); add **Desk** link; update `showDeskNav()` / `app.spec.ts` for header active states
- [x] 7.3 Nest existing currency routes under settings shell; hide desk queue tabs on `/settings/**`
- [x] 7.4 Write Vitest: Settings active on `/settings/institutions`; Desk active on `/term/received`; sub-nav switches Currencies ↔ Institutions
- [x] 7.5 Verify `DeskReturnService` + **Back to desk** on currency and institution list screens

## 8. Frontend institution settings (`frontend/`)

- [x] 8.1 Add API client/service for `004` institution settings endpoints
- [x] 8.2 Add `features/institution-settings/` routes: list (`institutionCode` + `displayName`), onboard (display name only), detail (read-only code, activate/deactivate)
- [x] 8.3 Write Vitest: onboard submits no `institutionCode` field; list shows generated code; shared `settings-panel` / desk theme tokens per `currency-settings-ui` delta
- [x] 8.4 Extract or reuse shared settings SCSS partial if institution screens duplicate currency list styling

## 9. Frontend execute institution picker (`frontend/`)

- [x] 9.1 Update order-detail execute form: autocomplete over active institutions (`displayName` label, `institutionCode` value); label field **Counterparty** per ubiquitous language
- [x] 9.2 Disable execute when catalog empty; show guidance/link to `/settings/institutions`
- [x] 9.3 Write Vitest: picker excludes inactive; submit sends `institutionCode` only; empty catalog disables execute
- [x] 9.4 Update any Cypress/e2e execute flows to onboard institution before execute

## 10. Bootstrap, seeds, and docs

- [x] 10.1 Register `InstitutionSettingsModuleConfiguration`, `InstitutionRepository`, policy beans; extend `OrderModuleConfiguration` / `TransactionalExecuteOrderUseCase` wiring
- [x] 10.2 Add `RestTestInstitutionBootstrap` (or extend test bootstrap) for integration tests requiring catalog rows
- [x] 10.3 Update `scripts/seed-demo-orders.sh` and quickstart: onboard ≥1 institution via settings API before execute
- [x] 10.4 Update `specs/004-institution-settings/quickstart.md` and programme cross-links in `PROGRAMME.md` if steps change

## 11. Final verification

- [x] 11.1 Run full `mvn test` from `backend/`
- [x] 11.2 Run `npm run test` (or `ng test`) in `frontend/`
- [x] 11.3 Confirm `004` OpenAPI and `002` execute delta match runtime (`api-v1.md` mirrors, no contract drift)
