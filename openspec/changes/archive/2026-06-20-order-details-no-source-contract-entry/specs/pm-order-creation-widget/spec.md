# pm-order-creation-widget Specification

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Wizard step 3 — Operation selection

The operation step SHALL call the appropriate operations endpoint (`GET /api/v1/order-creation/{type}/operations?currency={currency}`) and display operations with their minimum amounts, filtered by context so lifecycle operations are only available when a contract is in context:

- **Term** (always): SUBSCRIPTION only.
- **OnCall fresh flow** (no contract shortcut): SUBSCRIPTION only.
- **OnCall contract shortcut**: INCREASE, DECREASE, REDEMPTION only (not SUBSCRIPTION).

This ensures lifecycle operations — which require an existing source contract — are unreachable in any flow where no contract is known.

#### Scenario: Term operations show only SUBSCRIPTION

- **WHEN** the wizard reaches the operation step for Term/EUR
- **THEN** it displays SUBSCRIPTION with the minimum amount from the API response

#### Scenario: OnCall fresh flow shows only SUBSCRIPTION

- **WHEN** the wizard reaches the operation step for OnCall/EUR in the fresh flow (no `contractNumber`)
- **THEN** it displays only SUBSCRIPTION and does NOT display INCREASE, DECREASE, or REDEMPTION

#### Scenario: OnCall operations filtered for lifecycle (contract shortcut)

- **WHEN** the wizard reaches the operation step via the `contractNumber` shortcut
- **THEN** it displays only INCREASE, DECREASE, and REDEMPTION (not SUBSCRIPTION)

### Requirement: Wizard step 6 — Order details (amount, valueDate, minimumRate)

The order details step SHALL present form fields for `amount` (required), `valueDate` (required), and `minimumRate` (optional), and SHALL NOT present a source contract number field for any operation. Client-side validation SHALL enforce: amount >= minimum amount from step 3, valueDate >= today + 2 calendar days.

#### Scenario: Amount below minimum is rejected

- **WHEN** the user enters amount 100000 but the operation's minAmount is 500000
- **THEN** the form shows a validation error and prevents advancing

#### Scenario: ValueDate too soon is rejected

- **WHEN** the user enters a valueDate that is less than 2 calendar days in the future
- **THEN** the form shows a validation error indicating the minimum settlement delay

#### Scenario: MinimumRate is optional

- **WHEN** the user leaves the minimumRate field empty
- **THEN** the form is valid and the wizard can proceed without a minimumRate

#### Scenario: Lifecycle operation shows no source contract field

- **WHEN** the operation is INCREASE, DECREASE, or REDEMPTION (reached via the contract shortcut)
- **THEN** the order details step shows only amount, valueDate, and minimumRate — no source contract number field

### Requirement: Widget emits structured order payload on completion

The widget SHALL emit an `orderReady` output event containing an `OrderCreationPayload` when the user confirms the order in the review step. The payload SHALL include: `portfolioNumber`, `orderType`, `currency`, `operation`, `tenor` (Term only), `noticePeriod` (OnCall only), `institutionCode`, `counterparty` (display name), `amount`, `valueDate`, and optionally `minimumRate`. `sourceContractNumber` SHALL be included only when the wizard was entered via the contract-number shortcut, and SHALL equal the resolved contract number.

#### Scenario: Completing a Term Subscription emits full payload

- **WHEN** the user completes all wizard steps for a Term Subscription with portfolioNumber PF-001, currency EUR, tenor 3M, institution BNKCO, amount 1000000, valueDate 2026-06-10
- **THEN** the widget emits `orderReady` with `{ portfolioNumber: 'PF-001', orderType: 'TERM', currency: 'EUR', operation: 'SUBSCRIPTION', tenor: '3M', institutionCode: 'BNKCO', counterparty: 'BankCo', amount: 1000000, valueDate: '2026-06-10' }` and no `sourceContractNumber`

#### Scenario: Completing an OnCall Increase emits payload with shortcut-derived sourceContractNumber

- **WHEN** the user enters via the contract shortcut for CT-00042 and completes an OnCall Increase
- **THEN** the widget emits `orderReady` with `sourceContractNumber: 'CT-00042'` included in the payload

#### Scenario: User cancels the wizard

- **WHEN** the user clicks Cancel at any step
- **THEN** the widget emits a `cancelled` output event and does NOT emit `orderReady`

### Requirement: Contract-number shortcut for OnCall lifecycle

When the `contractNumber` input is provided, the widget SHALL call `GET /api/v1/order-creation/oncall/contract-info?contractNumber={contractNumber}` on initialization. On success, it SHALL set orderType to ON_CALL, currency and noticePeriod from the response, store the resolved `sourceContractNumber`, filter operations to INCREASE/DECREASE/REDEMPTION, and start the wizard at the operation step. On 404, it SHALL show an error with a fallback to the normal flow. The resolved `sourceContractNumber` SHALL be preserved when the user changes the selected operation.

#### Scenario: Valid contract number resolves and shortcuts the wizard

- **WHEN** the host provides `[contractNumber]="'CT-00042'"` and the API returns `{ currency: 'EUR', noticePeriod: '24H' }`
- **THEN** the wizard starts at the operation step with orderType=ON_CALL, currency=EUR, noticePeriod=24H, sourceContractNumber=CT-00042, and operations filtered to [INCREASE, DECREASE, REDEMPTION]

#### Scenario: Changing operation preserves the source contract

- **WHEN** the user, in contract-shortcut mode for CT-00042, selects INCREASE and then changes the selection to REDEMPTION
- **THEN** the wizard retains `sourceContractNumber: 'CT-00042'`

#### Scenario: Unknown contract number shows error with fallback

- **WHEN** the host provides `[contractNumber]="'CT-99999'"` and the API returns 404
- **THEN** the widget displays "Contract not found" error with a "Start from scratch" button that resets to the normal full flow
