# Design

## Context

See `proposal.md` (Why) and the two spec deltas. Current state that shapes the approach:

- `TermOrderCreationOptionsService` / `OnCallOrderCreationOptionsService` (`mmx-application`) compute currencies, tenors and notice periods from `ManagedCurrency.enabledTenors|enabledNoticePeriods` (hub own-intake enablement) plus rate existence, with no notion of the requesting LegalEntity. Counterparties already branch on `LegalEntity.isTradingClient()` through `OrderCreationDelegatedCounterpartySupport.forClient`, which applies `EffectiveEnablement` and the "open to new business with counterparty account" policy (`OrderAgainstInstitutionPolicy`, Subscription).
- On a client deployment (`mmx.cross-org.role=client`, `reference-data-remote=true`, profile `cgeg`), `ManagedCurrencyRepository`, `HubInstitutionCatalog`, `TermRateRepository` and `DelegatedGrantRepository` are remote-backed (`mmx-adapter-out-integration`). `RemoteTermRateRepository.findLatestRatePerInstitution` / `findDistinctCurrenciesWithTermRates` return `List.of()`. `OnCallRateRepository` stays the local JPA bean, whose table is empty on a client.
- `RemoteReferenceDataHttp.getList` maps every failure to `List.of()`.
- Institutions (`InstitutionRepository`) and client enablement (`ClientEnablementRepository`) are local to the client (ADR 0008). The client's `LegalEntity` (CGD) is stored locally on CGEG (V26 seed).
- Cross-org reads are served by `CrossOrgReferenceDataController` (`mmx-adapter-in-rest`, contract `contracts/007-cross-org-routing/openapi.yaml`), authenticated by `X-MMX-CrossOrg-Key` → proven `LegalEntityCode`.

## Goals / Non-Goals

**Goals:**
- One option rule per LegalEntity role, evaluated in `mmx-application`, identical for local (PAR) and remote (CGD) clients.
- Remote clients get real hub rates through the existing read ports; failures surface as `503`.

**Non-Goals:**
- Client-side Settings rate views (term-rate trading days, on-call segments per client institution) — they keep today's behaviour; separate ticket.
- Offering reducing operations (Decrease/Redemption) on institutions closed to new business — the wizard feeds new business only.
- Caching hub reads.
- The CGED → CGEG text rename (ticket `.scratch/cross-org-order-routing/issues/10-rename-cged-to-cgeg.md`).

## Decisions

**D1 — Client branch lives in the option services, sharing the counterparty helper.**
`listCurrencies`, `listTenors`, `listNoticePeriods` gain a `LegalEntityCode` parameter (use-case ports in `application.port.in`). When the LegalEntity is a TradingClient, the service builds the client's *candidates* — onboarded institutions open to new business with the OrderType's counterparty account, keyed by linked hub institution code, each with its `EffectiveEnablement` for the currency — by extracting that loop from `OrderCreationDelegatedCounterpartySupport.forClient` into a shared method (same class). A currency/tenor/notice period is offered when some candidate's effective enablement permits the term **and** the hub rate read for `(currency, term)` contains that candidate's hub institution (and, for a same-Organisation client, the linked hub institution admits it — existing `linkedHubInstitutionAdmits`). Hub branch is today's code, unchanged. Unknown LegalEntity → empty result (matches counterparties).
*Alternative rejected:* a separate client-only service — duplicates the hub/client fork that counterparties already own.

**D2 — Remote rates through the existing ports (Q4 = per-query endpoints).**
- `RemoteTermRateRepository.findLatestRatePerInstitution(currency, tenor)` → `GET /api/v1/cross-org/reference/term-rates/latest?currency&tenor`.
- New `RemoteOnCallRateRepository implements OnCallRateRepository` (`mmx-adapter-out-integration`): `findOpenSegmentsByCurrencyAndNoticePeriod` → `GET /api/v1/cross-org/reference/oncall-segments?currency&noticePeriod`; `findSegmentsCoveringDate` → same path with `valueDate`. Write methods throw `UnsupportedOperationException` (as `RemoteTermRateRepository` does). `findByInstitutionCode` returns `List.of()` — today's effective client behaviour (client institution codes never have hub segments); out of scope per Non-Goals.
- `findDistinctCurrenciesWithTermRates` / `findDistinctCurrenciesWithOpenOnCallSegments` on remote adapters throw `UnsupportedOperationException`: after D1 only the hub branch calls them, and a hub never runs with remote reference data. A silent empty here is exactly the bug being fixed.
- Wired as `@Primary` beans under `mmx.cross-org.reference-data-remote=true` in `CrossOrgRoutingModuleConfiguration` (`mmx-bootstrap`).

