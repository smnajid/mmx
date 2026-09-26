## Why

The frontend contract-codegen convention ("API types are generated from canonical contracts, never hand-written") is currently enforced by nothing automated. Generated output is git-ignored, so the only safeguard against a contract becoming invalid or the project drifting out of sync is reviewer vigilance. A cheap, CI-runnable verification (guarantee A: generation succeeds and the project type-checks against freshly generated types) makes the investment durable.

## What Changes

- Add a single CI-runnable npm script (e.g. `verify:contracts`) in `frontend/package.json` that:
  1. regenerates all contract types from `contracts/*` (reusing `generate:api`), failing if any contract is missing/invalid, and
  2. type-checks the frontend against the freshly generated types (e.g. `tsc --noEmit` / `ng build` in CI), failing on any type error.
- This delivers **guarantee A** ("generated output is up to date with the contract and the project compiles against it"). It explicitly does **not** attempt guarantee B (mechanically detecting reappearing hand-written DTOs) — that is a harder, separate problem left out of scope.
- No CI pipeline currently exists in the repo; this change provides the **gate script** intended to be invoked by CI. Wiring an actual CI pipeline (GitHub Actions, etc.) is a separate infrastructure concern and is out of scope; the script is runnable locally and CI-ready.
- Non-material / internal: no HTTP contract, backend, or REST change.

## Capabilities

### Modified Capabilities
- `frontend-contract-codegen`: add a requirement that contract type generation and type-compatibility are verifiable via a single command suitable for CI (guarantee A).

## Impact

- **Frontend tooling**: `frontend/package.json` (new `verify:contracts` script; possibly a `typecheck` script if not present).
- **No application code change**, no backend, no contracts change (consumed read-only).
- **Out of scope**: actual CI pipeline definition; guarantee B (lint/structural detection of hand-written contract DTOs); widget coverage (separate change `widget-contract-codegen`); AsyncAPI.
