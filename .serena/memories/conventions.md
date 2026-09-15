# Conventions

## Process (from AGENTS.md — binding)
- **SDD parity**: any material change (API, domain behaviour, persistence, Trader-facing UX) must update `openspec/specs/` + `contracts/` (incl. `api-v1.md` prose mirror) in the same change; missing spec updates are blocking defects. `GlobalAccountsController` is the only ad-hoc (no contract) surface.
- **TDD**: red → green → refactor by default. Exactly one JUnit 5 category tag per backend test class (`fast`/`integration`/`e2e`/`architecture`); `TestCategoryTaggingTest` fails the build otherwise. Pick the narrowest loop that can fail for the right reason.
- Workflow: contract first (`contracts/<feature>/openapi.yaml` → controller → use case → domain); hexagonal inward (domain → application → adapter); read `docs/adr/` when tenancy/routing/identity involved.

## Language & vocabulary (CONTEXT.md is canonical)
Use MMX terms verbatim; respect the `_Avoid_` lists: order (not deal/trade/transaction), OrderType/OrderStatus/OrderOperation names, MMXUser, LegalEntity (not entity/branch), Institution (not counterparty for request bodies), TradingHub/TradingClient (not hub/client alone).
- Known drift: URL/UI `oncall`/`ON-CALL` vs enum `ON_CALL` — same concept, do not "fix".

## Java style
- Hexagonal: domain has zero framework deps; REST adapters depend on `application.port.in` + `application.exception` only (ArchUnit Tier-2 enforced).
- Thin controllers: implement generated `*Api` interfaces, no business rules.
- Business rules in `mmx-application` services, not JPA adapters.
- Domain invariants worth remembering: silence is never terminal (transport failures never reject a routed client-side order); Term↔Tenor / OnCall↔NoticePeriod never mixed; MinimumRate immutable after intake; client-side `Routed` only (never `Assigned`).

## Angular style
- Standalone components, feature folders under `features/`, shared under `shared/`.
- Generated API types are never hand-edited — regenerate via `npm run generate:api` (runs automatically in pre-hooks).
- No comments unless asked (agent rule).
