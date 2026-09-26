## Context

The `pm-order-creation-wizard-api` change delivered nine read-only REST endpoints under `/api/v1/order-creation/` (Term and OnCall paths for currencies, operations, tenors/notice periods, counterparties, plus contract-info). These endpoints expose mmx reference data with cascading availability filters — each step only shows options that have viable downstream counterparties.

Currently the mmx frontend (Angular 21 trader SPA at `frontend/src/app/`) has no order creation UI — orders come exclusively from the Portfolio Management system via `POST /api/v1/orders`. The PM system is also Angular-based and will consume the new widget as a library dependency.

The widget is a **pure frontend concern**: no backend changes, no new API endpoints, no schema modifications. It is a read-only consumer of existing endpoints that collects user selections and emits a structured payload.

## Goals / Non-Goals

**Goals:**

- Deliver a self-contained Angular library at `frontend/projects/order-creation-widget/` that the PM Angular app can import directly
- Full wizard flow: order type → currency → operation → tenor/noticePeriod → counterparty → details → review → emit payload
- OnCall lifecycle shortcut via `contractNumber` input (resolves currency + noticePeriod from contract-info endpoint, starts at operation step)
- Responsive layout with scrolling; self-contained error UI with retry
- Widget Playground route inside the mmx trader frontend for development and demo
- TDD: Vitest unit tests for wizard logic and step components
- Publishable as `@mmx/order-creation-widget` via standard Angular library build

**Non-Goals:**

- No Web Component / Angular Elements build (deferred to Phase 3 if non-Angular consumers appear)
- No order submission — the widget emits the payload, the host submits `POST /api/v1/orders`
- No backend changes, no OpenAPI modifications
- No design system or CSS custom property theming — encapsulated neutral styles
- No production deployment pipeline (Phase 2 concern)
- No Cypress e2e tests (Vitest unit + playground integration is sufficient for Phase 1)

## Decisions

### D1: Angular library inside the existing frontend workspace

The widget lives at `frontend/projects/order-creation-widget/` using Angular's built-in library support (`ng-package.json`, buildable via `ng build order-creation-widget`).

**Rationale:** Shares the Angular 21 toolchain, TypeScript config, and dev server with the trader SPA. The trader app can import the library directly for the playground without a publish step. Standard Angular library workflow produces a clean npm-publishable package.

**Alternatives considered:**
- *Separate repository:* more isolation but doubles CI/CD setup and makes co-development harder. Overkill for an internal team.
- *Top-level `widget/` directory with independent tooling:* requires a second `angular.json` or a non-Angular build (Vite standalone). No benefit over Angular's library support.

### D2: Standalone components with signal-based state

All wizard components are Angular standalone (no NgModule). Internal state is managed via a `WizardStateService` using Angular signals, scoped to each widget instance via `providers` on the root component.

**Rationale:** Standalone components are the Angular 21 default and tree-shake well for library consumers. Signals provide fine-grained reactivity without RxJS complexity for synchronous state. Scoping the service to the root component ensures multiple widget instances on the same page don't conflict.

**Alternatives considered:**
- *NgRx or other state library:* over-engineered for a linear wizard with ~8 fields of state.
- *RxJS BehaviorSubject-based service:* works but signals are simpler for template binding and avoid subscription management.

### D2b: portfolioNumber as a required pass-through input

The widget receives `portfolioNumber` (required string) from the host. It displays the value in the review step summary and includes it in the `OrderCreationPayload` output. The widget does not validate or resolve the portfolio — it is an opaque identifier owned by the PM application that maps directly to the `portfolioNumber` field on `ReceiveOrderRequest`.

