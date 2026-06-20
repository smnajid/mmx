## 1. Widget generation tooling

- [x] 1.1 Add a widget type-generation script in root `frontend/package.json` that runs `openapi-typescript` against `../contracts/002-trader-orders-views/openapi.yaml` into `projects/order-creation-widget/src/lib/generated/trader-orders-views.ts`
- [x] 1.2 Add a widget build script (e.g. `build:widget` → `ng build order-creation-widget`) with a `pre*` hook that runs the widget generation first
- [x] 1.3 Git-ignore the widget generated directory (`projects/order-creation-widget/src/lib/generated/`)
- [x] 1.4 Run the widget generation and confirm the file is produced; confirm the library's `tsConfig.lib` include globs compile it

## 2. Migrate widget response DTOs

- [x] 2.1 Re-point the `002`-contracted response shapes in `api-responses.model.ts` to aliases of the generated `components['schemas'][...]` types (`OperationsResponse`/`OperationOption`, `TenorsResponse`, `NoticePeriodsResponse`, `TermCurrenciesResponse`, `OnCallCurrenciesResponse`, `CounterpartiesResponse`/`CounterpartyOption`, `ContractInfoResponse`, `LiveContractsResponse`/`LiveContract`)
- [x] 2.2 Remove the now-duplicated hand-written bodies; keep the library's public domain types (`OrderCreationPayload`, `WizardStep`, `Tenor`/`NoticePeriod`/`OrderType`/`OrderOperation`) hand-written
- [x] 2.3 Reconcile name/shape/optionality differences surfaced by the compiler in `wizard-api.service.ts`, components, and specs (adjust aliases; do not edit generated files)

## 3. Final verification

- [x] 3.1 Build the library: `ng build order-creation-widget` — succeeds
- [x] 3.2 Run `npm run test` in `frontend/` — green
- [x] 3.3 Run `openspec validate widget-contract-codegen` — passes
