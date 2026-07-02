## Why

MMX today is a single-tenant monolith with one user role (`Trader`, identified by `X-Trader-Id`) and global reference data. The bank group needs to model itself as one **Organisation** (e.g. Lombard Odier) containing several **LegalEntities** (e.g. Geneva `LOC`, Paris `PAR`, Singapore `SIN`), where each LegalEntity is either a **TradingHub** (operates a desk) or a **TradingClient** (no desk; orders routed to a hub). This change introduces the tenancy and identity foundation that order routing (Change B) and delegated institution grants (Change C) will build on. It is being done now because the legacy FiduTrader replacement must support multi-entity operation and client-rep self-service before routing and delegation can be layered on.

## What Changes

- **New tenancy model** — `Organisation` (4-char `OrganisationCode`), `LegalEntity` (globally-unique 3-char `LegalEntityCode`, belongs to one Organisation), and a `TradingHub` / `TradingClient` **role** on each LegalEntity (mutually exclusive in V1). A TradingClient is connected to exactly one TradingHub in the same Organisation (V1). One MMX deployment serves all LegalEntities of an Organisation (ADR-0001).
- **BREAKING — Umbrella user identity** — replace `X-Trader-Id` with **`X-User-Id`** + an explicit **role**. An `MMXUser` holds N allowed `(LegalEntity, role)` scopes; a session is bound to one **active scope**, switchable by re-scope; the server never accepts a free per-request entity choice (ADR-0004). V1 roles: `Trader` (desk; TradingHubs only) and `ClientRepresentative` (Settings-only; TradingClients).
- **BREAKING — Intake carries the LegalEntity** — `POST /api/v1/orders` gains a required **`legalEntityCode`** body field (PM is scoped per Organisation and serves multiple entities); PM is authorised at the organisation level and the code must identify a LegalEntity of that organisation.
- **LegalEntity-scoped order data and desk queries** — orders and desk queues are filtered by the active session scope; a session sees only its own LegalEntity's orders.
- **Role-gated navigation** — the Desk menu is available only on TradingHubs; a ClientRepresentative sees Settings only.
- **Reference-data LegalEntity scoping is deferred** — institutions, managed currencies, term rates, and on-call curves keep their existing (global) shape in this change; their LegalEntity dimension (hub-owned / client-read-only) and delegated grants land in Change C. Routing lands in Change B.

## Capabilities

### New Capabilities

- `legal-entity-tenancy`: Organisation, LegalEntity, OrganisationCode / LegalEntityCode, TradingHub / TradingClient role, hub–client connection, the one-deployment-per-Organisation boundary, and LegalEntity as the data partition dimension.
- `user-identity-and-scoping`: MMXUser, Role (`Trader` | `ClientRepresentative`), `(LegalEntity, role)` scopes, active session scope + re-scope, the `X-User-Id` header, and role-based access (desk vs settings).

### Modified Capabilities

- `money-market-order-lifecycle`: intake gains the required `legalEntityCode`; orders carry and are filtered by their owning LegalEntity.
- `desk-order-queries`: desk lists/detail are filtered by the active `(LegalEntity, role)` scope and use `X-User-Id`.
- `trader-desk-navigation`: the Desk menu is gated to TradingHubs; TradingClient users see Settings only.
- `back-office-accounting-handoff`: the trader-auth scenario reference is updated from `X-Trader-Id` to `X-User-Id`.

## Impact

- **Backend — hexagonal (contract-first):**
  - `mmx-domain`: new value objects/entities — `Organisation`, `LegalEntity`, `LegalEntityCode`/`OrganisationCode`, `TradingHubRole`/`TradingClientRole`, `MMXUser`, `Role`, `(LegalEntity, Role)` scope; order gains an owning `LegalEntityCode`. No Spring/framework.
  - `mmx-application`: new use cases — manage LegalEntities/role/hub-connection, manage MMXUser scopes, resolve active scope, re-scope; intake use case validates `legalEntityCode`; desk query use cases filter by active scope. New `port/out` for tenancy/user repositories.
  - `mmx-adapter-in-rest`: replace `TraderIdHeader` with `UserIdHeader` (`X-User-Id`) + role on trader endpoints; add `legalEntityCode` to the intake request schema. Contract-first: update `contracts/001-mm-order-processing/openapi.yaml` and `contracts/002-trader-orders-views/openapi.yaml` (+ `api-v1.md` mirrors), regenerate, then adapt controllers.
  - `mmx-adapter-out-persistence`: JPA entities + Flyway migrations for `organisation`, `legal_entity`, `legal_entity_role`/hub-connection, `mmx_user`, `mmx_user_scope`; add `legal_entity_code` column to `money_market_order` (and a migration default for existing rows). Repositories filter by LegalEntity.
  - `mmx-bootstrap`: deployment config identifying the Organisation this instance serves; transactional wrappers; auth/scope filter wiring.
- **Frontend (Angular 21):** role-gated nav (hide Desk for ClientRepresentatives), an active-scope/switcher in the shell, `X-User-Id` + role on API clients; intake widget passes `legalEntityCode`. Vitest for nav gating + switcher.
- **Contracts (BREAKING):** `001` intake schema (+ required `legalEntityCode`); `002` trader header `X-Trader-Id` → `X-User-Id` (+ role). Prose mirrors updated in the same delivery.
- **TDD:** red-first JUnit 5 for domain scoping rules and intake validation; Angular/Vitest for nav gating; REST integration tests for scope filtering. Spec–code parity is blocking (governance Principle VI).
- **ADRs:** 0001 (one MMX deployment per Organisation), 0004 (umbrella MMXUser identity + entity-scoped session).
