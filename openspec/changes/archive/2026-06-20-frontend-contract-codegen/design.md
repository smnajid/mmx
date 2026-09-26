## Context

The backend (`mmx-adapter-in-rest`) regenerates Spring API interfaces and Jackson models from `contracts/*/openapi.yaml` on every Maven `generate-sources` phase; the generated code lives under `target/` and is never committed. The Angular frontend has no equivalent: API request/response shapes are hand-authored TypeScript interfaces. `frontend/src/app/core/models/order.model.ts` is effectively a manual copy of the `002-trader-orders-views` OpenAPI schemas (`OrderSummary`, `OrderDetails`, `ReceiveOrderRequest`, etc.), and `order-api.service.ts` wires those types to `/api/v1/orders` calls by hand.

This change brings the frontend in line with the backend's contract-first discipline for the `002` contract as a pilot, using a types-only generator so the existing hand-written `HttpClient` services are preserved.

## Goals / Non-Goals

**Goals:**
- Generate TypeScript types from `contracts/002-trader-orders-views/openapi.yaml` at build time.
- Make the generated types the single source of truth for `002` request/response shapes; eliminate the duplicated definitions in `order.model.ts`.
- Keep generated output git-ignored and regenerated each build (parity with backend ephemeral codegen).
- Establish a repeatable convention future contracts/UI work can adopt.

**Non-Goals:**
- No runtime client generation (no `typescript-angular` services); hand-written `HttpClient` services stay.
- No changes to the HTTP contract, backend, or REST surface.
- Contracts `003–005` and AsyncAPI/event schemas are out of scope (follow-up changes).
- No CI gate wiring beyond the local build hooks (a drift check can be a follow-up).

## Decisions

### D1: Use `openapi-typescript` (types-only) over `openapi-generator` typescript-angular
- **Why**: The app already has clean, intentional `HttpClient` services with trader-header and paging helpers. A full client generator would either replace those (large churn, loss of conventions) or sit unused. `openapi-typescript` emits a single, dependency-free `.d.ts`-style module — minimal footprint, fast, no runtime code.
- **Alternative considered**: `openapi-generator-cli` `typescript-angular` (full services + models). Rejected for this pilot: heavier dependency (Java/JAR or npm wrapper), generates RxJS services that duplicate existing ones, and conflicts with the established service patterns.

### D2: Generation invocation and lifecycle
- Add a dev dependency `openapi-typescript` and an npm script, e.g. `generate:api` that writes to a git-ignored path.
- Hook generation ahead of the existing scripts via `prebuild`, `pretest`, and `prestart` so `ng build`, `ng test`, and `ng serve` always run against fresh types.
- **Why**: mirrors the backend's "regenerate every build" guarantee; no stale committed artifact can drift.

### D3: Generated output location and git handling
- Emit to a dedicated directory, e.g. `frontend/src/app/core/api/generated/` (final path decided at apply time), containing one generated file per contract (pilot: `trader-orders-views.ts`).
- Add the directory to `frontend/.gitignore`.
- **Why**: keeps generated code physically separate from hand-written code, makes the ignore rule unambiguous, and signals "do not edit".

### D4: Consuming the generated types
- `openapi-typescript` exposes schemas under `components["schemas"]["<Name>"]`. Re-export friendly aliases from `order.model.ts` (e.g. `export type OrderSummary = components['schemas']['OrderSummary']`) so existing imports across components/services keep working with minimal churn.
- Remove the hand-written interface bodies that duplicate `002` schemas; retain any genuinely frontend-only types (e.g. `PagedResponse<T>`, `PageParams`, `ReceivedListView`) that are not in the contract.
- **Why**: smallest-diff migration; preserves the public import surface the rest of the app relies on.

## Risks / Trade-offs

- **Generated names/shape mismatch with hand-written ones** → Diff the generated `002` types against current `order.model.ts` during apply; adjust aliases. Vitest suite (`ng test`) is the guardrail and must stay green.
- **Build now depends on the contracts path being present/valid** → Generation reads a repo-relative path; a missing/invalid spec fails the build loudly (desired — surfaces contract problems early).
- **Enums/closed sets**: contract may model some fields as plain `string` where the app uses TS enums (`OrderType`, etc.) → keep app-side enums as a UX convenience layer; do not force the generated `string` types to become enums.
- **Partial adoption confusion** (only `002` generated, `003–005` still hand-written) → spec records `002` as the pilot and states the intended convention; follow-up changes extend coverage.

## Migration Plan

1. Add dependency + scripts + `.gitignore`; run generation to produce `002` types.
2. Re-point `order.model.ts` aliases at generated types; delete duplicated bodies.
3. Run `npm run test` in `frontend/`; fix any type drift surfaced.
- **Rollback**: revert `package.json`/`.gitignore` and restore `order.model.ts` bodies — no runtime or contract change to unwind.

## Open Questions

- Final generated directory path and per-contract file naming (resolve at apply; default `frontend/src/app/core/api/generated/`).
- Whether to add a CI "generated types are up to date / no hand-written contract DTOs" check now or defer to a follow-up (leaning defer).
