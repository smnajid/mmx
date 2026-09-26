## Context

Two archived changes (`frontend-contract-codegen`, `frontend-contract-codegen-settings`) established generation for `002`–`005` and migrated the app services/models. Generated output is git-ignored and regenerated via `pre*` hooks. There is **no CI pipeline** in the repo today.

"Drift-check" splits into two guarantees:
- **A — generated output is current and the project compiles against it.** Largely already exercised locally (`pretest` runs `generate:api`), but not packaged as an explicit, CI-intended gate.
- **B — no hand-written DTO has reappeared for a contracted schema.** The real regression risk, but hard to automate (requires structural/lint detection). Out of scope per the decision to pursue guarantee A only.

## Goals / Non-Goals

**Goals:**
- One command that regenerates from contracts and type-checks the frontend, exiting non-zero on contract-invalid or type errors.
- CI-ready and locally runnable; deterministic.

**Non-Goals:**
- Defining an actual CI pipeline (no workflow files in scope).
- Guarantee B (detecting reappearing hand-written contract DTOs).
- Any application/runtime change.

## Decisions

### D1: Single `verify:contracts` script = `generate:api` + type-check
Compose the existing `generate:api` (which already fails loudly on a missing/invalid contract) with a no-emit type-check over the frontend. This is the smallest unit that delivers guarantee A.
- **Alternative**: rely on `npm run test` (which already pre-generates). Rejected as the *primary* gate — it conflates type/contract verification with the unit-test run and is slower; a dedicated script states intent and can run independently in CI.

### D2: Type-check via `tsc --noEmit` (or `ng build`) — pick the faster reliable option at apply
Prefer `tsc --noEmit` against the app tsconfig if it cleanly covers the generated-types usage; otherwise fall back to `ng build`. Decide empirically at apply time based on which reliably catches a deliberately introduced type error.
- **Alternative**: `ng build` always. Acceptable but slower; keep as fallback.

### D3: Script is the deliverable; pipeline wiring deferred
Because no CI system exists, the requirement targets a *command*, not a workflow file. When a pipeline is later added, it invokes `npm run verify:contracts`. This keeps the change small and avoids committing to a CI vendor prematurely.

## Risks / Trade-offs

- **False sense of security**: guarantee A does NOT stop someone re-adding a hand-written `interface OrderSummary`. Documented explicitly; guarantee B is a known follow-up.
- **Node version**: the toolchain requires Node ≥ 20.19/22.12 (Angular CLI). The script assumes a compatible Node in CI — note in tasks.
- **Duplication with `pretest`**: acceptable; the dedicated gate is intentionally runnable without the test suite.

## Migration Plan

1. Add `verify:contracts` (and a `typecheck` script if needed) to `frontend/package.json`.
2. Validate it fails on (a) a temporarily broken contract path and (b) a deliberately introduced type error, and passes on a clean tree.
- **Rollback**: remove the scripts; no code or contract impact.

## Open Questions

- `tsc --noEmit` vs `ng build` as the type-check step (resolve at apply by testing detection of a deliberate type error).
- Whether to also add a minimal CI workflow now or leave pipeline wiring entirely to a future infra change (leaning: leave it out, per D3).
