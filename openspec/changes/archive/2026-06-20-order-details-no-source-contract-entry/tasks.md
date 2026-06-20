## 1. Operation gating (fresh flow → SUBSCRIPTION only)

- [x] 1.1 **frontend** — Update `step-operation.component.spec.ts`: assert that for OnCall in the **fresh flow** (`contractShortcut` false) only SUBSCRIPTION is shown; in shortcut mode only INCREASE/DECREASE/REDEMPTION are shown; Term still shows SUBSCRIPTION.
- [x] 1.2 **frontend** — Update `step-operation.component.ts` filtering: shortcut → lifecycle ops; otherwise → `SUBSCRIPTION` only, until 1.1 passes.

## 2. Remove source-contract input from order details

- [x] 2.1 **frontend** — Update `step-order-details.component.spec.ts`: assert **no** `details-source-contract` field renders for any operation (SUBSCRIPTION or lifecycle); form is valid with only `amount` + `valueDate`.
- [x] 2.2 **frontend** — Update `step-order-details.component.ts`: remove the `sourceContractNumber` form control, the `@if (showSourceContractNumber())` template block, `showSourceContractNumber()`, and the `sourceContractNumber` argument passed to `setOrderDetails`.

## 3. Wizard state — derive-only source contract

- [x] 3.1 **frontend** — Update `wizard-state.service.spec.ts`: `setOrderDetails(amount, valueDate, minimumRate?)` does not modify `sourceContractNumber`; re-selecting operation in shortcut mode preserves the resolved `sourceContractNumber`; fresh flow keeps it undefined.
- [x] 3.2 **frontend** — Update `wizard-state.service.ts`: drop `sourceContractNumber` from `setOrderDetails`; guard `clearDownstream` so `sourceContractNumber` is preserved when `contractShortcut` is true (mirror the `noticePeriod` guard).

## 4. End-to-end widget behaviour

- [x] 4.1 **frontend** — Update `order-creation-wizard.component.spec.ts`: a contract-shortcut run still emits `orderReady` with `sourceContractNumber` equal to the resolved `contractNumber`, with no order-details source input involved.

## 5. Final verification

- [x] 5.1 Run `npm run test` in `frontend/` — green. (Both projects: `order-creation-widget` 61 tests, `frontend` app 90 tests, all passing.)
