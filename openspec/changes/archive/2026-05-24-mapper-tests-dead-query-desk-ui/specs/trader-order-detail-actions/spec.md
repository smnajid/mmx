# trader-order-detail-actions Specification (delta)

## ADDED Requirements

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
- **THEN** the policy allows unassign only if server rules permit (UI matches prior behaviour: unassign button visibility per existing product rules)

### Requirement: UX parity with pre-refactor details screen

Refactoring action visibility MUST NOT remove or add trader-facing actions relative to the pre-change order details screen for the same order state and assignee.

#### Scenario: Executed order has no assign

- **WHEN** the loaded order status is EXECUTED
- **THEN** the policy does not offer assign, cancel, or reject from the RECEIVED action set