**D3 — Hub-side grant scoping in the controller's read path.**
`CrossOrgReferenceDataController` resolves the proven client, loads its active grants (`DelegatedGrantRepository.findByClientLegalEntityCode`), and filters rows to granted `(hubInstitutionCode, currency)`. Applies to the new reads and the existing `/term-rates`. Filtering on the grant key only — tenor/notice-period permission is evaluated on the client via `EffectiveEnablement` (single place). Kept in the adapter because it is a transport-boundary policy over read ports, like the existing `/grants` auto-scoping; no new use case.

**D4 — Failure model.**
New `HubReferenceDataUnavailableException` in `application.exception` (adapters may depend on application). `RemoteReferenceDataHttp.getList` throws it on non-200, I/O error, timeout or parse error. `GlobalExceptionHandler` maps it to `503` with the existing error body. No retry/circuit breaker on reads (the PM retries via the widget).

**D5 — Contract-first.**
`contracts/002-trader-orders-views/openapi.yaml` + `api-v1.md`: `legalEntityCode` (required, 3 chars) on the four list operations; `503` response on all order-creation operations. `contracts/007-cross-org-routing/openapi.yaml` + `api-v1.md`: two new operations, grant-scoping statement on all rate reads, response schemas `CrossOrgTermRateResponse` (reused) and new `CrossOrgOnCallSegmentResponse` (`institutionCode`, `currency`, `noticePeriod`, `rate`, `valueDate`, `endDate`, `status`). Codegen via the `mmx-adapter-in-rest` OpenAPI generator; widget types via `npm run generate:api`.

**D6 — Frontend.**
`WizardApiService.listTermCurrencies|listOnCallCurrencies|listTermTenors|listOnCallNoticePeriods` take `legalEntityCode` from the existing host config; step components pass it. The currency step's existing error branch already renders a non-2xx response with Retry — covered by a new Vitest case for 503. Settings components already show HTTP error messages; no change.

**Test placement (strict TDD, red first):**
- `fast` (`mmx-application`): `TermOrderCreationOptionsServiceTest`, `OnCallOrderCreationOptionsServiceTest` — client scenarios with in-memory fakes.
- `fast` (`mmx-adapter-out-integration`): `RemoteReferenceDataAdapterTest` (existing, stubbed HTTP) — new endpoints, throw-on-failure.
- `fast` (`mmx-adapter-in-rest`): `CrossOrgReferenceDataControllerTest`, `OrderCreationOptionsControllerTest` — grant scoping, 400 without `legalEntityCode`, 503 mapping.
- `integration` (`mmx-bootstrap`): one client-role context test (no `rest-test` profile, outbox relays off) driving CGD's currency → counterparty path against a stubbed hub.
- Vitest: `step-currency.component.spec.ts`, `wizard-api.service.spec.ts`.

## Risks / Trade-offs

- [Several hub round-trips per step for a client: one rate read per (currency, enabled term)] → bounded by the client's enabled terms (single digits); acceptable without caching. Revisit with a cache if latency shows.
- [Breaking: required `legalEntityCode`] → only in-repo widget consumes the PM API; updated in the same delivery.
- [Breaking: `/cross-org/reference/term-rates` now grant-scoped] → its only consumer is the CGEG client adapter, which already only needs granted rows.
- [503 now appears on Settings reads that used to render empty] → Settings components already display HTTP errors; verified per component during apply.
- [Remote `findDistinct*` throwing] → only reachable through the hub branch; covered by tests asserting client path never calls them.

## Migration Plan

Deploy LODH (hub) before CGEG (client): new hub endpoints must exist before the client calls them. Rollback: redeploy previous CGEG first (it ignores the new endpoints), then LODH. No schema migration.
