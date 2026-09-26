## 1. Domain — tenancy & identity model (`mmx-domain`)

- [x] 1.1 Write failing `LegalEntityTenancyTest` — `mvn test -pl mmx-domain -Dtest=LegalEntityTenancyTest` covering: LegalEntity belongs to one Organisation; `LegalEntityCode` global uniqueness across organisations; TradingHub/TradingClient role mutually exclusive; TradingClient connected to one same-Organisation hub; cross-org connection rejected.
- [x] 1.2 Implement `Organisation`, `LegalEntity`, `LegalEntityCode`, `OrganisationCode`, `TradingHubRole`/`TradingClientRole`, and hub–client connection in `mmx-domain` until `LegalEntityTenancyTest` is green.
- [x] 1.3 Write failing `MmxUserIdentityTest` — `mvn test -pl mmx-domain -Dtest=MmxUserIdentityTest` covering: MMXUser holds N `(LegalEntityCode, Role)` scopes; a user with no scopes is unauthorised; re-scope validates the held pair.
- [x] 1.4 Implement `MMXUser`, `Role` (`TRADER`/`CLIENT_REPRESENTATIVE`), and the `(LegalEntityCode, Role)` scope value object until `MmxUserIdentityTest` is green.
- [x] 1.5 Write failing `OrderLegalEntityScopeTest` — `mvn test -pl mmx-domain -Dtest=OrderLegalEntityScopeTest` covering: a `MoneyMarketOrder` carries a non-null owning `LegalEntityCode`; equality/identity includes it.
- [x] 1.6 Add owning `LegalEntityCode` to the `MoneyMarketOrder` aggregate until `OrderLegalEntityScopeTest` is green.

## 2. Application — scope, intake, queries (`mmx-application`)

- [x] 2.1 Define `port/out` repositories: `OrganisationRepository`, `LegalEntityRepository`, `MmxUserRepository` (lookup by id, load scopes), and a `ScopeContext` read model for the active `(LegalEntityCode, Role)`.
- [x] 2.2 Write failing `IntakeLegalEntityValidationTest` — `mvn test -pl mmx-application -Dtest=IntakeLegalEntityValidationTest` covering: intake rejects missing/unknown `legalEntityCode`; intake rejects a code not belonging to the PM's Organisation; idempotency is per `(legalEntityCode, externalOrderReference)` (same ref + different entity → two orders).
- [x] 2.3 Extend the intake use case to validate `legalEntityCode` and scope idempotency per `(legalEntityCode, externalOrderReference)` until `IntakeLegalEntityValidationTest` is green.
- [x] 2.4 Write failing `DeskQueryScopeTest` — `mvn test -pl mmx-application -Dtest=DeskQueryScopeTest` covering: `DeskOrderQueries` cohorts filter by the active `LegalEntityCode`; detail lookup for an order in another entity reports not-found.
- [x] 2.5 Extend `DeskOrderQueryService` to filter cohorts by the active `LegalEntityCode` and treat cross-entity detail as not-found until `DeskQueryScopeTest` is green.
- [x] 2.6 Write failing `ReScopeUseCaseTest` — `mvn test -pl mmx-application -Dtest=ReScopeUseCaseTest` covering: re-scope to a held `(LegalEntity, Role)` succeeds and rebinds; re-scope to an unheld pair is rejected.
- [x] 2.7 Implement the re-scope use case until `ReScopeUseCaseTest` is green.

## 3. Contracts — contract-first (`contracts/`)

- [x] 3.1 Update `contracts/001-mm-order-processing/openapi.yaml`: add required `legalEntityCode` (string, 3 chars) to `ReceiveOrderRequest` and `ReceiveOrderResponse`; update `contracts/001-mm-order-processing/api-v1.md` mirror.
- [x] 3.2 Update `contracts/002-trader-orders-views/openapi.yaml`: rename `TraderIdHeader` (`X-Trader-Id`) to `UserIdHeader` (`X-User-Id`) on all trader operations; update `contracts/002-trader-orders-views/api-v1.md` mirror.
- [x] 3.3 Regenerate server stubs: `mvn -pl backend/mmx-adapter-in-rest -am compile -DskipTests` (codegen runs in compile).

