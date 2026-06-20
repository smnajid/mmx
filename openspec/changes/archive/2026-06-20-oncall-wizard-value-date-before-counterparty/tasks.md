## 1. Wizard state and step model

- [x] 1.1 **order-creation-widget** — Write failing `wizard-state.service.spec.ts` cases: OnCall `visibleSteps` includes `VALUE_DATE` between notice and counterparty; Term omits it; contract shortcut is OPERATION → VALUE_DATE → COUNTERPARTY → ORDER_DETAILS → REVIEW; `isStepComplete(VALUE_DATE)` requires `valueDate`; `setCounterparty` does not clear `valueDate`; changing notice period clears `valueDate` and downstream. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/wizard-state.service.spec.ts' --no-watch`
- [x] 1.2 **order-creation-widget** — Add `WizardStepId.VALUE_DATE`, update `FULL_FLOW_STEPS`, `CONTRACT_SHORTCUT_STEPS`, `computeVisibleSteps`, `isStepComplete`, `setValueDate`/`clearDownstream`, and `WIZARD_STEP_LABELS` until green.

## 2. Value date step (red-first)

- [x] 2.1 **order-creation-widget** — Write failing `step-value-date.component.spec.ts`: validates min settlement date; persists `valueDate` and advances on submit. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/step-value-date.component.spec.ts' --no-watch`
- [x] 2.2 **order-creation-widget** — Add `step-value-date.component.ts` and wire in `wizard-shell.component.ts` until green.

## 3. Counterparty and order details (red-first)

- [x] 3.1 **order-creation-widget** — Update failing `step-counterparty.component.spec.ts`: no value-date input; OnCall loads counterparties using `state.valueDate` from prior step. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/step-counterparty.component.spec.ts' --no-watch`
- [x] 3.2 **order-creation-widget** — Remove inline value-date UI from `step-counterparty.component.ts` until green.
- [x] 3.3 **order-creation-widget** — Update failing `step-order-details.component.spec.ts`: OnCall form has amount/minimumRate only (no valueDate control); Term still requires valueDate. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/step-order-details.component.spec.ts' --no-watch`
- [x] 3.4 **order-creation-widget** — Conditional order-details fields and `setOrderDetails` / `isStepComplete` for OnCall until green.

## 4. Integration specs

- [x] 4.1 **order-creation-widget** — Update failing `order-creation-wizard.component.spec.ts` (and `step-review.component.spec.ts` if needed) for OnCall step order and single valueDate in emitted payload. `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm test -- --include='**/order-creation-wizard.component.spec.ts' --no-watch`
- [x] 4.2 **order-creation-widget** — Fix wizard integration tests and exports (`public-api.ts` if new component exported) until green.

## 5. Final verification

- [x] 5.1 Run `export PATH="$HOME/.nvm/versions/node/v22.22.3/bin:$PATH" && cd frontend && npm run test` — green.
