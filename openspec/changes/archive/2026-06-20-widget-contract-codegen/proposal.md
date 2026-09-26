## Why

The `order-creation-widget` Angular library hand-writes its API response types in `api-responses.model.ts`, which mirror `002-trader-orders-views` schemas 1:1 (`OperationsResponse`, `TenorsResponse`, `CounterpartiesResponse`, `ContractInfoResponse`, `LiveContractsResponse`, etc.). These can drift from the canonical contract — the same risk the app side already eliminated via codegen. The widget was left out of prior codegen changes because it is a **separately published library embedded by external host apps**, which changes how generated types must be produced and shipped.

## What Changes

- Generate the widget's `002` response types at the **library's own build time** (in this repo, where `contracts/` exists), into a git-ignored file inside the widget's source tree (e.g. `projects/order-creation-widget/src/lib/generated/`).
- The widget library is self-contained: it MUST NOT import generated types from the app tree (`src/app/core/api/generated`). Generated types are an internal implementation detail compiled into the published package; external hosts consume the published `.d.ts`/bundle and never need `contracts/`.
- Migrate the response DTOs in `api-responses.model.ts` to aliases of the generated `002` types, removing the hand-written interface bodies.
- Keep the widget's **own public domain types** (`OrderCreationPayload`, `WizardStep`, and the `Tenor`/`NoticePeriod`/`OrderType`/`OrderOperation` unions in `order-creation-payload.model.ts`) hand-written — they are the library's public API surface (exported via `public-api.ts`) and intentionally independent of host enums.
- Add a widget build/generate wiring in the root `frontend/package.json` so `ng build order-creation-widget` regenerates types first.
- Non-material / internal: no HTTP contract, backend, or REST change. `002` consumed read-only.

## Capabilities

### Modified Capabilities
- `frontend-contract-codegen`: extend coverage to the `order-creation-widget` library, and add the external-host shipping model (the widget generates at its own build and ships compiled types, rather than relying on the consuming host to regenerate from contracts).

## Impact

- **Widget build/tooling**: root `frontend/package.json` (widget generation + build script wiring), git-ignore for the widget generated dir.
- **Widget code**: `projects/order-creation-widget/src/lib/models/api-responses.model.ts` re-pointed at generated `002` types; `wizard-api.service.ts` and components/specs consuming those types must still compile.
- **Contracts**: `002` consumed read-only; unchanged.
- **No app-tree, backend, Flyway, or REST impact.**
- **Out of scope**: contracts `003`–`005` are not consumed by the widget; AsyncAPI codegen; CI verification (separate change `frontend-codegen-ci-check`).