## 4. REST adapter (`mmx-adapter-in-rest`)

- [x] 4.1 Adapt `OrderIntakeController` + request mapper to read and forward `legalEntityCode` from `ReceiveOrderRequest`; no business rules in the adapter.
- [x] 4.2 Replace `X-Trader-Id` handling with `X-User-Id` across trader controllers; add a scope resolver/filter (thin) that builds the `ScopeContext` from the session and passes it to use cases — depends on `application.port.in`, not on services (Tier 2).
- [x] 4.3 Adapt `OrderManagementController` desk reads to pass the active scope into `DeskOrderQueries`.
- [x] 4.4 Compile gate: `mvn -pl backend/mmx-adapter-in-rest -am compile`.

## 5. Persistence (`mmx-adapter-out-persistence`)

- [x] 5.1 Add Flyway migrations: `organisation`, `legal_entity` (with role + hub-connection), `mmx_user`, `mmx_user_scope`; add non-null `legal_entity_code` to `money_market_order` with a configured default back-fill for existing rows.
- [x] 5.2 Add JPA entities + Spring Data repositories for Organisation/LegalEntity/MMXUser/scopes; add `legalEntityCode` to the order JPA entity and order repository query predicates.
- [x] 5.3 Implement the `port/out` repository adapters (`JpaOrganisationRepository`, `JpaLegalEntityRepository`, `JpaMmxUserRepository`) and update the order persistence adapter to filter by `LegalEntityCode`.
- [x] 5.4 Compile gate: `mvn -pl backend/mmx-adapter-out-persistence -am compile`.

## 6. Bootstrap (`mmx-bootstrap`)

- [x] 6.1 Add deployment config identifying the served `OrganisationCode` (e.g. `application.yml` / `*ModuleConfiguration`); wire `DeskOrderQueries`, scope resolver, tenancy/user repositories, and re-scope use case beans; add transactional wrappers.
- [x] 6.2 Write failing `RestScopeIntegrationTest` — `mvn test -pl mmx-bootstrap -Dtest=RestScopeIntegrationTest` (Testcontainers) covering: a trader scoped to `PAR` cannot read a `LOC` order (not-found); intake without `legalEntityCode` is 400; intake with unknown code is 400; `X-Trader-Id` is rejected in favour of `X-User-Id`.
- [x] 6.3 Wire the scope filter into the web layer and fix integration paths until `RestScopeIntegrationTest` is green.

## 7. Frontend (`frontend/`)

- [x] 7.1 Update `core/api/*-api.service.ts` clients to send `X-User-Id` (and role context) instead of `X-Trader-Id`; pass `legalEntityCode` on intake from the PM order-creation widget.
- [x] 7.2 Role-gate the shell: hide the **Desk** entry and block `/oncall` + `/term` routes for `ClientRepresentative`; redirect to Settings. Vitest beside the shell component covering the gate + redirect.
- [x] 7.3 Add an active-scope switcher in the shell that calls the re-scope endpoint and rebinds the session; Vitest covering re-scope to a held vs unheld scope.

## 8. Spec & contract alignment (SDD — governance Principle VI)

- [x] 8.1 Verify `openspec/specs/money-market-order-lifecycle`, `desk-order-queries`, `trader-desk-navigation`, `back-office-accounting-handoff` deltas match implemented behaviour; archive readiness check with `openspec validate`.
- [x] 8.2 Confirm `contracts/001` and `contracts/002` OpenAPI + `api-v1.md` mirrors match the running controllers (contract-first parity).

## 9. Final verification

- [x] 9.1 Run full `cd backend && mvn test` — all modules green.
- [x] 9.2 Run `npm run test` in `frontend/` — green.
- [x] 9.3 `openspec validate` for the change — green.
