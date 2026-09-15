# pm-order-creation-widget Specification

## Purpose

A standalone Angular wizard, embeddable in a PM application, for creating Term and On-Call money-market orders: typed configuration inputs, guided step-wise capture (order type, currency, operation, tenor or notice period, counterparty with rates, order details), review and confirm, structured `orderReady` payload emission to the mmx backend, inline error UI, and a playground for development and demo.

## Requirements
### Requirement: Widget exposes typed Angular inputs for configuration

The `OrderCreationWizardComponent` SHALL accept the following Angular inputs:
- `apiBaseUrl` (required, string): base URL of the mmx backend
- `portfolioNumber` (required, string): portfolio identifier from the PM application — included in the output payload and displayed in the review step
- `legalEntityCode` (required, string): exactly 3 characters — the LegalEntity the order belongs to; included in the output payload and passed to counterparty option APIs
- `orderType` (optional, `'TERM' | 'ON_CALL'`): pre-selects the order type and skips step 1
- `contractNumber` (optional, string): triggers the OnCall lifecycle shortcut
- `theme` (optional, `'light' | 'dark'`, default `'light'`): controls widget color scheme

The widget SHALL keep `legalEntityCode` in sync with the input for the lifetime of the component instance (including when the host changes the binding without remounting).

#### Scenario: Widget renders with required inputs

- **WHEN** the host renders `<mmx-order-creation-wizard [apiBaseUrl]="'http://localhost:8080'" [portfolioNumber]="'PF-001'" [legalEntityCode]="'LOC'">`
- **THEN** the widget initializes and displays the first wizard step (order type selection)

#### Scenario: Widget without legalEntityCode shows configuration error

- **WHEN** the host renders the widget without providing `legalEntityCode` or with a value that is not exactly 3 characters
- **THEN** the widget displays an error indicating `legalEntityCode` is required or invalid

#### Scenario: legalEntityCode input change updates host config without remount

- **WHEN** the host changes the bound `legalEntityCode` from LOC to PAR while the wizard instance remains mounted
- **THEN** subsequent counterparty API calls and the emitted `orderReady` payload use `legalEntityCode` PAR

#### Scenario: Widget with pre-selected orderType skips step 1

- **WHEN** the host provides `[orderType]="'TERM'"`
- **THEN** the wizard starts at the currency selection step with orderType already set to TERM

#### Scenario: Widget without required inputs shows configuration error

- **WHEN** the host renders the widget without providing apiBaseUrl or portfolioNumber
- **THEN** the widget displays an error indicating the required configuration is missing

---

### Requirement: Widget emits structured order payload on completion

The widget SHALL emit an `orderReady` output event containing an `OrderCreationPayload` when the user confirms the order in the review step. The payload SHALL include: `legalEntityCode`, `portfolioNumber`, `orderType`, `currency`, `operation`, `tenor` (Term only), `noticePeriod` (OnCall only), `institutionCode`, `counterparty` (display name), `amount`, `valueDate`, and optionally `minimumRate`. `sourceContractNumber` SHALL be included only when the wizard was entered via the contract-number shortcut, and SHALL equal the resolved contract number. `legalEntityCode` SHALL match the host's configured `legalEntityCode` input at confirm time.

#### Scenario: Completing a Term Subscription emits full payload

- **WHEN** the user completes all wizard steps for a Term Subscription with legalEntityCode LOC, portfolioNumber PF-001, currency EUR, tenor 3M, institution BNKCO, amount 1000000, valueDate 2026-06-10
- **THEN** the widget emits `orderReady` with `{ legalEntityCode: 'LOC', portfolioNumber: 'PF-001', orderType: 'TERM', currency: 'EUR', operation: 'SUBSCRIPTION', tenor: '3M', institutionCode: 'BNKCO', counterparty: 'BankCo', amount: 1000000, valueDate: '2026-06-10' }` and no `sourceContractNumber`

#### Scenario: Completing an OnCall Increase emits payload with shortcut-derived sourceContractNumber

- **WHEN** the user enters via the contract shortcut for CT-00042 and completes an OnCall Increase
- **THEN** the widget emits `orderReady` with `sourceContractNumber: 'CT-00042'` included in the payload

#### Scenario: User cancels the wizard

