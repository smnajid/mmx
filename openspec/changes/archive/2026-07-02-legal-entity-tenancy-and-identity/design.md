## Context

MMX is a hexagonal Java/Spring Boot 4 monolith (domain → application → adapters) with contract-first OpenAPI REST and an Angular 21 trader SPA. Today it is single-tenant: one user role `Trader` carried on `X-Trader-Id`, global reference data, and orders owned by the single deployment. `CONTEXT.md` now defines a tenancy model — **Organisation** → **LegalEntity** (TradingHub or TradingClient) — and an umbrella **MMXUser** identity with `(LegalEntity, role)` scopes, agreed in the grilling session and recorded in ADR-0001 (one MMX deployment per Organisation) and ADR-0004 (umbrella identity + entity-scoped session). This change implements that foundation. Order routing (Change B) and delegated institution grants (Change C) build on it.

Stakeholders: the bank group's PM (one system per Organisation, serving multiple LegalEntities), Traders (on TradingHubs), and ClientRepresentatives (on TradingClients, Settings-only). The legacy FiduTrader system being replaced assumed a single entity; the Deposits back-office application will integrate via MMX events/callbacks (Change B).

## Goals / Non-Goals

**Goals:**

- Model Organisation, LegalEntity, codes, and the TradingHub/TradingClient role + hub–client connection as domain reference data.
- Introduce `MMXUser` with N `(LegalEntity, role)` scopes; bind a session to one active scope; switchable by re-scope; no free per-request entity choice.
- Replace `X-Trader-Id` with `X-User-Id` (+ role) on trader endpoints; add required `legalEntityCode` to PM intake.
- Partition order data and desk queries by the active LegalEntity scope.
- Gate the Desk UI to TradingHubs; restrict TradingClient users to Settings.
- Keep reference-data capabilities (institutions, currencies, rates) behaviourally unchanged in this change — only the LegalEntity partition dimension is not yet applied to them.

**Non-Goals:**

- Order routing, the `Routed` status, two-linked-records, GlobalAccountDirectory (Change B).
- Delegated institution grants, proxy institutions, "via {hub}" naming, per-currency tenor grants, ClientRepresentative institution settings UI (Change C).
- Reference-data hub-owns/client-reads rules and LegalEntity scoping of institutions/currencies/rates (Change C).
- Real authentication/SSO wiring — V1 uses the existing POC posture (headers carry identity); production auth is a separate concern.
- Cross-organisation routing or multi-organisation federation (out of scope for V1).

## Decisions

### D1: LegalEntity as a domain partition key, not a deployment

One MMX deployment serves all LegalEntities of an Organisation (ADR-0001). `LegalEntityCode` is a column on every tenant-scoped aggregate (orders first; reference data in Change C) and the active session scope selects it. A deployment is configured with its `OrganisationCode`; LegalEntities are rows in `organisation` / `legal_entity`.

**Rationale:** Organisation-level fault isolation without N deployments; routing stays in-process. Alternatives: one deployment per LegalEntity (rejected — N× cost, cross-instance messaging for routing); one global multi-tenant instance (rejected — no org blast-radius isolation).

### D2: Umbrella identity — `X-User-Id` + role + active scope

