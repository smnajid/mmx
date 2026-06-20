# trader-order-detail-actions Specification (delta)

## REMOVED Requirements

### Requirement: Execute action uses institution picker policy

**Reason**: Institution is set at intake by Portfolio Management per `order-institution-constraints`. Execute is rate-only; the trader MUST NOT change counterparty at execution time.

**Migration**: Execute form shows the locked intake counterparty and accepts only `executedRate`. Remove institution autocomplete and catalog-empty execute gating from the execute form.

### Requirement: Execute form labels use order vocabulary

**Reason**: Superseded by locked-counterparty display — the execute form no longer has an institution picker input.

**Migration**: Counterparty is shown read-only using order vocabulary ("Counterparty") on the detail grid and execute panel.

---

## ADDED Requirements

### Requirement: Execute form shows locked intake counterparty

For ASSIGNED orders where the current trader is the assignee, the execute form SHALL display the intake **counterparty** (institution display name) and **institutionCode** as read-only context. The form SHALL NOT present an institution picker, datalist, or any control that allows changing the counterparty.

#### Scenario: Assignee sees PM-chosen counterparty on execute form

- **WHEN** the loaded order status is ASSIGNED, `assignedTraderId` equals the current trader, and the order has `counterparty` `QNB` and `institutionCode` `QNB-01` from intake
- **THEN** the execute form displays `QNB` as the counterparty and does not offer an institution selection control

#### Scenario: Execute submit is rate-only

- **WHEN** the assignee submits the execute form with executed rate `2.15`
- **THEN** the client POSTs `{ "executedRate": 2.15 }` to `POST /api/v1/orders/{orderId}/execute` with no `institutionCode` in the body

---

### Requirement: Order detail shows intake counterparty before execution

The order details grid SHALL display **counterparty** and **institutionCode** for orders that have intake institution data, including RECEIVED and ASSIGNED statuses — not only after EXECUTED.

#### Scenario: Received order shows PM counterparty

- **WHEN** the trader opens an order in RECEIVED status that was received with `institutionCode` `QNB-01` and `counterparty` `QNB`
- **THEN** the detail grid shows Counterparty `QNB` and the institution code `QNB-01`

---

### Requirement: Execute form proposes indicative executed rate

When the execute form is shown for an ASSIGNED order, the UI SHALL fetch the appropriate order-creation counterparties endpoint for the order's type and dimensions, locate the row matching the order's intake `institutionCode`, and pre-fill the executed rate input with that row's `rate`. The form SHALL display `rateDate` and an **Indicative** indicator when `indicative` is true (same semantics as the PM order-creation wizard). The trader SHALL be able to edit the rate before submit.

#### Scenario: OnCall order pre-fills segment rate

- **WHEN** an ASSIGNED OnCall order has `currency` CHF, `noticePeriod` 48H, `valueDate` 2026-06-30, and `institutionCode` QNB-01, and the oncall counterparties endpoint returns QNB-01 with rate 2.15, rateDate 2026-06-20, indicative false
- **THEN** the execute form pre-fills executed rate `2.15`, shows rate date 2026-06-20, and does not show an Indicative badge

#### Scenario: Stale rate shows indicative warning

- **WHEN** the counterparties row for the intake institution has `indicative` true
- **THEN** the execute form shows an Indicative badge near the proposed rate

#### Scenario: Missing rate leaves field empty

- **WHEN** the counterparties response has no row for the order's `institutionCode`
- **THEN** the executed rate input is empty, a short hint explains no rate was found, and the trader may still enter a rate and submit

#### Scenario: Term order uses term counterparties endpoint

- **WHEN** an ASSIGNED Term order has `currency` EUR, `tenor` 3M, and `institutionCode` BNKCO
- **THEN** the execute form calls `GET /api/v1/order-creation/term/counterparties` with those parameters and pre-fills from the matching institution row when present

---

### Requirement: Execute form respects PM minimum rate floor

When the order has a non-null `minimumRate` from intake, the execute form SHALL surface that floor near the rate input. Submit validation SHALL reject rates below the floor before calling the API (client-side), consistent with server validation.

#### Scenario: Rate below floor blocked client-side

- **WHEN** the order has `minimumRate` 2.00 and the trader enters executed rate 1.99
- **THEN** the form shows a validation error and does not emit execute