- **WHEN** the user clicks Cancel at any step
- **THEN** the widget emits a `cancelled` output event and does NOT emit `orderReady`

---

### Requirement: Wizard step 1 — Order Type selection

When `orderType` input is not provided, the wizard SHALL display a selection between Term and OnCall as the first step.

#### Scenario: User selects Term

- **WHEN** the wizard shows the order type step and the user selects Term
- **THEN** the wizard advances to the currency step and sets orderType to TERM

#### Scenario: User selects OnCall

- **WHEN** the wizard shows the order type step and the user selects OnCall
- **THEN** the wizard advances to the currency step and sets orderType to ON_CALL

---

### Requirement: Wizard step 2 — Currency selection from API

The currency step SHALL call the appropriate API endpoint based on orderType (`GET /api/v1/order-creation/term/currencies` or `GET /api/v1/order-creation/oncall/currencies`) and display the available currencies. Only currencies with at least one viable counterparty SHALL be shown (as guaranteed by the API).

#### Scenario: Currencies loaded and displayed for Term

- **WHEN** the wizard reaches the currency step with orderType TERM
- **THEN** it calls `GET {apiBaseUrl}/api/v1/order-creation/term/currencies` and displays the returned currencies

#### Scenario: Currencies loaded for OnCall

- **WHEN** the wizard reaches the currency step with orderType ON_CALL
- **THEN** it calls `GET {apiBaseUrl}/api/v1/order-creation/oncall/currencies` and displays the returned currencies

#### Scenario: No currencies available

- **WHEN** the API returns an empty currency list
- **THEN** the wizard displays a message indicating no currencies are currently available for order creation

---

### Requirement: Wizard step 3 — Operation selection

The operation step SHALL call the appropriate operations endpoint (`GET /api/v1/order-creation/{type}/operations?currency={currency}`) and display available operations with their minimum amounts.

#### Scenario: Term operations show only SUBSCRIPTION

- **WHEN** the wizard reaches the operation step for Term/EUR
- **THEN** it displays SUBSCRIPTION with the minimum amount from the API response

#### Scenario: OnCall operations show only SUBSCRIPTION in fresh flow

- **WHEN** the wizard reaches the operation step for OnCall/EUR in the normal flow (no contractNumber)
- **THEN** it displays only SUBSCRIPTION with the minimum amount from the API response (not INCREASE, DECREASE, or REDEMPTION)

#### Scenario: OnCall operations filtered for lifecycle (contract shortcut)

- **WHEN** the wizard reaches the operation step via the contractNumber shortcut
- **THEN** it displays only INCREASE, DECREASE, and REDEMPTION (not SUBSCRIPTION)

---

### Requirement: Wizard step 4 — Tenor or Notice Period selection

For Term orders, the wizard SHALL call `GET /api/v1/order-creation/term/tenors?currency={currency}` and display available tenors. For OnCall orders, it SHALL call `GET /api/v1/order-creation/oncall/notice-periods?currency={currency}` and display available notice periods. This step is skipped when using the contractNumber shortcut (noticePeriod already resolved).

#### Scenario: Term tenors displayed

- **WHEN** the wizard reaches step 4 with orderType TERM and currency EUR
- **THEN** it calls the tenors endpoint and displays the returned tenors (e.g. 1M, 3M, 6M)

#### Scenario: OnCall notice periods displayed

- **WHEN** the wizard reaches step 4 with orderType ON_CALL and currency EUR
- **THEN** it calls the notice-periods endpoint and displays the returned periods (e.g. 24H, 48H)

#### Scenario: Step skipped for contract shortcut

- **WHEN** the wizard is in contract-number shortcut mode (noticePeriod already resolved)
- **THEN** step 4 is skipped and the wizard proceeds to operation selection, then value date, then counterparty

---

### Requirement: Wizard step — OnCall value date (before counterparty)

For OnCall orders (full flow and contract-number shortcut), the wizard SHALL include a dedicated **value date** step after notice period (or after operation in shortcut mode) and **before** counterparty selection. The step SHALL collect a single `valueDate` with client-side validation: valueDate >= today + 2 calendar days. The step SHALL NOT appear for Term orders. Completing the step SHALL persist `valueDate` in wizard state and advance to counterparty selection.

#### Scenario: OnCall full flow shows value date step after notice period