A request carries `X-User-Id` and the role is established on the session (resolved from the user's allowed scopes at login/re-scope). Authorisation is the single mechanism `(user, role, active LegalEntity)`. The desk contract's `TraderIdHeader` (`X-Trader-Id`) becomes `UserIdHeader` (`X-User-Id`); the intake contract is unaffected by the session model (PM is a system caller — see D3).

**Rationale:** Future roles are a new enum value, not a new header/auth path (ADR-0004). Alternatives: parallel `X-Trader-Id` + `X-Client-Rep-Id` headers (rejected — contract churn per role, two drifting auth paths).

**Adapter placement:** a request filter/resolver in `mmx-adapter-in-rest` (or bootstrap wiring) resolves the active `(LegalEntity, role)` scope from the session and makes it available to application use cases via a `ScopeContext` port — adapters depend on `application.port.in`, not on services directly (Tier 2 rule). Scope resolution logic itself lives in `mmx-application`; the filter only translates HTTP → scope.

### D3: Intake entity via body field, trader entity via session (intentional asymmetry)

PM is one system per Organisation serving multiple LegalEntities, so `POST /api/v1/orders` (`contracts/001-mm-order-processing/openapi.yaml`, operation `receiveOrder`) gains a required `legalEntityCode` body field; PM is authorised at the organisation level and the code must identify a LegalEntity of that organisation. Trader/settings endpoints derive the LegalEntity from the session scope (no body field).

**Rationale:** The order inherently belongs to a LegalEntity; for a system caller an explicit required field is honest and self-documenting, with safety from the authorisation check. The Q7 "no free per-request entity choice" invariant applies to human sessions; PM's body field is the system-caller equivalent, gated by authz.

### D4: Order LegalEntity scoping via column + query filter

`money_market_order` gains a non-null `legal_entity_code` column (Flyway migration back-fills existing rows to a configured default LegalEntity for the organisation). Desk query use cases (`DeskOrderQueries` and friends) filter by the active scope's `LegalEntityCode`; a query scoped to `PAR` cannot see `LOC` orders. The order aggregate in `mmx-domain` carries `LegalEntityCode` as part of its identity.

**Rationale:** Keeps the per-LegalEntity scope rule (a session sees only its own entity's data) enforceable at the query layer. Alternatives: row-level security in Postgres (premature for POC); separate schemas per entity (rejected — contradicts one-deployment-per-org).

### D5: Role-gated navigation in the Angular shell

The Angular shell (`frontend/src/app/`, `app.routes.ts`, `trader-desk-navigation` capability) resolves the active role and hides Desk routes for `ClientRepresentative`; TradingClient users land on Settings. An active-scope switcher lets a multi-scope user re-scope (calls a re-scope endpoint that re-validates the `(LegalEntity, role)` pair and rebinds the session).

**Rationale:** The Desk is meaningless without a trading desk (TradingClients have none). The switcher realises the "connects to an entity" model. API clients send `X-User-Id` + role.

### D6: Contract-first ordering for the breaking header/intake change

Update `contracts/002-trader-orders-views/openapi.yaml` (`TraderIdHeader` → `UserIdHeader`, `X-User-Id`) and `contracts/001-mm-order-processing/openapi.yaml` (`ReceiveOrderRequest` + required `legalEntityCode`), plus their `api-v1.md` mirrors, **before** changing controllers. Regenerate server stubs in `mmx-adapter-in-rest`, then adapt controllers/mappers. Frontend API clients (`core/api/*-api.service.ts`) are updated to the new contract.

**Rationale:** Governance Principle I (contract-first) and Tier 2 (REST adapters depend on generated `*Api` interfaces). Reversing the order breaks codegen parity.

## Risks / Trade-offs

- **[Risk] Breaking `X-Trader-Id` for existing consumers** → Any existing integration/tests using `X-Trader-Id` must move to `X-User-Id`. Mitigation: update all REST integration tests and the frontend API clients in the same delivery; the change is intentionally breaking (major redesign).
- **[Risk] Back-filling `legal_entity_code` on existing orders** → Existing rows need a valid LegalEntity. Mitigation: Flyway migration assigns a configured default LegalEntity for the organisation; documented as a one-shot migration. Rollback: the migration is additive (column + default); dropping the column rolls back.
- **[Trade-off] Reference data stays global in this change** → Institutions/currencies/rates are not yet LegalEntity-scoped, so a single-hub deployment works but a multi-hub Organisation would share reference data until Change C. Acceptable: V1 deployments start with the tenancy model; reference-data scoping arrives with grants (Change C) before multi-hub reference data is needed.
- **[Trade-off] POC auth posture** → Identity is carried on headers, not a real auth provider. Acceptable for the POC; production auth is a separate, flagged concern.

## Migration Plan

1. Add Flyway migrations: `organisation`, `legal_entity` (+ role/hub-connection), `mmx_user`, `mmx_user_scope`; add `legal_entity_code` to `money_market_order` with a default back-fill.
2. Update contracts (002 header, 001 intake field) + mirrors; regenerate.
3. Implement domain/application scoping + intake validation; adapt REST controllers and the scope resolver/filter.
4. Frontend: role-gated shell + scope switcher + `X-User-Id` clients.
5. Rollback: migrations are additive; revert code + drop additive columns/tables (order data back-fill is non-destructive).

## Open Questions

- **Re-scope endpoint shape** — is re-scope a `POST /api/v1/session/scope` that returns the rebound session, or implicit on next request with a header? (Lean: an explicit re-scope endpoint that re-validates and rebinds, returning the new active scope.)
- **Default LegalEntity for back-fill** — confirm the configured default LegalEntity code per Organisation for migrating existing orders (operationally: one code per existing deployment).
- **Role source on the wire** — is the role sent as a header (`X-User-Role`) on every request, or only resolved at login/re-scope and carried in the session? (Lean: session-carried; a header is redundant and forgeable without auth.)
