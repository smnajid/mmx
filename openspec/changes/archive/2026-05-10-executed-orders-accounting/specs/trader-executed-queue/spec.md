## ADDED Requirements

### Requirement: Workspace-scoped Executed list

The system SHALL expose, for each workspace (`OrderType` **Term** or **OnCall**), an Executed list endpoint that returns orders whose `OrderStatus` is `EXECUTED` AND whose `OrderType` matches the active workspace. Orders of any other `OrderStatus` and orders of the other `OrderType` SHALL be excluded.

#### Scenario: Term Executed list returns only EXECUTED Term orders

- **WHEN** the trader requests the **Term** Executed list
- **THEN** every row has `status = EXECUTED` and `orderType = TERM`

#### Scenario: OnCall Executed list returns only EXECUTED OnCall orders

- **WHEN** the trader requests the **OnCall** Executed list
- **THEN** every row has `status = EXECUTED` and `orderType = ON_CALL`

#### Scenario: Cross-workspace orders are excluded

- **WHEN** an `EXECUTED` order has `orderType = ON_CALL`
- **THEN** it does **not** appear in the Term Executed list

#### Scenario: Non-EXECUTED orders are excluded

- **WHEN** a Term order is in `RECEIVED`, `ASSIGNED`, `CANCELLED`, or `REJECTED`
- **THEN** it does **not** appear in the Term Executed list

---

### Requirement: Counterparty visible on Executed list rows

For each row returned by an Executed list endpoint, the response SHALL include the execution `counterparty` string that was recorded when the order was executed. The Executed list response MUST NOT omit counterparty for orders that have execution data.

#### Scenario: Counterparty present on every row

- **WHEN** the Executed list (Term or OnCall) returns at least one row
- **THEN** each row carries a non-null `counterparty` field

#### Scenario: Counterparty matches what was recorded at execution

- **WHEN** an order was executed with counterparty `"Acme Bank"`
- **THEN** that order's row in the Executed list carries `counterparty = "Acme Bank"`

---

### Requirement: Accounted orders leave the Executed list

When an order transitions from `EXECUTED` to `ACCOUNTED`, it SHALL no longer appear in any Executed list response. Other orders in `EXECUTED` SHALL remain in the list.

#### Scenario: Newly accounted order disappears

- **WHEN** an order in the Term Executed list transitions to `ACCOUNTED`
- **THEN** the next request to the Term Executed list does not include it

#### Scenario: Other executed orders remain

- **WHEN** one EXECUTED Term order transitions to `ACCOUNTED` while another remains `EXECUTED`
- **THEN** the next request to the Term Executed list returns the still-`EXECUTED` order and excludes the `ACCOUNTED` one

---

### Requirement: Executed list is read-only

The Executed list endpoints SHALL be query-only and SHALL NOT modify any order's state. No Trader action (assign, unassign, execute, cancel, reject, account) is performed as a side effect of reading the list.

#### Scenario: Reading the list does not change state

- **WHEN** the trader requests an Executed list
- **THEN** every order in the result has the same `status` and `updatedAt` after the request as before
