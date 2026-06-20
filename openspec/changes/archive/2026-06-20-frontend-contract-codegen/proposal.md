## Why

The backend enforces contract-first OpenAPI by regenerating server interfaces and DTOs from `contracts/*/openapi.yaml` on every build, but the **frontend hand-writes** its request/response types (e.g. `frontend/src/app/core/models/order.model.ts` duplicates the `002-trader-orders-views` schemas). These hand-written interfaces can silently drift from the canonical contract, defeating the "frontend MUST target paths and bodies that match the published contract" rule. We want the frontend to derive its types from the same canonical OpenAPI the backend uses, with no checked-in, drift-prone copies.

## What Changes

- Introduce a **build-time TypeScript type generation** step in the frontend that reads `contracts/002-trader-orders-views/openapi.yaml` and emits type definitions consumed by the Angular app.
- Use **`openapi-typescript`** (types-only): generated output is a single ambient/exported types module — no runtime client, hand-written `HttpClient` services stay.
- Generated output is **git-ignored** and **regenerated each build** (matching the backend's ephemeral-codegen pattern); generation runs before `ng build`/`ng test` and via a standalone script.
- **Pilot scope: one contract** (`002-trader-orders-views`). Contracts `003–005` and AsyncAPI are explicitly out of scope for this change.
- Migrate the `002`-derived hand-written models (`order.model.ts`) to reference the generated types as the source of truth, removing the duplicated shape definitions.
- This is a **non-material / internal** change: the HTTP contract (`openapi.yaml`, `api-v1.md`) is unchanged. No backend or REST surface change.

## Capabilities

### New Capabilities
- `frontend-contract-codegen`: Establishes the convention and acceptance criteria that frontend API types are generated from the canonical OpenAPI contract at build time, are git-ignored, and are never hand-authored where a contract exists.

### Modified Capabilities
<!-- None: no existing capability's requirements change. The HTTP contract is unchanged. -->

## Impact

- **Frontend build/tooling**: `frontend/package.json` (new dev dependency `openapi-typescript`, new `generate:api` script, `prebuild`/`pretest` hooks), `frontend/.gitignore` (ignore generated dir), Angular `tsconfig`/path mapping if needed.
- **Frontend code**: `frontend/src/app/core/models/order.model.ts` re-pointed at generated `002` types; `order-api.service.ts` consumes them. Vitest suite must stay green.
- **Contracts**: consumed read-only; `contracts/002-trader-orders-views/openapi.yaml` is unchanged.
- **No backend impact**; no Flyway, no domain, no REST changes.
- **Convention going forward**: new frontend code for a contracted endpoint must use generated types rather than hand-written interfaces.