- **WHEN** the user completes notice period selection for an OnCall order
- **THEN** the wizard advances to the value date step (not directly to counterparty)

#### Scenario: Value date too soon is rejected on OnCall value date step

- **WHEN** the user enters a valueDate that is less than 2 calendar days in the future on the OnCall value date step
- **THEN** the form shows a validation error and prevents advancing

#### Scenario: Contract shortcut includes value date before counterparty

- **WHEN** the wizard is in contract-number shortcut mode and the user completes operation selection
- **THEN** the wizard advances to the value date step before counterparty selection

#### Scenario: Term flow skips value date step

- **WHEN** the user completes tenor selection for a Term order
- **THEN** the wizard advances directly to counterparty selection with no value date step

---

### Requirement: Wizard step 5 — Counterparty selection with rates

The counterparty step SHALL call the appropriate counterparties endpoint and display institutions sorted by best rate. Each call SHALL include the host's `legalEntityCode`. Each entry SHALL show institution display name, rate, rate date, and an indicative warning when the rate is stale. For OnCall, the endpoint SHALL be called with the `valueDate` already collected on the preceding value date step. The counterparty step SHALL NOT prompt for or collect value date.

For a **TradingClient** `legalEntityCode`, the API returns only thin-proxy institutions with an active delegated grant (see `pm-order-creation-options`). The widget SHALL display exactly what the API returns (no additional hub-native institutions).

When the wizard is in **contract-number shortcut mode**, the step SHALL display **only** the institution resolved from contract-info (`institutionCode` / `counterparty` from `GET .../oncall/contract-info`). The widget SHALL filter the counterparties API response to that `institutionCode`. Other institutions with rates for the same currency, notice period, and value date SHALL NOT be offered.

For **INCREASE** in shortcut mode, the user SHALL NOT advance unless the counterparties API returns a rate row for the locked institution on the chosen value date (new funds require pricing).

For **DECREASE** and **REDEMPTION** in shortcut mode, the user SHALL always be able to confirm the locked contract counterparty and advance **even when** no rate row exists for that institution on the chosen value date. When a rate is absent, the step SHALL show the locked counterparty without a rate (not an error that blocks the flow). Outflow operations do not price new funds or new contracts.

#### Scenario: Term counterparties with fresh rates

- **WHEN** the wizard calls `GET .../term/counterparties?legalEntityCode=LOC&currency=EUR&tenor=3M`
- **THEN** it displays institutions sorted by rate descending, with today's rates marked as not indicative

#### Scenario: OnCall counterparties use prior value date

- **WHEN** the wizard reaches counterparty selection for OnCall with currency EUR, noticePeriod 48H, and valueDate 2026-06-30 already set
- **THEN** it calls `GET .../oncall/counterparties?legalEntityCode={legalEntityCode}&currency=EUR&noticePeriod=48H&valueDate=2026-06-30` and displays sorted institutions

#### Scenario: OnCall counterparties with stale rate warning

- **WHEN** an institution's rate has `indicative: true`
- **THEN** the widget displays a visual warning (e.g. icon or label) indicating the rate is from a previous date

#### Scenario: User selects a counterparty

- **WHEN** the user selects institution BNKCO from the list
- **THEN** the wizard stores `institutionCode: 'BNKCO'` and `counterparty: 'BankCo'` (display name) and advances without clearing the previously set valueDate

#### Scenario: Contract shortcut shows only the contract institution

- **WHEN** the wizard is in contract shortcut mode, contract-info returned institutionCode BNKCO, and the counterparties API returns BNKCO and SGFR for the chosen value date
- **THEN** the counterparty step displays only BNKCO (not SGFR)

#### Scenario: Contract shortcut INCREASE blocked without rate for locked institution

- **WHEN** the wizard is in contract shortcut mode, operation INCREASE, locked institution BNKCO, and the counterparties API returns no row for BNKCO on the chosen value date
- **THEN** the step does not allow advancing to order details until a rate exists or the user changes value date

#### Scenario: Contract shortcut DECREASE proceeds without rate for locked institution

- **WHEN** the wizard is in contract shortcut mode, operation DECREASE, locked institution BNKCO, and the counterparties API returns no row for BNKCO on the chosen value date
- **THEN** the step displays BNKCO as the locked counterparty without a rate and allows the user to continue

