# Frontend core (Angular 21)

## Routes (`src/app/app.routes.ts`)
- `/oncall/{received|assigned|executed}` and `/term/{…}` — desk queues; default entry `/oncall/received`; guarded by `traderDeskGuard`. Features: `features/oncall-orders/`, `features/term-orders/`, shared `assigned-orders/`, `received-orders/`.
- `/orders/:id` — `features/order-details/` (optional `?ws=&queue=` for desk highlight/back).
- `/settings/*` — `features/settings/settings-shell` + children (`currency-settings`, `institution-settings`, `term-rate-settings`, `oncall-rate-settings`, `delegated-grants`, `global-accounts`); ClientRepresentative entry; desk nav hidden on settings routes.
- `/dev/widget-playground` — dev-only widget harness (`isDevMode()`).

## App shell
`src/app/app.ts` — desk nav, session user picker, scope switcher (`SessionApiService` + `TraderContextService`). Desk nav hidden for ClientRepresentative.

## Layers
- `core/api/generated/*.ts` — OpenAPI-derived types (DO NOT hand-edit; `npm run generate:api` regenerates; `pre*` hooks run it automatically for start/build/test).
- `core/api/*-api.service.ts` — HTTP clients (orders, settings, session, delegated grants, global accounts, order-creation options); `core/api/user-api-headers.ts` = `X-User-Id` helper.
- `core/models/` — TS enums/models; `core/trader/` — `TraderContextService`, `DeskReturnService`, `trader-desk.guard.ts`, `received-view-mode.service.ts`.
- `shared/components/` — `order-table`, `status-badge`, `confirm-dialog`; `shared/amount-input/` — amount parsing directive (mirrored in widget project).

## PM order-creation widget (library)
`frontend/projects/order-creation-widget/` — ng-packagr; build `npm run build:widget` (pregenerates its own types); exercised via `/dev/widget-playground`.

## Tests
- Unit: `*.spec.ts` beside components (Vitest via `ng test`).
- E2E: Cypress `frontend/cypress/e2e/trader-workflow.cy.ts` (requires running app).
- Typecheck: `npm run typecheck` (tsc --noEmit -p tsconfig.app.json).

## UI language rules (spec-enforced)
Trader-facing labels MUST NOT contain "Workspace"; primary tabs are `ON-CALL` / `Term`; URL uses `oncall`, Java/API enums `ON_CALL` — same concept. Term and OnCall orders never mixed on one desk surface.
