# pm-order-creation-widget Specification (delta)

## MODIFIED Requirements

### Requirement: Wizard step 5 — Counterparty selection with rates

The counterparty step SHALL call the appropriate counterparties endpoint and display institutions sorted by best rate. Each entry SHALL show institution display name, rate, rate date, and an indicative warning when the rate is stale. For OnCall, the endpoint SHALL be called with the `valueDate` already collected on the preceding value date step. The counterparty step SHALL NOT prompt for or collect value date.

When the wizard is in **contract-number shortcut mode**, the step SHALL display **only** the institution resolved from contract-info (`institutionCode` / `counterparty` from `GET .../oncall/contract-info`). The widget SHALL filter the counterparties API response to that `institutionCode`. Other institutions with rates for the same currency, notice period, and value date SHALL NOT be offered.

For **INCREASE** in shortcut mode, the user SHALL NOT advance unless the counterparties API returns a rate row for the locked institution on the chosen value date (new funds require pricing).

For **DECREASE** and **REDEMPTION** in shortcut mode, the user SHALL always be able to confirm the locked contract counterparty and advance **even when** no rate row exists for that institution on the chosen value date. When a rate is absent, the step SHALL show the locked counterparty without a rate (not an error that blocks the flow). Outflow operations do not price new funds or new contracts.

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

#### Scenario: Contract shortcut outflow emits payload without counterparty rate

- **WHEN** the user completes a DECREASE or REDEMPTION shortcut flow with no rate row for the locked institution
- **THEN** the emitted `orderReady` payload includes `institutionCode` and `counterparty` from contract-info and does not require `counterpartyRate` in wizard state

---

### Requirement: Contract-number shortcut for OnCall lifecycle

When the `contractNumber` input is provided, the widget SHALL call `GET /api/v1/order-creation/oncall/contract-info?contractNumber={contractNumber}` on initialization. On success, it SHALL set orderType to ON_CALL, currency, noticePeriod, **institutionCode**, and **counterparty** from the response, store the resolved `sourceContractNumber`, filter operations to INCREASE/DECREASE/REDEMPTION, and start the wizard at the operation step. On 404, it SHALL show an error with a fallback to the normal flow. The resolved `sourceContractNumber` and locked institution SHALL be preserved when the user changes the selected operation or value date.

#### Scenario: Valid contract number resolves and shortcuts the wizard

- **WHEN** the host provides `[contractNumber]="'CT-00042'"` and the API returns `{ currency: 'EUR', noticePeriod: '24H', institutionCode: 'BNKCO', counterparty: 'BankCo' }`
- **THEN** the wizard starts at the operation step with orderType=ON_CALL, currency=EUR, noticePeriod=24H, locked institution BNKCO, and operations filtered to [INCREASE, DECREASE, REDEMPTION]

#### Scenario: Unknown contract number shows error with fallback

- **WHEN** the host provides `[contractNumber]="'CT-99999'"` and the API returns 404
- **THEN** the widget displays "Contract not found" error with a "Start from scratch" button that resets to the normal full flow

#### Scenario: Locked institution preserved when operation changes in shortcut

- **WHEN** the user is in contract shortcut mode with institutionCode BNKCO from contract-info and changes operation from INCREASE to DECREASE
- **THEN** the wizard retains locked institution BNKCO for counterparty filtering
