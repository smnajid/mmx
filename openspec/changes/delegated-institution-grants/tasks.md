# Tasks

## 1. Domain — grant and thin-proxy model (red-first TDD)

- [x] 1.1 Model `DelegatedInstitutionGrant` aggregate keyed by `(hubInstitutionCode, clientLegalEntityCode, currency)` with `enabledTenors` and `enabledNoticePeriods` sets, `active` flag. Unit test (red): create/update a grant; key uniqueness; activation/deactivation toggles `active` without hard-delete.
- [x] 1.2 Model the enabled-subset rule: `enabledTenors` ⊆ hub managed-currency `enabledTenors`; `enabledNoticePeriods` ⊆ hub managed-currency `enabledNoticePeriods`; allowed tenor codes `1W,2W,1M,3M,6M,1Y` and notice codes `24H,48H`. Unit test (red): subset within hub set accepted; out-of-set rejected; invalid codes rejected.
- [x] 1.3 Model `ThinProxyInstitution` referencing `(hubLegalEntityCode, hubInstitutionCode)` with a derived-name policy `"{hubInstitution.displayName} via {hubLegalEntityCode}"`. Unit test (red): derived name is deterministic; proxy carries no own rate curves.
- [x] 1.4 Model grant prospective-only semantics: create/update/deactivate does not alter already-routed/executed orders. Unit test (red): history unchanged after a grant edit.

## 2. Application — grant CRUD, proxy onboard, directory port (red-first TDD)

- [x] 2.1 Define `DelegatedGrantDirectory` out-port: resolve `(clientLegalEntityCode, proxyInstitutionCode, currency)` + tenor|notice → granted/not-granted, considering `active`. Unit test the port contract with a fake (red): granted tenor confirmed; out-of-set rejected; inactive grant → no grant.
- [x] 2.2 Implement grant CRUD use cases (create/update/deactivate) restricted to Trader role on the TradingHub; validate the hub institution is active and the enabled subset is within the hub managed currency. Unit test (red): Trader success; ClientRepresentative rejected; inactive hub institution rejected; subset violation rejected.
- [x] 2.3 Implement role-qualified institution onboard: Trader → native (displayName); ClientRepresentative → proxy selected from an active grant, derived name, generated `institutionCode` via acronym rules, references `(hub, hubInstitution)`; reject proxy without an active grant and reject a client-supplied free-form proxy name. Unit test (red): proxy from grant succeeds; proxy without grant rejected; free-form name rejected.
- [x] 2.4 Implement role-scoped institution list: Trader sees native hub institutions; ClientRepresentative sees only their client's proxies. Unit test (red): client list excludes native hub institutions.
- [x] 2.5 Implement reference-data ownership enforcement: ClientRepresentative receives an authorisation error on currency/term-rate/OnCall mutation use cases; client read use cases target the connected hub's `LegalEntityCode`. Unit test (red): client read returns hub data; client mutation rejected.

## 3. Contracts — contract-first OpenAPI (grant CRUD + role-scoped mirrors)

- [x] 3.1 Create `contracts/006-delegated-institution-grants/openapi.yaml` (+ `api-v1.md`) for grant create/read/update/deactivate; declare Trader-only authorisation and role/active-scope semantics.
- [x] 3.2 Document role-scoped semantics in the existing settings mirrors: `contracts/003-managed-currency-settings/api-v1.md`, `contracts/004-institution-settings/api-v1.md`, `contracts/005-term-rate-settings/api-v1.md` — `ClientRepresentative` read-only on currencies/term-rates and proxy-only on institutions; no new client mutation endpoints and no breaking schema changes.
- [x] 3.3 Regenerate codegen; confirm generated server interfaces and Angular clients align with the published contracts.

## 4. REST adapter — grant endpoints and role-scoped authorisation

- [x] 4.1 Implement grant CRUD endpoints (Trader-only) from the generated OpenAPI interfaces; role + active-scope LegalEntity authorisation. Integration test (red): Trader CRUD succeeds; ClientRepresentative receives 403.
- [x] 4.2 Add role-scoped authorisation on existing settings endpoints: `ClientRepresentative` mutation of currencies/term-rates/OnCall rates returns 403; institution onboard accepts the proxy path and rejects native onboard from a client. Integration test (red): client mutation 403; client proxy onboard from grant succeeds.

## 5. Persistence — JPA + Flyway (additive)

- [x] 5.1 Flyway: `delegated_institution_grant` table (`hub_institution_code`, `client_legal_entity_code`, `currency`, `enabled_tenors`, `enabled_notice_periods`, `active`; PK over the tuple); add nullable proxy columns `hub_legal_entity_code`, `hub_institution_code` to `institution`; scope reference-data rows to the hub `LegalEntityCode` (hub-owned).
- [x] 5.2 JPA mapping + repositories for the grant directory (keyed by `(client, proxy, currency)`), proxy institutions, and hub-scoped reference-data reads. Integration test (red): directory resolves granted/not-granted; client reference-data read returns the hub's rows.

## 6. Bootstrap — wiring

- [x] 6.1 Wire grant CRUD use cases, `DelegatedGrantDirectory` adapter (consumed by Change B's `RouteOrderUseCase`), role-scoped settings authorisation, and hub-scoped reference-data reads into the application/bootstrap configuration.
- [x] 6.2 Confirm the `DelegatedGrantDirectory` port is consumable by Change B's routing intake validation (integration smoke against the adapter).

## 7. Frontend (Angular) — hub grant management + client settings surface

- [x] 7.1 TradingHub grant management screen: list/create/update/deactivate grants per `(institution, client, currency)` with enabled tenor/notice toggles bounded by the hub managed currency. Component test (red): create a grant; subset bounded; ClientRepresentative cannot access.
- [x] 7.2 TradingClient Institutions screen: list proxies only; onboard a proxy from a grant; enable tenors/notice only within the grant's enabled set (out-of-set controls disabled/hidden). Component test (red): only granted toggles enabled.
- [x] 7.3 Read-only rendering for `ClientRepresentative` on Currencies, Term rates, and OnCall rates screens (disabled controls, role-gated). Component test (red): no mutate controls rendered for client role.

## 8. Final verification (single gate before marking complete)

- [x] 8.1 `openspec validate delegated-institution-grants` passes; spec–code parity confirmed (new `delegated-institution-grants` capability; deltas to `institution-onboarding`, `order-institution-constraints`, `managed-currency-settings`, `term-rate-daily-upload`, `oncall-rate-curve-management`).
- [x] 8.2 Backend: `./gradlew test` green, including grant domain, enabled-subset bounds, proxy derived name, directory port, role-scoped authorisation, and client intake grant validation integration tests (Testcontainers).
- [x] 8.3 Contracts: `contracts/006` OpenAPI present; `003`/`004`/`005` mirrors document role-scoped semantics; codegen in sync.
- [x] 8.4 Frontend: `ng test` (Vitest) green for grant management, client proxy institution surface, and read-only currency/term/OnCall rendering.