**Rationale:** `portfolioNumber` is a required field on the mmx intake contract (`POST /api/v1/orders`). The PM app already knows it (it's the context in which the user opens the wizard). Passing it through the widget ensures the output payload is complete and ready for submission without the host needing to re-attach it.

### D3: Widget consumes mmx API directly (no BFF proxy)

The widget's internal `WizardApiService` makes HTTP calls directly to the mmx backend using the `apiBaseUrl` input. CORS is assumed configured on the mmx backend for the PM app's origin.

**Rationale:** The nine endpoints already exist, require no auth (`X-Trader-Id` not needed), and return small payloads. Adding a BFF proxy doubles effort with zero functional benefit for an internal system.

**Alternatives considered:**
- *PM BFF proxying to mmx:* adds latency, another deployment, and coupling between PM's backend and mmx's API shape. Appropriate for external consumers, not internal Angular-to-Angular.

### D4: Widget does NOT submit the order

The wizard's final step emits an `orderReady` output with a typed `OrderCreationPayload`. The host application (PM) enriches it with PM-specific fields (`externalOrderReference`, portfolio context) and calls `POST /api/v1/orders` itself.

**Rationale:** Order submission involves PM-side concerns (idempotency key generation, portfolio association, approval workflows) that the widget cannot and should not know about. Clean separation: widget = data collection, host = submission.

### D5: Contract-number shortcut resolves at widget init

When the `contractNumber` input is set, the widget immediately calls `GET /api/v1/order-creation/oncall/contract-info?contractNumber=X` on init. On success, it locks `orderType=ON_CALL`, sets `currency` and `noticePeriod` from the response, filters operations to `[INCREASE, DECREASE, REDEMPTION]`, and starts the wizard at the operation step. On 404, it shows an error with a "Start fresh" fallback that resets to the normal full flow.

**Rationale:** Matches the existing API design (contract-info was built specifically for this use case). Resolving at init avoids a mid-wizard fetch that could break flow.

### D6: ViewEncapsulation.Emulated with self-contained styles

The widget ships its own CSS scoped via Angular's default emulated encapsulation (attribute selectors). Styles are neutral and responsive — no attempt to inherit the host's design system.

**Rationale:** Simplest approach. Shadow DOM adds complexity (form control styling, scroll containers) for no benefit when both mmx and PM are Angular apps. Emulated encapsulation prevents style leaking without Shadow DOM limitations.

### D7: Dev-only playground route

The playground is a lazy-loaded route at `/dev/widget-playground` in the mmx trader frontend, guarded by an `isDevMode()` check (not included in production builds via tree-shaking). It imports the widget library directly and provides a configuration panel alongside an event log.

**Rationale:** Fast feedback loop during development. The dev guard ensures it never appears in production. Using the real mmx backend (same as the trader app) gives true integration coverage.

### D8: Deployment phases

| Phase | Consumer experience | Infrastructure |
|-------|-------------------|----------------|
| **1** | PM imports from git or uses built dist tarball | None |
| **2** | PM installs `@mmx/order-creation-widget` from private npm registry | GitHub Packages or Verdaccio |
| **3** (future) | Web Component build for non-Angular consumers | CDN hosting |

**Rationale:** Start with zero infrastructure. Add registry when version management becomes a real pain point. Web Component build only if a non-Angular consumer appears.

## Risks / Trade-offs

**[Risk] CORS configuration required on mmx backend** → The mmx backend must allow the PM app's origin. This is a one-line Spring config change (`@CrossOrigin` or `WebMvcConfigurer`). Documented as a prerequisite for PM integration, not part of this change.

**[Risk] API shape changes break the widget** → The widget is tightly coupled to the nine order-creation endpoints. If those change, the widget must be updated in the same delivery. Mitigated by both living in the same repo — contract-first OpenAPI ensures the contract is explicit and changes are visible.

**[Trade-off] No SSR or server-side pre-rendering** → The widget is client-rendered. Acceptable for an internal B2B tool where SEO and initial load time are not priorities.

**[Trade-off] Bundle size for PM** → PM imports the widget library which adds its own component tree. Since both are Angular 21, there's no framework duplication. The widget's own code is small (~10 components, 1 service, no heavy dependencies).

**[Trade-off] No design token integration** → The widget looks like "the widget" regardless of PM's design system. Acceptable for Phase 1. Can expose CSS custom properties later if visual integration becomes important.
