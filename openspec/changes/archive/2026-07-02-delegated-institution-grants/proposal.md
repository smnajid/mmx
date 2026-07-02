## Why

A TradingClient has no desk and can only trade institutions its TradingHub has delegated to it. Today there is no grant model: institutions are a flat trader-maintained catalog with no per-client enablement, no "via {hub}" counterparty identity, and no rule that a client may only act within what its hub granted. This change introduces **delegated institution grants** — the mechanism that lets a TradingHub expose an institution to a TradingClient per currency with a permitted subset of tenors/notice periods — and makes the TradingClient the **reader** (not the owner) of its hub's currency, term-rate, and OnCall-curve reference data. It is the validation that Change B's order routing depends on, and it completes the reference-data LegalEntity scoping that Change A deferred.

## What Changes

- **New `delegated-institution-grants` capability**: a TradingHub grants one of its native institutions to a connected TradingClient for a given currency, with an **enabled subset** of tenors (Term) and notice periods (OnCall) drawn from the hub's managed-currency workspace. A grant is keyed by `(hub institution, client LegalEntity, currency)` and is **independent of the hub's own intake enablement** — a hub may enable a tenor for a client while keeping it off for its own desk, and vice versa. Grants are **prospective**: changes do not rewrite already-routed or executed orders.
- **Thin-proxy institution with derived name**: the TradingClient onboards a **proxy** institution that references the hub's native institution; its display name is derived as **"{hub institution displayName} via {hub LegalEntityCode}"** (e.g. `BNP via LOC`). The proxy carries no independent rate curves; it reads the hub's rates for the granted institution.
- **Client intake is grant-bound**: at a TradingClient, intake's `institutionCode` MUST reference one of the client's active granted proxies, and the order's tenor/notice MUST be within the grant's enabled set for that currency. At a TradingHub, intake institution rules are unchanged.
- **Reference data is hub-owned, client-read**:
  - **Managed currencies** are owned and managed by the TradingHub; a TradingClient reads its hub's currency catalog (read-only) and cannot onboard/update/disable currencies. Client intake currency validation uses the hub's managed-currency set.
  - **Term rates** are owned and managed by the TradingHub; a TradingClient reads its hub's term rates for granted institutions (read-only) and cannot upload.
  - **OnCall rate curves** are owned and managed by the TradingHub; a TradingClient reads its hub's curves for granted institutions (read-only) and cannot add or cancel rates.
- **ClientRepresentative settings surface**: a TradingClient user (role `ClientRepresentative`, per Change A) sees Settings only; within Institutions they may onboard only granted proxies and enable only the granted tenor/notice subset; Currencies, Term rates, and OnCall rates render **read-only**.
- **BREAKING (intake)**: a TradingClient intake that names a non-granted institution, or a tenor/notice outside the grant's enabled set for the currency, is now rejected. (TradingHub intake is unaffected.)
- **Contract-first**: the grant CRUD is a new product REST surface under a canonical OpenAPI document (+ mirror); existing settings OpenAPI contracts gain role-scoped read/write semantics (no new mutation endpoints for clients).

## Capabilities

### New Capabilities

- `delegated-institution-grants`: TradingHub→TradingClient institution delegation per currency with enabled tenor/notice subsets; thin-proxy institution model and "via {hub}" naming; grant prospective-only semantics; reference-data ownership rule (hub owns currencies/term-rates/OnCall-curves, client reads).

### Modified Capabilities

- `institution-onboarding`: a TradingClient onboards only **thin-proxy** institutions for grants it holds, with the derived "via {hub}" display name; a TradingHub onboards native institutions as today; the client's institution list is limited to its proxies.
- `order-institution-constraints`: intake at a TradingClient requires the `institutionCode` to be an active granted proxy and the tenor/notice to be within the grant's enabled set for the currency; hub intake unchanged.
- `managed-currency-settings`: currencies are owned/managed by the TradingHub; a TradingClient reads its hub's currency catalog read-only and cannot mutate it; client intake currency validation uses the hub's set.
- `term-rate-daily-upload`: term rates are owned/managed by the TradingHub; a TradingClient reads its hub's term rates for granted institutions read-only and cannot upload.
- `oncall-rate-curve-management`: OnCall curves are owned/managed by the TradingHub; a TradingClient reads its hub's curves for granted institutions read-only and cannot add/cancel rates.

## Impact

- **Stack assumptions:** Spring Boot 4 backend, Angular SPA, hexagonal module boundaries (`mmx-domain` / `mmx-application` / `mmx-adapter-in-rest` / `mmx-adapter-out-persistence` / `mmx-bootstrap`); contract-first OpenAPI for product REST; TDD (red-first JUnit 5 / Angular Vitest) for behavioural work.
- **Backend — hexagonal:**
  - `mmx-domain`: `DelegatedInstitutionGrant` aggregate (key `(hubInstitutionCode, clientLegalEntityCode, currency)`, enabled tenors/notice periods), `ThinProxyInstitution` concept referencing the hub institution + derived name policy; reference-data ownership rules. No Spring/framework.
  - `mmx-application`: grant CRUD use cases; `DelegatedGrantDirectory` out-port (consumed by Change B's `RouteOrderUseCase` for client intake validation); client-side institution onboard use case restricted to grants; read-only enforcement for client role on currency/term/oncall settings use cases.
  - `mmx-adapter-in-rest`: new grant CRUD endpoints (hub users) under a canonical OpenAPI contract; role/LegalEntity-scoped authorisation on existing settings endpoints (write blocked for `ClientRepresentative`).
  - `mmx-adapter-out-persistence`: JPA + Flyway for `delegated_institution_grant` and proxy-institution reference columns; repositories for the grant directory and proxy institutions.
  - `mmx-bootstrap`: wiring for grant use cases, the `DelegatedGrantDirectory` adapter, and role-scoped settings authorisation.
- **Contracts:** new `contracts/006-delegated-institution-grants/openapi.yaml` (+ `api-v1.md`) for grant CRUD; existing settings OpenAPI contracts (`003`, `004`, `005`) gain role-scoped semantics (read-only for `ClientRepresentative`) — documented in their `api-v1.md` mirrors; no new client mutation endpoints.
- **Frontend (Angular):** TradingHub grant management screen; TradingClient Institutions screen limited to granted proxies + granted tenor/notice toggles; Currencies / Term rates / OnCall rates screens render read-only for `ClientRepresentative` (role-gated per Change A).
- **Dependencies:** depends on Change A (`legal-entity-tenancy-and-identity` — LegalEntity role, `ClientRepresentative`, scope) and cooperates with Change B (`order-routing-client-to-hub`), which calls the `DelegatedGrantDirectory` at TradingClient intake. ADRs: none new (grants are a capability, not a hard-to-reverse architectural decision); cross-references ADR-0001 (one deployment per Organisation → grants are intra-Org reference data).
