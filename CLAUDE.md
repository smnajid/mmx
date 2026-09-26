# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

@AGENTS.md

The rules in AGENTS.md above are binding: SDD spec–code parity, strict TDD, the test-loop map, OpenSpec, and Serena memory rules. This file adds orientation only. For the full layout, read `CONTEXT.md` (the domain glossary; its `_Avoid_` terms are enforced) and `docs/agents/codebase-map.md` (where modules, controllers, Flyway migrations, and routes live).

## What this is

MMX (Money Market Exchange) handles internal money-market **order intake**, the **trader desk workflow** (receive → assign → update → execute / cancel / reject), and **reference-data settings** (currencies, institutions, term rates, on-call rate curves, delegated grants, global accounts). Actual market dealing happens outside MMX.

Each Organisation runs its own MMX deployment. A LegalEntity is either a TradingHub or a TradingClient, never both. A **routed order** is two linked records, one client-side and one hub-side, correlated by a deterministic routing id. Leg A (client → hub) goes over REST; leg B (hub outcome → client) goes over a Kafka outbox. Invariant: **silence is never terminal**, so a transport failure never rejects a routed client-side order.

## Repo layout (big picture)

- `backend/`: Java 25 / Spring Boot 4 hexagonal Maven reactor. **The reactor root is `backend/pom.xml`; there is no top-level pom**, so always `cd backend` before running `mvn`.
- `frontend/`: Angular 21 trader SPA (`src/app/`) plus the embeddable PM order-creation widget library (`projects/order-creation-widget/`).
- `contracts/00N-<feature>/`: canonical OpenAPI/AsyncAPI/JSON schemas, each with an `api-v1.md` prose mirror that must stay aligned. **`002-trader-orders-views` is the primary contract** for orders, session scope, and on-call rates.
- `openspec/specs/`: canonical capability specs. `openspec/changes/archive/` is history only.
- `docs/adr/`: tenancy, routing, identity, and back-office boundaries. Read these when behaviour differs between hub and client.
- `.scratch/`: local issue tracker (`docs/agents/issue-tracker.md`).

## Backend architecture

Dependencies point inward only, enforced by ArchUnit (`ArchitectureRules` → `DomainArchitectureTest`, `HexagonalArchitectureTest`):

```
mmx-bootstrap → {adapter-in-rest, adapter-out-persistence, adapter-out-messaging, adapter-out-integration} → mmx-application → mmx-domain
```

- `mmx-domain` has no framework dependencies.
- `mmx-application` holds the use cases (`port/in`), ports (`port/out`), and services. Business rules belong here.
- REST controllers are thin. They implement the `*Api` interfaces that the OpenAPI Generator produces from `contracts/` (output goes to `target/generated-sources/` and is not committed). Controllers may depend only on `application.port.in` and `application.exception`.
- `mmx-bootstrap` wires beans (`*ModuleConfiguration`), wraps use cases in transactions, owns `application*.yml`, and holds the Flyway migrations (`src/main/resources/db/migration/V*.sql`).
- The package root is `com.mmx.order` in every module.
- Identity comes from the `X-User-Id` header. The active `(LegalEntity, role)` is session state set via `POST /api/v1/session/scope`, never chosen per request. The exception is PM intake, which carries `legalEntityCode` in the request body.
- To work on a change, go contract-first: `contracts/<feature>/openapi.yaml` → controller → use case → domain.

## Commands

Backend test loops: use the map in AGENTS.md. Other backend commands:

```bash
cd backend && mvn -q -pl mmx-adapter-in-rest -am compile -DskipTests   # compile + regenerate OpenAPI sources
cd backend && mvn test -pl mmx-bootstrap -am -Dtest=HexagonalArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false
```

Frontend (run from `frontend/`; Node version is in `.nvmrc`):

```bash
npm start                 # ng serve on :4200 (prestart regenerates API types)
npm test                  # Vitest via ng test (pretest regenerates API + widget types)
npx ng test --include='src/app/path/to/foo.spec.ts'   # single spec
npm run typecheck
npm run verify:contracts  # generate:api + typecheck; run after any contract change
npm run build:widget
npm run e2e               # Cypress; the app must already be running
```

Never hand-edit the generated TS types (`src/app/core/api/generated/`, `projects/order-creation-widget/src/lib/generated/`). Regenerate them with `npm run generate:api`.

Specs: `openspec validate && openspec status --json`.

Local stacks:

```bash
./mmx-start.sh                         # docker compose (Postgres :5432, Redpanda) + backend + frontend
./mmx-cross-org-start.sh [--frontend]  # two deployments: LODH hub :8080 + CGEG client :8082 + identity stub :8090
./scripts/cross-org-smoke.sh           # end-to-end cross-org order flow check
```

## Gotchas

- Desk/settings default scope depends on resolution order. Scripts and tests must pin the scope with `POST /api/v1/session/scope`.
- Client-role Spring context tests must **not** activate the `rest-test` profile, because its seeders assume a hub. They should also disable `mmx.backoffice.outbox.relay-enabled` and `mmx.oncall.outbox.relay-enabled`; otherwise the relay polls race Flyway's clean+migrate.
- `oncall` (URL), `ON-CALL` (UI), and `ON_CALL` (enum) are the same concept. Don't "fix" the drift.
- `GlobalAccountsController` has no contract yet; it is a POC surface. Any material change to it needs a new contract under `contracts/`.
- Coding style (from `.cursor/rules/karpathy-guidelines.mdc`): write the minimum code for the task and make surgical diffs. State your assumptions, and ask when the request is ambiguous. For substantial UI work, follow `.cursor/skills/frontend-design/SKILL.md`.
