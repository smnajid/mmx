## 1. Generation tooling

- [x] 1.1 Add `openapi-typescript` as a dev dependency in `frontend/package.json`
- [x] 1.2 Add a `generate:api` script that runs `openapi-typescript` against `../contracts/002-trader-orders-views/openapi.yaml` and writes to the generated-output dir (default `frontend/src/app/core/api/generated/trader-orders-views.ts`)
- [x] 1.3 Wire `prebuild`, `pretest`, and `prestart` scripts to run `generate:api` first
- [x] 1.4 Add the generated-output directory to `frontend/.gitignore`
- [x] 1.5 Run `npm run generate:api` in `frontend/` and confirm the types file is produced; verify generation exits non-zero if the contract path is wrong

## 2. Migrate 002 models to generated types

- [x] 2.1 In `frontend/src/app/core/models/order.model.ts`, re-export the `002` contracted shapes as aliases of the generated `components['schemas'][...]` types (`OrderSummary`, `OrderDetails`, `ReceiveOrderRequest`, `ReceiveOrderResponse`, `UpdateOrderRequest`, `ExecuteOrderRequest`, `RejectOrderRequest`)
- [x] 2.2 Remove the now-duplicated hand-written interface bodies for those shapes; keep frontend-only types not present in the contract (`PagedResponse<T>`, `PageParams`, `ReceivedListView`, app-side enums)
- [x] 2.3 Reconcile any name/shape/optionality differences surfaced by the compiler (adjust aliases; do not edit generated files)

## 3. Final verification

- [x] 3.1 Run `npm run test` in `frontend/` — green
- [x] 3.2 Run `openspec validate frontend-contract-codegen` — passes
