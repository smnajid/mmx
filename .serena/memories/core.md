# MMX — Money Market Exchange (core)

Internal Money Market **order intake + trader workflow** (assign/update/execute/cancel/reject) + **reference-data settings**; cross-org order routing (TradingClient → TradingHub) via REST (Leg A) + Kafka (Leg B). Market dealing is external.

## Read order (MANDATORY before code exploration)
1. `CONTEXT.md` — domain glossary, invariants, "avoid" terms (routed orders, delegated grants, global accounts, silence-is-never-terminal).
2. `docs/agents/codebase-map.md` — where code/specs/contracts live, module table, controller list, Flyway map, grep tips.
3. `AGENTS.md` — SDD governance + test feedback loop policy.

## Repo layout
- `backend/` — Java 25 / Spring Boot 4 hexagonal Maven reactor (7 modules). See `mem:backend/core`.
- `frontend/` — Angular 21 trader SPA + `projects/order-creation-widget/` (embeddable PM library). See `mem:frontend/core`.
- `contracts/00N-<feature>/` — canonical OpenAPI/AsyncAPI/JSON schemas; feature 002 is primary for orders/session/on-call rates.
- `openspec/specs/` — canonical capability specs (SDD); `openspec/changes/archive/` historical only.
- `docs/adr/` — tenancy, routing, identity, back-office boundaries.
- `scripts/` — schema registry, demo seed, BO callbacks; `mmx-cross-org-start.sh` (repo root) + `scripts/cross-org-smoke.sh` boot/verify the two-deployment cross-org stack (CGD@CGEG → LOC@LODH; details in `mem:backend/core`). `.scratch/` — issue tracker.

## Project-wide invariants
- **SDD parity**: material changes (API, domain behaviour, persistence, Trader UX) MUST update `openspec/specs/` AND `contracts/` (incl. `api-v1.md` prose mirror) — missing spec updates are a blocking defect.
- **TDD**: strict red-green-refactor for new behaviour/bug fixes; JUnit 5 tags `fast|integration|e2e|architecture`, exactly one per test class (enforced by `TestCategoryTaggingTest`).
- **Deployment**: one MMX instance per Organisation; LegalEntity is TradingHub XOR TradingClient; routed order = two linked records (client-side + hub-side) correlated by deterministic routing id.
- **Identity**: `X-User-Id` header; active `(LegalEntity, role)` scope is session-bound via `/api/v1/session/scope`, never a per-request body/header choice.

## Module memories
- Backend structure, hexagonal rules, controllers, persistence: `mem:backend/core`.
- Frontend routes, layers, widget, codegen: `mem:frontend/core`.
- Build/test commands: `mem:suggested_commands`; style/patterns: `mem:conventions`; definition of done: `mem:task_completion`; versions: `mem:tech_stack`.
