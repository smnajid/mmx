# pm-order-creation-widget Specification (delta)

## ADDED Requirements

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

## MODIFIED Requirements

### Requirement: Wizard step 4 — Tenor or Notice Period selection

For Term orders, the wizard SHALL call `GET /api/v1/order-creation/term/tenors?currency={currency}` and display available tenors. For OnCall orders, it SHALL call `GET /api/v1/order-creation/oncall/notice-periods?currency={currency}` and display available notice periods. This step is skipped when using the contractNumber shortcut (noticePeriod already resolved). OnCall orders SHALL advance to the **value date** step next (not directly to counterparty).

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

### Requirement: Wizard step 5 — Counterparty selection with rates

The counterparty step SHALL call the appropriate counterparties endpoint and display institutions sorted by best rate. Each entry SHALL show institution display name, rate, rate date, and an indicative warning when the rate is stale. For OnCall, the endpoint SHALL be called with the `valueDate` already collected on the preceding value date step. The counterparty step SHALL NOT prompt for or collect value date.

#### Scenario: Term counterparties with fresh rates

- **WHEN** the wizard calls `GET .../term/counterparties?currency=EUR&tenor=3M`
- **THEN** it displays institutions sorted by rate descending, with today's rates marked as not indicative

#### Scenario: OnCall counterparties use prior value date

- **WHEN** the wizard reaches counterparty selection for OnCall with currency EUR, noticePeriod 48H, and valueDate 2026-06-30 already set
- **THEN** it calls `GET .../oncall/counterparties?currency=EUR&noticePeriod=48H&valueDate=2026-06-30` and displays sorted institutions

#### Scenario: OnCall counterparties with stale rate warning

- **WHEN** an institution's rate has `indicative: true`
- **THEN** the widget displays a visual warning (e.g. icon or label) indicating the rate is from a previous date

#### Scenario: User selects a counterparty

- **WHEN** the user selects institution BNKCO from the list
- **THEN** the wizard stores `institutionCode: 'BNKCO'` and `counterparty: 'BankCo'` (display name) and advances without clearing the previously set valueDate

---

### Requirement: Wizard step 6 — Order details (amount, valueDate, minimumRate)

The order details step SHALL present form fields for `amount` (required) and `minimumRate` (optional). For **Term** orders, it SHALL also present `valueDate` (required) with validation valueDate >= today + 2 calendar days. For **OnCall** orders, it SHALL NOT present an editable value date field — valueDate was set on the preceding value date step and SHALL remain in wizard state through to review. Client-side validation SHALL enforce: amount >= minimum amount from step 3.

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

### Requirement: Widget step navigation shows progress

The wizard SHALL display a progress indicator showing the current step and total steps (based on visible steps for the active order type and flow). The user SHALL be able to navigate back to any previously completed step. Forward navigation beyond the current step SHALL be disabled until the current step is completed.

#### Scenario: OnCall progress includes value date step

- **WHEN** the user is on the value date step in an OnCall full flow
- **THEN** the progress indicator includes a "Value date" step between notice period and counterparty

#### Scenario: User navigates back

- **WHEN** the user clicks on a completed step while on a later step
- **THEN** the wizard navigates back to that step with its previous selection displayed

#### Scenario: Forward navigation is disabled for incomplete steps

- **WHEN** the user is on a step and has not satisfied that step's completion rules
- **THEN** clicking a later step or the "Next" button is disabled
