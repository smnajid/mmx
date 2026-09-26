## Why

The `pm-order-creation-wizard-api` change delivered nine read-only REST endpoints under `/api/v1/order-creation/` that expose mmx reference data for a cascading order creation wizard. The design deferred the UI entirely to the Portfolio Management (PM) team. In practice, the wizard embodies subtle mmx domain rules (cascading availability filters, indicative rate warnings, contract-info lifecycle shortcuts, operation constraints per order type) — leaving PM to re-discover and re-implement that logic in their frontend creates duplication and drift risk. An mmx-owned, embeddable **Order Creation Widget** lets the mmx team ship and evolve the wizard experience atomically, while PM integrates it with minimal effort.

## What Changes

- **New Angular library** (`frontend/projects/order-creation-widget/`) — standalone, buildable, publishable as `@mmx/order-creation-widget`. Contains the full step-by-step wizard consuming the existing `/api/v1/order-creation/*` endpoints.
- **Widget API surface (Angular inputs/outputs)** — host provides `apiBaseUrl`, `portfolioNumber` (required), optional `orderType`, optional `contractNumber`, and receives an `orderReady` event with the complete order payload including `portfolioNumber`. Widget does NOT submit the order — the host does.
- **OnCall lifecycle shortcut** — when `contractNumber` is provided, the widget calls `GET /oncall/contract-info`, resolves currency + noticePeriod, and starts the wizard at the operation step (skipping type/currency/noticePeriod selection).
- **Self-contained error UI** — network failures and API errors render inline inside the widget with retry affordance; no error events bubble to the host.
- **Encapsulated styles** — widget ships its own scoped CSS; responsive layout with scrolling behavior.
- **Widget Playground** — dev-only route (`/dev/widget-playground`) in the mmx trader frontend that embeds the widget against the real mmx backend, with a configuration panel and event inspector for development and demo purposes.
- **No backend changes** — the existing nine `/api/v1/order-creation/*` endpoints are consumed as-is.

## Capabilities

### New Capabilities

- `pm-order-creation-widget`: Angular library providing an embeddable order creation wizard component. Consumes the existing order-creation-options API, guides the user through cascading selection steps (type → currency → operation → tenor/noticePeriod → counterparty → details → review), and emits a structured order payload on completion. Supports a contract-number shortcut for OnCall lifecycle operations.

### Modified Capabilities

(none — existing API endpoints and specs are consumed unmodified)

## Impact

- **Frontend — `frontend/projects/order-creation-widget/`** (new): Angular library with standalone components, internal HTTP service, wizard state management via signals. Buildable as `ng build order-creation-widget`.
- **Frontend — `frontend/src/app/`** (existing trader SPA): new dev-only route and playground component importing the widget library. No trader-facing behaviour changes.
- **Frontend — `angular.json`**: registers the new library project.
- **Deployment**: Phase 1 — PM consumes via git dependency or copies the built dist. Phase 2 — publish to private npm registry (GitHub Packages) with semver.
- **Testing**: Vitest unit tests for each step component (mock HTTP), widget playground for integration/demo against real backend. TDD for wizard behaviour.
- **No backend changes**: no new endpoints, no schema changes, no migrations.
- **No OpenAPI changes**: widget consumes existing contract.