#### Scenario: Contract shortcut REDEMPTION proceeds without rate for locked institution

- **WHEN** the wizard is in contract shortcut mode, operation REDEMPTION, locked institution BNKCO, and the counterparties API returns no row for BNKCO on the chosen value date
- **THEN** the step displays BNKCO as the locked counterparty without a rate and allows the user to continue

#### Scenario: TradingClient counterparty list is grant-scoped

- **WHEN** the host provides `legalEntityCode` PAR (a TradingClient), only BNP has an active delegated grant and onboarded thin-proxy for EUR/3M, and hub institutions BNKCO and SGFR also have EUR/3M rates
- **THEN** the counterparty step displays only the BNP thin-proxy (e.g. institutionCode `BNPLOC`, displayName `BNP Paribas via LOC`) and does not offer BNKCO or SGFR

---

### Requirement: Wizard step 6 — Order details (amount, valueDate, minimumRate)

The order details step SHALL present form fields for `amount` (required) and `minimumRate` (optional). For **Term** orders, it SHALL also present `valueDate` (required) with validation valueDate >= today + 2 calendar days. For **OnCall** orders, it SHALL NOT present an editable value date field — valueDate was set on the preceding value date step and SHALL remain in wizard state through to review. The step SHALL NOT present a source contract number field for any operation. Client-side validation SHALL enforce: amount >= minimum amount from step 3.

#### Scenario: Amount below minimum is rejected

- **WHEN** the user enters amount 100000 but the operation's minAmount is 500000
- **THEN** the form shows a validation error and prevents advancing

#### Scenario: Term ValueDate too soon is rejected

- **WHEN** the user enters a valueDate on a Term order that is less than 2 calendar days in the future
- **THEN** the form shows a validation error indicating the minimum settlement delay

#### Scenario: OnCall order details omits value date field

- **WHEN** the user reaches order details for an OnCall order with valueDate 2026-06-30 already set
- **THEN** the form shows amount and optional minimumRate only (no editable value date input)

#### Scenario: MinimumRate is optional

- **WHEN** the user leaves the minimumRate field empty
- **THEN** the form is valid and the wizard can proceed without a minimumRate

---

### Requirement: Source contract number is never user-entered

The widget SHALL NOT present any input that lets the user type or edit a source contract number. The `sourceContractNumber` carried in the emitted `OrderCreationPayload` SHALL originate solely from the contract-number shortcut (`contractNumber` input resolved via `GET /api/v1/order-creation/oncall/contract-info`). For fresh orders without a contract, `sourceContractNumber` SHALL be absent because mmx allocates the contract number at execution.

#### Scenario: No source contract field in order details for any operation

- **WHEN** the user reaches the Order details step for any operation (SUBSCRIPTION or a lifecycle operation)
- **THEN** the step shows no source contract number input and the user cannot type one

#### Scenario: Source contract comes from the shortcut, not the form

- **WHEN** the widget is opened via the contract-number shortcut for contract CT-00042 and the user completes an Increase
- **THEN** the emitted payload's `sourceContractNumber` equals `CT-00042` and was never collected from an order-details field

#### Scenario: Fresh subscription emits no source contract

- **WHEN** the user completes a fresh OnCall or Term Subscription (no contract shortcut)
- **THEN** the emitted payload has no `sourceContractNumber`

---

### Requirement: Wizard step 7 — Review and confirm

The review step SHALL display a summary of all selections made in previous steps. The user SHALL be able to go back to any previous step to modify selections. Clicking "Create Order" SHALL emit the `orderReady` event. Clicking "Cancel" SHALL emit the `cancelled` event.

#### Scenario: Review shows complete summary

- **WHEN** the wizard reaches the review step
- **THEN** it displays portfolioNumber, orderType, currency, operation, tenor/noticePeriod, counterparty (name + rate), amount, valueDate, and minimumRate (if set)

#### Scenario: User goes back to modify

- **WHEN** the user clicks on a previous step in the review
- **THEN** the wizard navigates back to that step with all subsequent selections preserved until changed

#### Scenario: Confirm emits orderReady

- **WHEN** the user clicks "Create Order" on the review step
- **THEN** the widget emits the `orderReady` output with the complete `OrderCreationPayload`

