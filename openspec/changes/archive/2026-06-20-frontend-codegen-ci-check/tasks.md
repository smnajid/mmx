## 1. Verification script

- [x] 1.1 Add a `typecheck` script to `frontend/package.json` (e.g. `tsc --noEmit -p tsconfig.app.json`); if `tsc --noEmit` does not reliably cover generated-types usage, use `ng build` instead (decide by testing detection of a deliberate type error)
- [x] 1.2 Add a `verify:contracts` script that runs `generate:api` then the type-check, so it fails on either a missing/invalid contract or a type error
- [x] 1.3 Confirm the script does not depend on the unit-test suite (runnable standalone)

## 2. Validate guarantee A

- [x] 2.1 Confirm `npm run verify:contracts` exits 0 on the current clean tree
- [x] 2.2 Confirm it exits non-zero when a contract path is temporarily broken (then restore)
- [x] 2.3 Confirm it exits non-zero when a deliberate type error is introduced against a generated type (then revert)

## 3. Final verification

- [x] 3.1 Run `npm run verify:contracts` in `frontend/` — green on clean tree
- [x] 3.2 Run `openspec validate frontend-codegen-ci-check` — passes
