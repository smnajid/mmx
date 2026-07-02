# trader-order-detail-actions Specification

## Purpose

Client-side policy for which trader actions are visible on the order details screen, derived from order status and assignee identity.

## Requirements

### Requirement: Testable action policy for order details

The order details feature SHALL derive visible trader actions from a dedicated, unit-tested policy based on loaded `OrderDetails` status and the current trader id, rather than scattering equivalent `@if` rules only in the component template.

#### Scenario: RECEIVED order actions

- **WHEN** the loaded order status is RECEIVED
- **THEN** the policy allows assign, cancel, and reject actions for the current trader

#### Scenario: ASSIGNED assignee-only sensitive actions

- **WHEN** the loaded order status is ASSIGNED and `assignedTraderId` equals the current trader
- **THEN** the policy allows unassign, reject, update, and execute

#### Scenario: ASSIGNED non-assignee

- **WHEN** the loaded order status is ASSIGNED and `assignedTraderId` differs from the current trader
- **THEN** the policy allows unassign only (not reject, update, or execute)

### Requirement: UX parity with pre-refactor details screen

Refactoring action visibility MUST NOT remove trader-facing actions that existed before the policy extraction for the same order state and assignee, except where the prior UI incorrectly showed execute/update to non-assignees on ASSIGNED orders.

#### Scenario: Executed order has no assign

- **WHEN** the loaded order status is EXECUTED
- **THEN** the policy does not offer assign, cancel, or reject from the RECEIVED action set

---

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

When the execute form is shown for an ASSIGNED order, the UI SHALL fetch the appropriate order-creation counterparties endpoint for the order's type and dimensions, passing the trader's active session `legalEntityCode`, locate the row matching the order's intake `institutionCode`, and pre-fill the executed rate input with that row's `rate`. The form SHALL display `rateDate` and an **Indicative** indicator when `indicative` is true (same semantics as the PM order-creation wizard). The trader SHALL be able to edit the rate before submit.

#### Scenario: OnCall order pre-fills segment rate

- **WHEN** an ASSIGNED OnCall order has `currency` CHF, `noticePeriod` 48H, `valueDate` 2026-06-30, and `institutionCode` QNB-01, the trader's active scope is legal entity LOC, and the oncall counterparties endpoint returns QNB-01 with rate 2.15, rateDate 2026-06-20, indicative false
- **THEN** the execute form calls `GET /api/v1/order-creation/oncall/counterparties?legalEntityCode=LOC&currency=CHF&noticePeriod=48H&valueDate=2026-06-30`, pre-fills executed rate `2.15`, shows rate date 2026-06-20, and does not show an Indicative badge

#### Scenario: Stale rate shows indicative warning

- **WHEN** the counterparties row for the intake institution has `indicative` true
- **THEN** the execute form shows an Indicative badge near the proposed rate

#### Scenario: Missing rate leaves field empty

- **WHEN** the counterparties response has no row for the order's `institutionCode`
- **THEN** the executed rate input is empty, a short hint explains no rate was found, and the trader may still enter a rate and submit

#### Scenario: Term order uses term counterparties endpoint

- **WHEN** an ASSIGNED Term order has `currency` EUR, `tenor` 3M, and `institutionCode` BNKCO, and the trader's active scope is legal entity LOC
- **THEN** the execute form calls `GET /api/v1/order-creation/term/counterparties?legalEntityCode=LOC&currency=EUR&tenor=3M` and pre-fills from the matching institution row when present

---

### Requirement: Execute form respects PM minimum rate floor

When the order has a non-null `minimumRate` from intake, the execute form SHALL surface that floor near the rate input. Submit validation SHALL reject rates below the floor before calling the API (client-side), consistent with server validation.

#### Scenario: Rate below floor blocked client-side

- **WHEN** the order has `minimumRate` 2.00 and the trader enters executed rate 1.99
- **THEN** the form shows a validation error and does not emit execute