---

### Requirement: Contract-number shortcut for OnCall lifecycle

When the `contractNumber` input is provided, the widget SHALL call `GET /api/v1/order-creation/oncall/contract-info?contractNumber={contractNumber}` on initialization. On success, it SHALL set orderType to ON_CALL, currency, noticePeriod, **institutionCode**, and **counterparty** from the response, store the resolved `sourceContractNumber`, filter operations to INCREASE/DECREASE/REDEMPTION, and start the wizard at the operation step. On 404, it SHALL show an error with a fallback to the normal flow. The resolved `sourceContractNumber` and locked institution SHALL be preserved when the user changes the selected operation or value date.

#### Scenario: Valid contract number resolves and shortcuts the wizard

- **WHEN** the host provides `[contractNumber]="'CT-00042'"` and the API returns `{ currency: 'EUR', noticePeriod: '24H', institutionCode: 'BNKCO', counterparty: 'BankCo' }`
- **THEN** the wizard starts at the operation step with orderType=ON_CALL, currency=EUR, noticePeriod=24H, locked institution BNKCO, sourceContractNumber=CT-00042, and operations filtered to [INCREASE, DECREASE, REDEMPTION]

#### Scenario: Changing operation preserves the source contract

- **WHEN** the user, in contract-shortcut mode for CT-00042, selects INCREASE and then changes the selection to REDEMPTION
- **THEN** the wizard retains `sourceContractNumber: 'CT-00042'` and locked institution BNKCO

#### Scenario: Unknown contract number shows error with fallback

- **WHEN** the host provides `[contractNumber]="'CT-99999'"` and the API returns 404
- **THEN** the widget displays "Contract not found" error with a "Start from scratch" button that resets to the normal full flow

---

### Requirement: Widget displays inline error UI for API failures

When any API call fails (network error, 5xx, timeout), the widget SHALL display an inline error message within the affected step with a "Retry" button. The widget SHALL NOT emit error events to the host — all error handling is self-contained.

#### Scenario: Network error shows retry

- **WHEN** the currencies API call fails due to network error
- **THEN** the widget displays "Unable to load currencies. Please try again." with a Retry button

#### Scenario: Retry succeeds after transient failure

- **WHEN** the user clicks Retry after a failed API call and the retry succeeds
- **THEN** the widget resumes normal operation and displays the data

#### Scenario: API returns 5xx

- **WHEN** any order-creation endpoint returns a 500 error
- **THEN** the widget displays a generic error message with Retry, not the raw server error

---

### Requirement: Widget Playground for development and demo

The mmx trader frontend SHALL include a route at `/dev/widget-playground` (available only in dev mode) that embeds the `OrderCreationWizardComponent` with a configuration panel and event log. The configuration panel SHALL include a **legal entity code** field (exactly 3 characters), portfolio number, optional API base URL, and order type. The widget SHALL mount only after the user clicks **Apply configuration** (no automatic apply on page load). The applied-configuration summary SHALL show the active legal entity code alongside portfolio, API base, order type, and contract (when set). The playground SHALL offer a **live-contract picker** instead of a free-text contract field: the user selects an order type, the playground calls `GET /api/v1/order-creation/contracts?portfolioNumber={pf}&orderType={type}`, and selecting a contract sets the widget's `contractNumber` input (driving the OnCall lifecycle shortcut). Term contracts MAY be listed but are not selectable into the shortcut until Term lifecycle operations exist. Applying configuration SHALL remount the widget so `legalEntityCode` and other inputs take effect.

#### Scenario: Playground is accessible in dev mode

- **WHEN** the mmx frontend is running in development mode
- **THEN** navigating to `/dev/widget-playground` renders the playground with the widget

#### Scenario: Playground is not accessible in production

- **WHEN** the mmx frontend is built for production
- **THEN** the `/dev/widget-playground` route is not registered and returns 404

#### Scenario: Picker lists live contracts and drives the shortcut

- **WHEN** the user selects order type OnCall in the playground and the contracts endpoint returns CT-00042 for the configured portfolio
- **THEN** the picker lists CT-00042, and selecting it mounts the widget with `contractNumber` set to CT-00042 (entering the lifecycle shortcut)

#### Scenario: Configuration panel changes propagate to widget

