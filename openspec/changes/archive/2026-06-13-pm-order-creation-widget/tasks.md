## 1. Library scaffolding and project setup

- [x] 1.1 Generate Angular library: run `cd frontend && ng generate library order-creation-widget --prefix=mmx` to scaffold `frontend/projects/order-creation-widget/` with `ng-package.json`, `tsconfig.lib.json`, and `public-api.ts`.
- [x] 1.2 Verify library compiles: `cd frontend && ng build order-creation-widget`.
- [x] 1.3 Remove scaffolded placeholder component/service (the generator creates a default component and service that we'll replace with our own).

## 2. Models and API service

- [x] 2.1 Create `projects/order-creation-widget/src/lib/models/order-creation-payload.model.ts`: define `OrderCreationPayload` interface (portfolioNumber, orderType, currency, operation, tenor?, noticePeriod?, institutionCode, counterparty, amount, valueDate, minimumRate?, sourceContractNumber?) and `WizardStep` type.
- [x] 2.2 Create `projects/order-creation-widget/src/lib/models/wizard-state.model.ts`: define `WizardState` interface and `WizardStepId` enum (ORDER_TYPE, CURRENCY, OPERATION, TENOR_OR_NOTICE_PERIOD, COUNTERPARTY, ORDER_DETAILS, REVIEW).
- [x] 2.3 Create `projects/order-creation-widget/src/lib/models/api-responses.model.ts`: define TypeScript interfaces matching the existing OpenAPI response schemas (`CurrencyOption`, `OperationOption`, `TenorOption`, `NoticePeriodOption`, `CounterpartyOption`, `ContractInfoResponse`, list wrappers).
- [x] 2.4 Write failing `WizardApiService` test (`projects/order-creation-widget/src/lib/services/wizard-api.service.spec.ts`): test that each of the nine endpoint methods calls the correct URL with correct parameters using `HttpClientTestingModule`. `cd frontend && npx ng test order-creation-widget --include='**/wizard-api.service.spec.ts'`
- [x] 2.5 Implement `WizardApiService` (`projects/order-creation-widget/src/lib/services/wizard-api.service.ts`): injectable service accepting `apiBaseUrl` via injection token, wrapping all nine `/api/v1/order-creation/*` GET calls. Implement until tests pass.

## 3. Wizard state service

- [x] 3.1 Write failing `WizardStateService` test (`projects/order-creation-widget/src/lib/services/wizard-state.service.spec.ts`): test initial state, step transitions (next/back/goTo), selection updates, reset, contract-shortcut mode (skipping steps). `cd frontend && npx ng test order-creation-widget --include='**/wizard-state.service.spec.ts'`
- [x] 3.2 Implement `WizardStateService` (`projects/order-creation-widget/src/lib/services/wizard-state.service.ts`): signal-based state management. Tracks currentStep, selections, determines visible steps based on orderType and shortcut mode. Implement until tests pass.

## 4. Step components — Order Type and Currency

- [x] 4.1 Write failing `StepOrderTypeComponent` test (`steps/step-order-type.component.spec.ts`): test that selecting Term/OnCall updates state and emits navigation. `cd frontend && npx ng test order-creation-widget --include='**/step-order-type.component.spec.ts'`
- [x] 4.2 Implement `StepOrderTypeComponent` (`steps/step-order-type.component.ts`): standalone component with Term/OnCall selection cards, responsive layout. Implement until tests pass.
- [x] 4.3 Write failing `StepCurrencyComponent` test (`steps/step-currency.component.spec.ts`): test loading state, API call with correct type, currency display, selection, empty state, error + retry. `cd frontend && npx ng test order-creation-widget --include='**/step-currency.component.spec.ts'`
- [x] 4.4 Implement `StepCurrencyComponent` (`steps/step-currency.component.ts`): calls `WizardApiService` for currencies based on orderType, displays cards, handles loading/error. Implement until tests pass.

## 5. Step components — Operation, Tenor/NoticePeriod

- [x] 5.1 Write failing `StepOperationComponent` test (`steps/step-operation.component.spec.ts`): test operations displayed with minAmount, correct filtering for contract-shortcut mode (excludes SUBSCRIPTION), selection. `cd frontend && npx ng test order-creation-widget --include='**/step-operation.component.spec.ts'`
- [x] 5.2 Implement `StepOperationComponent` (`steps/step-operation.component.ts`): calls operations endpoint, displays operation cards with minimum amounts, filters for lifecycle mode. Implement until tests pass.
- [x] 5.3 Write failing `StepTenorNoticePeriodComponent` test (`steps/step-tenor-notice-period.component.spec.ts`): test tenor display for Term, notice period display for OnCall, correct API call per type, skip logic for contract shortcut. `cd frontend && npx ng test order-creation-widget --include='**/step-tenor-notice-period.component.spec.ts'`
- [x] 5.4 Implement `StepTenorNoticePeriodComponent` (`steps/step-tenor-notice-period.component.ts`): unified component handling both tenors and notice periods based on orderType. Calls appropriate endpoint. Implement until tests pass.

## 6. Step components — Counterparty, Order Details, Review

- [x] 6.1 Write failing `StepCounterpartyComponent` test (`steps/step-counterparty.component.spec.ts`): test counterparties listed sorted by rate, indicative flag renders warning, selection stores institutionCode + counterparty name. `cd frontend && npx ng test order-creation-widget --include='**/step-counterparty.component.spec.ts'`
- [x] 6.2 Implement `StepCounterpartyComponent` (`steps/step-counterparty.component.ts`): calls counterparties endpoint (Term or OnCall depending on type), renders table/cards with rate, rateDate, indicative badge. Implement until tests pass.
- [x] 6.3 Write failing `StepOrderDetailsComponent` test (`steps/step-order-details.component.spec.ts`): test amount validation (>= minAmount), valueDate validation (>= today+2), minimumRate optional, sourceContractNumber shown for lifecycle ops. `cd frontend && npx ng test order-creation-widget --include='**/step-order-details.component.spec.ts'`
- [x] 6.4 Implement `StepOrderDetailsComponent` (`steps/step-order-details.component.ts`): reactive form with amount, valueDate, minimumRate, sourceContractNumber (conditional). Client-side validation. Implement until tests pass.
- [x] 6.5 Write failing `StepReviewComponent` test (`steps/step-review.component.spec.ts`): test summary renders all selections, "Create Order" button emits complete payload, "Cancel" button emits cancelled. `cd frontend && npx ng test order-creation-widget --include='**/step-review.component.spec.ts'`
- [x] 6.6 Implement `StepReviewComponent` (`steps/step-review.component.ts`): displays full summary, confirm/cancel buttons. Implement until tests pass.

## 7. Wizard shell and root component

- [x] 7.1 Write failing `WizardShellComponent` test (`wizard-shell/wizard-shell.component.spec.ts`): test step indicator renders correct count, back/next navigation, step click navigation for completed steps, forward disabled for incomplete. `cd frontend && npx ng test order-creation-widget --include='**/wizard-shell.component.spec.ts'`
- [x] 7.2 Implement `WizardShellComponent` (`wizard-shell/wizard-shell.component.ts`): stepper chrome with progress indicator, navigation controls, responsive scroll container. Renders the active step component. Implement until tests pass.
- [x] 7.3 Write failing `OrderCreationWizardComponent` test (`order-creation-wizard.component.spec.ts`): test inputs (apiBaseUrl required, portfolioNumber required, orderType skips step 1, contractNumber triggers shortcut + API call), outputs (orderReady emitted with payload including portfolioNumber, cancelled emitted). Test contract-info 404 shows error with fallback. `cd frontend && npx ng test order-creation-widget --include='**/order-creation-wizard.component.spec.ts'`
- [x] 7.4 Implement `OrderCreationWizardComponent` (`order-creation-wizard.component.ts`): root standalone component. Provides `WizardStateService` and `WizardApiService` (with apiBaseUrl token). Accepts required `portfolioNumber` input and includes it in the output payload. Handles contractNumber init logic. Wires inputs/outputs. Implement until tests pass.

## 8. Shared components (error, loading)

- [x] 8.1 Create `WizardErrorComponent` (`shared/wizard-error.component.ts`): standalone component accepting error message and emitting retry. Simple inline error panel with "Retry" button.
- [x] 8.2 Create `WizardLoadingComponent` (`shared/wizard-loading.component.ts`): standalone component showing a loading spinner/skeleton.

## 9. Public API and library exports

- [x] 9.1 Update `projects/order-creation-widget/src/public-api.ts`: export `OrderCreationWizardComponent`, `OrderCreationPayload`, `WizardStep`, and the API base URL injection token. Do not export internal step components.
- [x] 9.2 Verify library builds cleanly: `cd frontend && ng build order-creation-widget`.

## 10. Widget Playground (mmx trader frontend)

- [x] 10.1 Create `frontend/src/app/features/widget-playground/widget-playground.component.ts`: standalone component importing the widget library, providing config panel (orderType selector, contractNumber input, apiBaseUrl input) and event log (JSON display of emitted events).
- [x] 10.2 Create `frontend/src/app/features/widget-playground/widget-playground.routes.ts`: lazy-loaded route definition.
- [x] 10.3 Register playground route in `frontend/src/app/app.routes.ts`: add `/dev/widget-playground` route, guarded by `isDevMode()` (use `canMatch` guard that returns `isDevMode()`).
- [x] 10.4 Verify playground renders: `cd frontend && ng serve` — navigate to `/dev/widget-playground`, confirm widget loads and configuration panel is functional.

## 11. Final verification

- [x] 11.1 Run `cd frontend && npm run test` — all library and application tests green.
