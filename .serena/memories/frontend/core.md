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

## Institution settings (`features/institution-settings/`)
- Role-split on `TraderContextService`: Trader = native institutions (Deactivate/Reactivate); ClientRepresentative = onboarded institutions (onboard from `listGrantedInstitutions`, Offboard/Re-onboard via the same deactivate/activate endpoints).
- Client-enablement toggles come from `InstitutionResponse.enablements[]` (single-institution GET only, not the list) — not from the grants API. Switching on needs grant + the OrderType's counterparty account; switching off is always allowed; "enabled, not granted" is flagged.

## PM order-creation widget (library)
`frontend/projects/order-creation-widget/` — ng-packagr; build `npm run build:widget` (pregenerates its own types); exercised via `/dev/widget-playground`.

## Tests
- Unit: `*.spec.ts` beside components (Vitest via `ng test`).
- Single spec: `npx ng test frontend --watch=false --include='src/app/…/x.spec.ts'` — the project name is required (two projects; without it: "No tests found"). A TS error in ANY spec fails the whole build, so regenerated contract types that add a required field break unrelated spec fixtures.
- E2E: Cypress `frontend/cypress/e2e/trader-workflow.cy.ts` (requires running app).
- Typecheck: `npm run typecheck` (tsc --noEmit -p tsconfig.app.json).

## UI language rules (spec-enforced)
Trader-facing labels MUST NOT contain "Workspace"; primary tabs are `ON-CALL` / `Term`; URL uses `oncall`, Java/API enums `ON_CALL` — same concept. Term and OnCall orders never mixed on one desk surface.