- **WHEN** the user changes the legal entity code or order type in the playground configuration panel and clicks Apply configuration
- **THEN** the embedded widget remounts with the new `legalEntityCode` and `orderType` inputs

#### Scenario: Playground shows active legal entity in applied summary

- **WHEN** the user sets legal entity code PAR and clicks Apply configuration
- **THEN** the applied-configuration summary includes `legal entity PAR`

#### Scenario: Events are logged in the playground

- **WHEN** the widget emits `orderReady` in the playground
- **THEN** the event payload is displayed as formatted JSON in the event log panel

---

### Requirement: Playground submits orderReady payloads to mmx intake

The widget playground SHALL act as a development PM host: for each logged `orderReady` event, the user SHALL be able to submit the payload to mmx intake via `POST /api/v1/orders` using the playground's configured API base URL (same base as the embedded widget). The playground SHALL map `OrderCreationPayload` to `ReceiveOrderRequest` per the canonical intake contract (`legalEntityCode`, `operation` → `orderOperation`, include `institutionCode`, omit display-only `counterparty`). The playground SHALL generate an `externalOrderReference` at submit time. The widget library itself SHALL NOT perform submission.

#### Scenario: User sends a completed order to intake

- **WHEN** the widget emits `orderReady` in the playground and the user clicks Send to mmx
- **THEN** the playground POSTs a `ReceiveOrderRequest` to `{apiBaseUrl}/api/v1/orders` (or `/api/v1/orders` via dev proxy when api base is empty) and displays the returned `orderId` and HTTP status on success

#### Scenario: Submit preserves legalEntityCode from payload

- **WHEN** the user sends an `orderReady` payload that includes `legalEntityCode: 'PAR'`
- **THEN** the POST body includes `legalEntityCode: 'PAR'`

#### Scenario: Submit uses institutionCode from the payload

- **WHEN** the user sends an `orderReady` payload that includes `institutionCode: 'BNKCO'`
- **THEN** the POST body includes `institutionCode: 'BNKCO'` and does not include `counterparty`

#### Scenario: Submit failure is surfaced

- **WHEN** intake returns `400 Bad Request` (e.g. validation error)
- **THEN** the playground displays the error message in the event log and allows the user to retry Send with a newly generated `externalOrderReference`

#### Scenario: Successful submit disables duplicate send

- **WHEN** intake returns `201 Created` for a playground submit
- **THEN** that event entry shows success with `orderId` and does not offer another Send for the same event

---

### Requirement: Widget is responsive and scrollable

The wizard SHALL adapt to the container width provided by the host. Content SHALL scroll vertically within the wizard when it exceeds the available height. The widget SHALL NOT impose a fixed width or height.

#### Scenario: Widget adapts to narrow container

- **WHEN** the host renders the widget in a 400px wide container
- **THEN** the wizard layout adapts (single column, compact) without horizontal overflow

#### Scenario: Widget adapts to wide container

- **WHEN** the host renders the widget in an 800px+ wide container
- **THEN** the wizard layout uses available space (e.g. wider cards, side-by-side where appropriate)

#### Scenario: Long content scrolls vertically

- **WHEN** the counterparty list exceeds the visible height
- **THEN** the list scrolls vertically within the widget bounds

---

### Requirement: Widget step navigation shows progress

The wizard SHALL display a progress indicator showing the current step and total steps (based on visible steps for the active order type and flow). The user SHALL be able to navigate back to any previously completed step. Forward navigation beyond the current step SHALL be disabled until the current step is completed.

#### Scenario: OnCall progress includes value date step

- **WHEN** the user is on the value date step in an OnCall full flow
- **THEN** the progress indicator includes a "Value date" step between notice period and counterparty

#### Scenario: Progress indicator shows current position

- **WHEN** the user is on step 3 of 7
- **THEN** the progress indicator highlights step 3 and shows steps 1-2 as completed

#### Scenario: User navigates back

- **WHEN** the user clicks on a completed step (step 2) while on step 4
- **THEN** the wizard navigates back to step 2 with its previous selection displayed

#### Scenario: Forward navigation is disabled for incomplete steps

- **WHEN** the user is on step 3 and has not made a selection
- **THEN** clicking step 4 or the "Next" button is disabled

