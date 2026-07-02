## Context

Change A introduced LegalEntity tenancy and the `ClientRepresentative` role (Settings-only) on a TradingClient; Change B introduced order routing and consumes a `DelegatedGrantDirectory` at TradingClient intake to validate the institution/tenor against a grant. This change supplies that grant model and the reference-data ownership rules. The existing institution catalog (`institution-onboarding`), managed currencies (`managed-currency-settings`), term rates (`term-rate-daily-upload`), and OnCall curves (`oncall-rate-curve-management`) are flat, trader-maintained, single-scope catalogs; they have no per-client enablement, no hub/client ownership distinction, and no "via {hub}" counterparty identity. All reference data lives inside the one-deployment-per-Organisation boundary (ADR-0001), so a TradingClient reading its hub's reference data is an in-process, same-database read keyed by the hub's `LegalEntityCode` — no cross-instance integration.

Stakeholders: TradingHub users (Traders) who manage institutions and grants and own reference data; TradingClient users (ClientRepresentatives) who onboard granted proxies and enable granted tenors/notice periods; Portfolio Management (intake validated against grants); the Deposits back office (unchanged by this change — it consumes routed events from Change B).

## Goals / Non-Goals

**Goals:**

- Model a delegated institution grant keyed by `(hub institution, client LegalEntity, currency)` with an enabled subset of tenors (Term) and notice periods (OnCall), independent of the hub's own intake enablement, prospective-only.
- Let a TradingClient onboard a thin-proxy institution for a grant it holds, with the derived display name "{hub institution displayName} via {hub LegalEntityCode}".
- Bind TradingClient intake to grants: institution must be an active granted proxy; tenor/notice must be in the grant's enabled set for the currency.
- Make currencies, term rates, and OnCall curves hub-owned and client-read-only (in-process read of the connected hub's reference data).
- Expose grant CRUD to TradingHub users via a contract-first OpenAPI surface; render the ClientRepresentative settings surface with institution-proxy limited activity and read-only currency/term/OnCall screens.
- Provide the `DelegatedGrantDirectory` out-port that Change B's `RouteOrderUseCase` consumes.

**Non-Goals:**

- Cross-organisation grants (out of scope; same-Org only, ADR-0001).
- Deposits product / onboarding the client's own LegalEntity as a counterparty (deferred).
- Back-office contract creation/reversal/replace (back-office-internal, Change B).
- Order routing mechanics (Change B) — this change only supplies the grant lookup the router calls.
- A TradingClient managing its own currencies/term-rates/OnCall-curves (explicitly forbidden — hub-owned).
- Rate-curve segregation beyond institution: the proxy reads the hub's curves for the granted institution as-is.

## Decisions

### D1: Grant keyed by (hub institution, client LE, currency) with an enabled tenor/notice subset; independent of hub intake enablement; prospective

A `DelegatedInstitutionGrant` is keyed by `(hubInstitutionCode, clientLegalEntityCode, currency)` and carries `enabledTenors` (subset of the hub's managed-currency `enabledTenors` for Term) and `enabledNoticePeriods` (subset for OnCall). A grant MAY enable a tenor for a client that the hub keeps off for its own desk, and the hub MAY keep a tenor on for its own desk while withholding it from a client — the grant is independent of the hub's own intake enablement. Grant create/update/delete are **prospective**: they do not rewrite already-routed or executed orders; historical orders keep the grant in effect when they were routed.

**Rationale:** Per-currency granularity matches the order (which carries currency) and the global-account lookup (per currency, Change B). Independence from hub intake enablement matches the agreed rule ("the tradingHub user could enable it for a tenor for a client but not for the hub"). Prospective-only keeps history immutable.

**Alternatives considered:** (a) grant keyed by institution only (no currency) — rejected, rates/accounts/tenors are currency-segregated and a hub may grant BNP-EUR to a client but not BNP-USD. (b) grant that mirrors the hub's own enabled set — rejected, contradicts the independence requirement. (c) retroactive grant rewrite of history — rejected, immutable history.

**Adapter placement:** `DelegatedInstitutionGrant` aggregate in `mmx-domain`; grant CRUD use cases in `mmx-application`; `delegated_institution_grant` table + repository in `mmx-adapter-out-persistence`.

### D2: Thin-proxy institution referencing the hub native institution; derived "via {hub}" name; no own rates

A TradingClient does not onboard a free-form institution. It onboards a **thin proxy** that references the hub's native `institutionCode` and whose `displayName` is derived as `"{hubInstitution.displayName} via {hubLegalEntityCode}"` (e.g. `BNP via LOC`). The proxy has its own `institutionCode` (generated per the existing acronym rules) for client-scope identity, but carries **no independent rate curves** — term and OnCall rates for the proxy are the hub's rates for the referenced native institution, read in-process.

**Rationale:** The client's counterparty identity must be the "via" name the PM and back office see; routing (Change B) maps the proxy to the hub's native institution at the hub side. Giving the proxy its own `institutionCode` keeps client-scope intake idempotency and lists working without leaking hub-native codes into the client's catalog. No own rates avoids duplicating curves and keeps one source of truth (the hub).

**Alternatives considered:** (a) reuse the hub's `institutionCode` directly in the client catalog — rejected, violates per-LegalEntity scope and breaks the client's acronym/idempotency model. (b) copy the hub's rate curves into the client — rejected, duplication/drift. (c) free-form client display name — rejected, must be deterministic for routing and back-office correlation.

**Adapter placement:** proxy-institution reference columns (`hub_legal_entity_code`, `hub_institution_code`) on the institution row in `mmx-adapter-out-persistence`; derived-name policy in `mmx-domain`; client onboard use case in `mmx-application` enforces "grant must exist" before proxy creation.

### D3: Reference data is hub-owned, client-read (in-process, same deployment)

Managed currencies, term rates, and OnCall curves are **owned by the TradingHub** LegalEntity. A TradingClient's read use cases target the connected hub's `LegalEntityCode` and return the hub's reference data read-only. There is no replication and no cross-instance call (ADR-0001: same deployment, same database). The client never holds its own rows for these catalogs.

**Rationale:** One source of truth per Organisation's hub; avoids drift; matches the agreed rule ("currencies are owned by the hub only, a tradingClient asks the hub to get them"). In-process reads keep it simple and transactional with intake validation.

**Alternatives considered:** (a) replicate reference data per client — rejected, drift and complexity. (b) an external reference-data service — rejected for V1 (over-engineering; the hub is in the same deployment).

**Adapter placement:** reference-data repositories in `mmx-adapter-out-persistence` accept a `LegalEntityCode` scope; client read use cases in `mmx-application` resolve the connected hub's code and query with it. The grant directory lookup at intake (Change B) resolves the hub from the client's connection (Change A) and the grant by `(hubInstitution, client, currency)`.

### D4: DelegatedGrantDirectory out-port (consumed by Change B)

A `port/out` `DelegatedGrantDirectory` answers "does a grant exist for `(clientLegalEntityCode, proxyInstitutionCode, currency)` and is the tenor/notice in its enabled set?". V1 is backed by the `delegated_institution_grant` table. This is the single seam Change B's `RouteOrderUseCase` calls at TradingClient intake.

**Rationale:** Keeps the routing use case free of a hard dependency on grant persistence; mirrors the `GlobalAccountDirectory` port pattern from Change B. Allows a future external grant source without changing routing.

**Adapter placement:** port in `mmx-application`; adapter in `mmx-adapter-out-persistence`; wired in `mmx-bootstrap`.

### D5: Role-scoped authorisation on settings endpoints (write blocked for ClientRepresentative, except proxy onboard + granted toggles)

Mutation of currencies, term rates, and OnCall curves is **Trader-only** (TradingHub). A `ClientRepresentative` (TradingClient) receives 403 on those mutation endpoints and read-only rendering in the UI. Institution onboard is **role-qualified**: a ClientRepresentative may create a proxy only for a grant they hold and may toggle only tenors/notice periods within the grant's enabled set; a Trader onboards native institutions as today. Grant CRUD is Trader-only (TradingHub). Authorisation is enforced in the application use cases (defence in depth) and at the REST layer (role + active-scope LegalEntity match per Change A).

**Rationale:** One authorisation mechanism `(user, role, LegalEntity)` from Change A; the role dimension carries the read/write distinction. Enforcing in the use case (not only REST) keeps the invariant for any caller.

**Alternatives considered:** separate per-role endpoint sets — rejected (contradicts ADR-0004's umbrella identity). Client-side-only gating — rejected (insecure).

### D6: Contract-first — new grant CRUD OpenAPI; existing settings contracts gain role-scoped semantics

New `contracts/006-delegated-institution-grants/openapi.yaml` (+ `api-v1.md`) for grant CRUD (TradingHub). Existing settings contracts (`003`, `004`, `005`) gain documented role-scoped semantics (read-only for `ClientRepresentative`; institution onboard role-qualified) in their `api-v1.md` mirrors — **no new client mutation endpoints** and **no breaking schema changes**; the 403 responses are standard authorisation errors. Codegen regenerates; runtime matches the published contracts in the same delivery.

**Rationale:** Governance Principle I (contract-first). Adding role-scoping is a behavioural/authorisation change documented in the mirrors, not a schema break.

## Risks / Trade-offs

- **[Risk] Grant/proxy consistency at intake** — Change B's router calls the grant directory; an inconsistent proxy (references a hub institution the grant no longer covers) could let a bad order route. Mitigation: client proxy onboard requires a grant to exist; grant deletion deactivates (does not hard-delete) and intake checks grant active; integration test asserts intake rejects when the grant is inactive.
- **[Risk] Read-through to hub reference data couples client availability to hub data** — a client read fails if the hub's rows are absent. Mitigation: hub reference data is seeded as part of hub onboarding; client read returns empty/404 gracefully (same as a cold-start hub today).
- **[Risk] Dependency ordering with Change B** — Change B calls `DelegatedGrantDirectory`; this change must land the port + adapter before Change B's routing is production-ready. Mitigation: the port is defined here; Change B can code against the port and stub the adapter until this change lands. Both must be present before TradingClient intake is enabled.
- **[Trade-off] Independence from hub intake enablement doubles the enablement surface** — a tenor can be on for the hub, off for the client, etc. Acceptable: matches the agreed rule; the grant is the explicit per-client truth.
- **[Trade-off] Proxy has its own institutionCode** — adds a client-scope code alongside the hub-native code. Acceptable: preserves per-LegalEntity scope and client idempotency; routing maps proxy→native (Change B).

## Migration Plan

1. Flyway: `delegated_institution_grant` table (`hub_institution_code`, `client_legal_entity_code`, `currency`, `enabled_tenors`, `enabled_notice_periods`, `active`); proxy columns on `institution` (`hub_legal_entity_code`, `hub_institution_code`, nullable); reference-data rows gain/keep `legal_entity_code` scope (hub).
2. Contracts: add `contracts/006-.../openapi.yaml` (+ `api-v1.md`); document role-scoped semantics in `003`/`004`/`005` mirrors; regenerate codegen.
3. Implement domain/application (grant aggregate, proxy, directory port, read-only enforcement, role-scoped authorisation) + persistence adapters.
4. REST: grant CRUD endpoints (Trader); role-scoped authorisation on existing settings endpoints.
5. Frontend: hub grant management screen; client Institutions (proxy onboard + granted toggles); read-only currency/term/OnCall screens for `ClientRepresentative`.
6. Rollback: migrations are additive (new table, nullable columns); revert code; existing single-scope flows untouched for TradingHub users.

## Open Questions

- **Grant currency vs. institution onboarding currency** — does a client proxy institution get one row per granted currency (e.g. `BNP via LOC` appears once, with EUR and USD grants as separate grant rows), or one proxy row per (institution, currency)? (Lean: one proxy institution row per `(hubInstitution, client)` with a derived name independent of currency; grants are per-currency rows. Confirm.)
- **Granularity of `enabledTenors`/`enabledNoticePeriods` storage** — stored as a list on the grant row, or normalised into a child table? (Lean: list/array column on the grant row for V1 simplicity.)
- **Deactivating a grant with open client orders** — should deactivation be allowed while `RECEIVED`/`ROUTED` client orders reference it (prospective-only, like currency deactivation), and does it block new intake only? (Lean: yes — deactivation blocks new intake, leaves in-flight orders untouched, mirroring `managed-currency-settings` deactivation semantics.)
- **Client UI for currency/term/OnCall read-only** — render the existing screens in a disabled state, or a dedicated read-only viewer? (Lean: disabled-state reuse of existing screens, gated by role.)
