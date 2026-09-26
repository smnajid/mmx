## Context

`frontend-contract-codegen` (archived) covers the app tree: `src/app/core/api/generated/` is regenerated each app build and git-ignored. The `order-creation-widget` library was deliberately excluded because it is an `@angular/build:ng-packagr` library with its own `package.json` (`version 0.0.1`, `peerDependencies`) and a `public-api.ts` — it is embedded by **external host apps**.

Verified facts:
- Every response type in `api-responses.model.ts` maps 1:1 to a `002` schema.
- The widget consumes only `002` order-creation endpoints (`/api/v1/order-creation/*`).
- Response DTOs are **internal** — `public-api.ts` exports only `OrderCreationWizardComponent`, `OrderCreationPayload`, `WizardStep`, and `ORDER_CREATION_API_BASE_URL`. The response DTOs are never part of the published type surface.
- The library is built in this repo via `ng build order-creation-widget`, where `contracts/` is available.

## Goals / Non-Goals

**Goals:**
- Remove the widget's hand-written duplicates of `002` response schemas, replacing them with generated types.
- Generate at the widget's own build, inside the widget tree, git-ignored.
- Keep the library self-contained and publishable without the host needing `contracts/`.

**Non-Goals:**
- No change to the widget's public API (`OrderCreationPayload`, `WizardStep`, enums stay hand-written and exported).
- No `003`–`005` (not consumed by the widget); no AsyncAPI; no CI gate.
- No runtime client generation.

## Decisions

### D1: Widget owns its generated file; no cross-tree import
Generate to `projects/order-creation-widget/src/lib/generated/trader-orders-views.ts` and git-ignore it. The widget MUST NOT import from `src/app/core/api/generated` — libraries cannot depend on the host app, and ng-packagr would reject out-of-project imports anyway.
- **Alternative**: share one generated file across app + widget. Rejected — violates library independence and ng-packagr boundaries.

### D2: Generate at the widget's build, ship compiled types
External hosts don't have `contracts/`, so generation cannot happen at the host's build. It happens here, before `ng build order-creation-widget`, via a `pregenerate`/prebuild script in root `frontend/package.json` (e.g. `build:widget` with a preceding `generate:api:widget`). The generated `.ts` compiles into the library; since the DTOs are internal, they don't even surface in the published `.d.ts` — drift protection is the sole objective.
- **Alternative**: commit the generated file into the widget source. Rejected — keeps the ephemeral/git-ignored pattern consistent with the app and avoids stale committed artifacts.

### D3: Public domain types stay hand-written
`OrderCreationPayload`, `WizardStep`, and the `Tenor`/`NoticePeriod`/`OrderType`/`OrderOperation` unions remain hand-authored. They are the library's public contract with host apps and must not couple to either the app's enums or the generated contract module. This is the "frontend-only types may remain hand-written" carve-out from the capability spec, applied to a library's public surface.

## Risks / Trade-offs

- **ng-packagr compiling a generated file** → ensure the generated dir is inside the library's compiled source roots and the `tsConfig.lib` include globs pick it up; the library build is the guardrail.
- **Field/shape drift** (e.g. `CounterpartyOption.rateDate`/`indicative`) → reconcile via the compiler during apply; widget Vitest specs must stay green. If a hand-written field is absent from `002`, that is a contract gap to raise, not paper over.
- **Generation must run before lib build in all paths** (CI, local, publish) → wire via npm `pre*` hook on the widget build script so it cannot be skipped.

## Migration Plan

1. Add widget generation + build-script wiring; git-ignore the widget generated dir; run generation.
2. Re-point `api-responses.model.ts` response DTOs at generated `002` types; delete duplicated bodies.
3. Build the library (`ng build order-creation-widget`) and run widget specs; reconcile drift.
- **Rollback**: revert the model file and package.json wiring; generated dir is ephemeral.

## Open Questions

- Exact generated path/filename within the widget tree (default `projects/order-creation-widget/src/lib/generated/trader-orders-views.ts`).
- Whether a follow-up should make the widget's `Tenor`/`NoticePeriod` unions *derive* from generated `002` enums while staying exported (kept out of scope here to avoid touching the public API).
