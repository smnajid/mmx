# Architecture deepening backlog

Source: `/improve-codebase-architecture` pass on 2026-07-05.
Vocabulary: domain terms from [CONTEXT.md](../../CONTEXT.md); architecture terms (Module, Interface, Depth, Seam, Adapter, Leverage, Locality) from the `improve-codebase-architecture` skill.

Each item below is a **deepening opportunity** — a shallow or smeared module that could be made deep. Handle one at a time; split an item into `issues/NN-<slug>.md` when you start it. This file is a tracking artefact and can be deleted once all items are resolved or consciously rejected.

Status legend: see [triage-labels.md](../../docs/agents/triage-labels.md). Default `Status: ready-for-agent` unless noted.

---

## Candidates

### 1. Unified order intake module
**Status:** grilled — ready for `/opsx:propose`
**Visualization:** [01-unified-intake-architecture.md](./01-unified-intake-architecture.md) — current vs target module maps + sequences (mermaid) + 8 resolved grilling decisions + migration plan.

**Files:** `mmx-application/.../ReceiveOrderService.java`, `mmx-application/.../RouteOrderService.java`, `mmx-adapter-in-rest/.../OrderIntakeController.java` (39–66).

**Problem:** Routing is smeared, not deep. `RouteOrderService` re-implements ~70% of `ReceiveOrderService` (idempotency, `intakeSourceContractNumber`, currency validation wiring, PM-org legal-entity checks — `RouteOrderService` even imports constants from `ReceiveOrderService`). The REST controller owns the TradingClient vs TradingHub branch and reaches into `LegalEntityRepository` to make it — a third place that knows the routing rule. `ReceiveOrderService.validateLifecycleInstitutionMatchesContract` (OnCall INCREASE/DECREASE/REDEMPTION institution-vs-contract checks) **never runs on the routing path** — a real gap, not just duplication.

**Solution (plain English):** One application entry that owns intake for both hub-native and routed orders — shared validation (including the lifecycle institution match), idempotency, audit; the TradingClient branch becomes an internal seam that adds grant resolution + proxy institution + global account + hub-side order creation behind the same interface.

**Benefits:** Locality — intake rules live in one place, so the missing institution-vs-contract check is fixed once and stays fixed. Leverage — one test surface covers both paths. Tests — the hub/routing divergence becomes an internal seam you can probe, instead of two near-parallel services to keep in lockstep.

**Deletion test:** folding the two together concentrates complexity; deleting one without merging just moves the duplicated validation.

**ADR check:** Compatible with ADR-0002 (two linked records created synchronously at intake) — it's about the entry shape, not the record model.

---

### 2. Routed-order pair outcome-propagation module
**Status:** implemented — ready for `/opsx:archive`

**Files:** `mmx-application/.../ExecuteOrderService.java` (`propagateHubOutcome`, `routingContextFor`), `mmx-application/.../OrderLifecycleService.java` (`propagateHubCancelOrReject`).

**Problem:** Hub→client outcome propagation is duplicated across two services with the same guard (`isHubSideRoutedLink`), the same client lookup (`findRoutedClientOrderByRoutingId`), and the same apply-outcome shape. Hub execute loads the client-side order **twice** (propagation + routing context). The cancel/reject propagation path has **zero unit tests anywhere**.

**Solution (plain English):** One module that owns "when a hub-side order reaches a terminal outcome, find the linked client-side order by routing id and apply the outcome (execute/reject/cancel), and produce the routing context for the outbox" — behind a small interface both `ExecuteOrderService` and `OrderLifecycleService` call.

**Benefits:** Locality — the routing-outcome rules (ADR-0002's "status must be synchronised") sit in one place. Leverage — one test surface finally covers hub→client cancel/reject. Tests — the untested cancel/reject path becomes testable through a single interface.

**Deletion test:** extracting it concentrates; deleting either private method moves logic to the other.

---

### 3. Explicit client↔hub link model (replace the nullable sentinel)
**Status:** ready-for-agent

**Files:** `mmx-domain/.../MoneyMarketOrder.java` (`isRoutedClientSide`/`isHubSideRoutedLink`, 298–304), `mmx-domain/.../RoutedOrderLink.java` (zero usages outside its own file), `mmx-adapter-out-persistence/.../SpringDataOrderRepository.java` (24–26), `JpaOrderRepository.java` (62–72).

**Problem:** "Which order is the client side vs the hub side, and how are they correlated?" is the hidden core of routing — and today it's a nullable-field sentinel (`routingId != null` + `originatingLegalEntityCode` null-vs-non-null) split across domain booleans, persistence null checks, and an unused `RoutedOrderLink` type. Understanding the concept requires bouncing across all three.

**Solution (plain English):** Make the link explicit — actually use `RoutedOrderLink`, or replace the sentinel with a real correlation model — so discrimination and lookup live in one place.

**Benefits:** Locality — the hardest routing concept becomes one module. Leverage — correlation queries and discrimination go through one interface. Tests — routing correlation finally has a test surface (today no `findByRoutingId*` persistence test exists).

**Deletion test:** deleting `RoutedOrderLink` (already orphan) concentrates by removing dead vocabulary; keeping the sentinel moves discrimination logic across layers.

**ADR check:** Compatible with / reinforces ADR-0002 (which *mandates* two linked records correlated by a routing id — today the link is implicit, not modeled).

---

### 4. ContractNumber resolution module
**Status:** ready-for-agent

**Files:** `mmx-domain/.../MoneyMarketOrder.java` (`validateSourceContractNumber` 483–487, `execute` 377–379), `mmx-application/.../ExecuteOrderService.java` (`resolveExecutionContractNumber` 139–148, `propagateHubOutcome` 104–107).

**Problem:** ContractNumber rules live in three layers — intake source validation, desk-execute guard, and application pre-check — and the routed-subscription path generates **two** contract numbers (hub at execute, client at propagation) with no test asserting that invariant.

**Solution (plain English):** One module that resolves ContractNumber per `(OrderOperation, side, lifecycle stage)` — intake source requirement, execute generation, and routed-pair propagation — with the subscription-vs-lifecycle distinction explicit.

**Benefits:** Locality — the per-operation contract-number rules concentrate. Tests — the "routed subscription = two numbers" invariant becomes assertable through one interface.

**Deletion test:** consolidating concentrates; deleting `resolveExecutionContractNumber` moves checks into domain only.

---

### 5. OnCall rate curve aggregate
**Status:** ready-for-agent

**Files:** `mmx-domain/.../OnCallRateSegment.java`, `mmx-domain/.../OnCallRateCurvePolicy.java`, `mmx-application/.../AddOnCallRateService.java`, `CancelOnCallRateService.java`, `ConfirmOnCallRateService.java`, `mmx-adapter-out-persistence/.../SpringDataOnCallRateSegmentRepository.java` (open-segment queries hard-code `VALID`+`PENDING_CONFIRMATION`+`endDate`), `frontend/.../group-oncall-rate-segments-for-review.ts` (duplicates `2999-12-31`).

**Problem:** There is no aggregate "OnCallRateCurve" — the invariants (at most one PENDING_CONFIRMATION per `(institution, currency, noticePeriod)`, no backdated value date, supersede prior open segment, open-segment = `2999-12-31`, status transitions) are split across domain policy, application orchestration, persistence query predicates, and a frontend constant. "Open segment" is encoded in JPA query strings, not in a domain concept.

**Solution (plain English):** A domain `OnCallRateCurve` keyed by `(institution, currency, noticePeriod)` that owns the open segment, the pending segment, and the supersede/cancel/confirm transitions; application and persistence become thin adapters around it.

**Benefits:** Locality — every curve invariant sits in one module. Leverage — `AddOnCallRate`/`Cancel`/`Confirm` shrink to adapters. Tests — curve invariants testable through the aggregate interface instead of via JPA query strings.

**Deletion test:** consolidating concentrates; keeping the split spreads the same invariants across four layers.

---

### 6. Delegated client entitlements module
**Status:** ready-for-agent

**Files:** `mmx-application/.../ManageDelegatedGrantsService.java` (`validated`), `mmx-application/.../RouteOrderService.java` (`resolveGrant` 170–181, via `DelegatedGrantDirectory`), `mmx-application/.../OrderCreationDelegatedCounterpartySupport.java` (re-filters grants in-memory instead of using `DelegatedGrantDirectory`), `TermOrderCreationOptionsService.java` / `OnCallOrderCreationOptionsService.java` (list tenors/notice periods from hub ManagedCurrency ∩ rate existence **without grant intersection**).

**Problem:** "What tenors / notice periods / institutions is this TradingClient allowed?" is not one module — it's smeared across grant administration, routing intake enforcement, and creation-options filtering. `DelegatedGrantDirectory` claims to be the single seam but creation-options bypass it and re-filter in Java. Real bug: a client can see a tenor/notice in the wizard that yields **empty counterparties** because grant filtering happens only at the counterparty step.

**Solution (plain English):** One module answering "for `(client LegalEntity, proxy?, currency)`, what tenors / notices / institutions are allowed?" — consumed by grant admin, routing intake, and creation-options alike.

**Benefits:** Locality — the entitlement rule lives once. Leverage — three callers share one seam. Tests — entitlement logic testable through one interface, and the wizard/empty-counterparty inconsistency disappears.

**Deletion test:** `OrderCreationDelegatedCounterpartySupport` deletion today would force real duplication into both creation-options services.

---

### 7. Hub-scoped reference-data reads (tenancy leak fix)
**Status:** ready-for-agent

**Files:** `mmx-application/.../HubScopeResolver.java`, `mmx-adapter-in-rest/.../CurrencySettingsController.java` (49–53, hub-scoped ✓), `TermOrderCreationOptionsService.java` (70 `findAll()`), `OnCallOrderCreationOptionsService.java` (75), `SampleTermRateCsvGenerator.java` (43), `ManageDelegatedGrantsService.java` (`listGrants` 39).

**Problem:** `HubScopeResolver` exists for role-aware hub reads, but several paths call unscoped `findAll()`. Within one MMX deployment (per ADR-0001, all LegalEntities of an Organisation share one deployment) **multiple TradingHubs can exist**, so unscoped reads return other hubs' reference data — a real cross-hub leak, not just style drift.

**Solution (plain English):** One hub-scoped read seam so every reference-data read resolves the hub via `HubScopeResolver` before querying; eliminate bare `findAll()` on hub-owned catalogs.

**Benefits:** Locality — hub scoping lives in one place. Leverage — new settings services get correct tenancy for free. Tests — the leak becomes assertable through one interface.

**Deletion test:** `HubScopeResolver` deletion would duplicate scoping ternary logic in every settings controller — it is deep; callers ignoring it is the friction.

**ADR check:** Reinforces ADR-0001 (per-organisation deployment with shared LegalEntities) — scoping is the in-process tenancy boundary.

---

### 8. `OrderRepository` decomposition
**Status:** ready-for-agent — but flagged: per LANGUAGE.md "one adapter = hypothetical seam." Only pursue if you expect a second adapter (e.g. a separate contract read-model store).

**Files:** `mmx-application/.../port/out/OrderRepository.java` (19–59), `mmx-adapter-out-persistence/.../JpaOrderRepository.java`.

**Problem:** One port combines CRUD, desk list queries, routing correlation (`findRoutedClientOrderByRoutingId`, `findHubOrderByRoutingId`), and contract catalog (`findExecutedSubscriptionByContractNumber`, `findExecutedSubscriptionsByPortfolioAndOrderType`, `findContractNumbersWithNonCancelledRedemption`). Six use cases with very different reasons reach through one port into JPA specifics; contract-DTO assembly with `IllegalStateException` guards lives in the adapter.

**Solution (plain English):** Split the seam into order persistence vs contract read-model vs routing lookup.

**Benefits:** Locality — each read-model's query logic and evolution concentrate. Tests — each seam has its own test surface (today no `findByRoutingId*` tests exist).

**Deletion test:** splitting concentrates each seam; deleting methods moves SQL into use cases.

---

### 9. `OrderStatus` / `OrderLifecycleKind` cohesion in `MoneyMarketOrder`
**Status:** ready-for-agent

**Files:** `mmx-domain/.../OrderStatus.java` (`transitionTo` defaults to `DESK` 36–37; `ROUTED_CLIENT` kind 27–34), `mmx-domain/.../MoneyMarketOrder.java` (`cancel` 423–425, `reject` 438–439 always use DESK).

**Problem:** Two lifecycle graphs (desk `Received→Assigned→Executed→Accounted` vs routed-client `Received→Routed→Executed→Accounted`) are split across `OrderLifecycleKind`, `OrderStatus` maps, and per-method domain calls — and `MoneyMarketOrder.cancel`/`reject` always use the DESK graph, so a ROUTED client-side order **cannot be cancelled/rejected via desk API**; only hub propagation works. No test documents "client ROUTED cancel must go through hub."

**Solution (plain English):** Make the lifecycle graph selection cohesive inside `MoneyMarketOrder` so a routed-client order's terminal transitions are impossible to invoke on the wrong graph.

**Benefits:** Locality — the dual-graph rules concentrate. Tests — the "routed-client cancel goes through hub" invariant becomes assertable.

**Deletion test:** merging concentrates lifecycle rules; this is a locality problem more than a shallow-module problem.

---

### 10. GlobalAccount settings surface (contract gap + dual seam)
**Status:** ready-for-agent — also an SDD governance gap (no `contracts/` entry).

**Files:** `mmx-adapter-in-rest/.../GlobalAccountsController.java` (ad-hoc, no generated `*Api`), `mmx-application/.../ManageGlobalAccountsService.java`, `mmx-domain/.../GlobalAccountDirectory.java` (port used by `RouteOrderService` 117–120), `frontend/.../global-accounts-api.service.ts` (hand-written types, unlike other generated settings APIs).

**Problem:** `GlobalAccountsController` has **no `contracts/` entry** (codebase-map flags it as POC surface). The same `global_account` table has two read/write seams — `ManageGlobalAccountsUseCase` (settings CRUD) vs `GlobalAccountDirectory` (routing resolve) — and the frontend hand-writes types instead of regenerating them. Per AGENTS.md/SDD, material changes here need a new contract.

**Solution (plain English):** Add an OpenAPI contract for global accounts; align the settings surface with `GlobalAccountDirectory` so upsert and resolve share a seam; regenerate frontend types.

**Benefits:** Locality — the global-account concept has one contract and one seam. Leverage — frontend types come from codegen like every other settings area. Tests — contract-driven controller tests instead of ad-hoc records.

---

## Smaller items (not elevated to candidates)

Low impact or pure relays that pass the deletion test as "just move." Fold into a chosen candidate during grilling, or handle as one-line cleanups.

- Shallow list services: `ListTermRatesForDayService`, `ListTermRateTradingDaysService`, `ListOnCallRateSegmentsService` — pure relays.
- `CurrencySettingsController` bypasses application layer for list reads (injects `ManagedCurrencyRepository` directly).
- Term vs OnCall creation-options twin duplication (parallel `listCurrencies` / `listOperations` / `listTenors` / `listNoticePeriods` / `toCounterparty`).
- Frontend `CLIENT_CODES = ['PAR','SIN']` hardcoded in `delegated-grants-form.component.ts` (should be a hub-connected TradingClient query).
- Frontend `global-accounts-form.component.ts` uses free-text client code (no client directory).
- Proxy display-name staleness: `ThinProxyInstitution.deriveDisplayName` is persisted; if the hub institution display name changes, proxy names go stale.
- Persistence mapper `LOC` / `LEGACY` fallbacks in `OrderPersistenceMapper.java` (123–139) hide bad data.
- Bootstrap transactional wrappers `TransactionalRouteOrderUseCase` / `TransactionalExecuteOrderUseCase` are pass-throughs; `ReceiveOrderUseCase` has no equivalent wrapper (inconsistent).
- Policy instantiation inconsistency: `OrderAgainstCurrencyPolicy` constructed with `new` in three services; `OrderAgainstInstitutionPolicy` is a Spring bean.
- `DeskOrderQueryService` loads all then paginates in memory; page validation duplicated with `JpaOrderRepository`. Won't scale.
- Term rate CSV column list duplicated (parser `REQUIRED_COLUMNS`, generator, frontend template); tenor parsing duplicated between `TermRateIngestPolicy` and `DelegatedGrantSubsetPolicy`.
- `MarkOrderAccountedService` has no routed-client propagation (only hub execute propagation exists today) — may be intentional but is undocumented in code.
- Institution acronym allocation duplicated between `ManageInstitutionSettingsService.onboard` and `OnboardInstitutionService.onboardProxy`.
- Two authorization styles: `ReferenceDataMutationGuard.ensureTrader` in controllers vs `requireTrader`/`requireClientRepresentative` in `ManageDelegatedGrantsService`.
- `MoneyMarketOrder.getInstitutionCode`/`getCounterparty` blur intake vs execution fields; round-tripping via `OrderPersistenceMapper.toEntity` overwrites intake with execution values post-execute (intentional but non-obvious).

## Recommended order (friction-to-effort)

1. #1 (intake) — real missing-validation bug + heavy duplication.
2. #2 (propagation) — untested cancel/reject path.
3. #7 (hub-scoped reads) — real cross-hub leak.
4. #6 (entitlements) — real wizard bug.
5. #3 (link model) — hardest concept, currently implicit.
