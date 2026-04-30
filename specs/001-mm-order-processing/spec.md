# Feature Specification: Money Market Order Processing

**Feature Branch**: `001-mm-order-processing`
**Created**: 2026-04-28
**Status**: Draft
**Input**: Internal banking application that receives Money Market orders from an external Portfolio Management system and supports the Trader operational workflow until execution is confirmed.

## Clarifications

### Session 2026-04-29

- Q: Is Counterparty free text or a selection from a predefined list? → A: Free text — Trader types the Counterparty name manually.
- Q: What is the business distinction between Cancel and Reject? → A: Cancel = order withdrawn (no longer needed); Reject = Trader refuses to process (with reason).
- Q: Can any Trader view the full details of any order regardless of assignment? → A: Yes — any authenticated Trader can view any order's details.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Receive and List Orders (Priority: P1)

The external Portfolio Management system sends Money Market orders to the application. Each order specifies an OrderType (Term or OnCall), an OrderOperation (Subscription, Increase, Decrease, or Redemption), and the required financial details. Once received, orders appear in separate lists for Term and OnCall, visible to all Traders.

**Why this priority**: Without order intake and visibility, no other workflow is possible. This is the entry point for all business value.

**Independent Test**: Can be fully tested by sending order payloads and verifying they appear in the correct list (Term or OnCall) with Received status.

**Acceptance Scenarios**:

1. **Given** the Portfolio Management system submits a valid Term Subscription order, **When** the system processes it, **Then** the order appears in the Received Term orders list with all submitted details.
2. **Given** the Portfolio Management system submits a valid OnCall Subscription order, **When** the system processes it, **Then** the order appears in the Received OnCall orders list.
3. **Given** the Portfolio Management system submits an OnCall Increase referencing an existing ContractNumber, **When** the system processes it, **Then** the order appears in the Received OnCall orders list.
4. **Given** a Term order is submitted with an invalid Tenor value, **When** the system validates it, **Then** the order is rejected with a clear error indicating the invalid field.
5. **Given** the same ExternalOrderReference is submitted twice, **When** the system processes the second submission, **Then** no duplicate order is created and the existing order is returned.

---

### User Story 2 — Assign and Unassign Orders (Priority: P2)

A Trader selects a Received order and assigns it to themselves. This signals that the Trader takes ownership and intends to work the order toward execution. The Trader can also unassign an order before execution, returning it to the Received pool for another Trader to pick up. Each Trader can view a dedicated list of their own assigned orders.

**Why this priority**: Assignment is the gateway to all Trader actions (update, execute). Without it, orders remain unactionable.

**Independent Test**: Can be tested by receiving an order, assigning it, verifying it appears in the Trader's assigned list, then unassigning it and verifying it returns to the Received list.

**Acceptance Scenarios**:

1. **Given** an order in Received status, **When** a Trader assigns it to themselves, **Then** the order moves to Assigned status and appears in that Trader's assigned orders list.
2. **Given** an order assigned to Trader A, **When** Trader A unassigns it, **Then** the order returns to Received status and is visible in the Received list again.
3. **Given** an order assigned to Trader A, **When** Trader B attempts to unassign it, **Then** the system rejects the action because only the assigned Trader can unassign.
4. **Given** an order in Assigned status, **When** another Trader attempts to assign it, **Then** the system rejects the action because the order is no longer in Received status.

---

### User Story 3 — Execute an Order (Priority: P3)

After market dealing happens outside the system, the assigned Trader records the execution outcome. The Trader provides the ExecutedRate and the chosen Counterparty. The system generates a DealingReference, a ContractNumber (for downstream Deposits integration), and records the ExecutionTime automatically. The order then moves to Executed status.

**Why this priority**: Execution is the primary business outcome — it captures the result of market dealing and generates the identifiers needed by downstream systems.

**Independent Test**: Can be tested by receiving an order, assigning it, then executing it with valid data and verifying the order reaches Executed status with all generated references.

**Acceptance Scenarios**:

1. **Given** an order assigned to a Trader, **When** the Trader provides a valid ExecutedRate and Counterparty, **Then** the order moves to Executed status with a system-generated DealingReference, ContractNumber, and ExecutionTime.
2. **Given** an order assigned to a Trader, **When** the Trader attempts to execute without providing ExecutedRate, **Then** the system rejects the execution with a clear error.
3. **Given** an order assigned to a Trader, **When** the Trader attempts to execute without providing Counterparty, **Then** the system rejects the execution.
4. **Given** an order assigned to Trader A, **When** Trader B attempts to execute it, **Then** the system rejects the action because only the assigned Trader can execute.
5. **Given** an order in Received status, **When** any Trader attempts to execute it, **Then** the system rejects the action because execution is only allowed from Assigned status.

---

### User Story 4 — Cancel and Reject Orders (Priority: P4)

A Trader can cancel or reject an order that is still in Received status. Both are terminal states — no further actions are possible. **Cancel** means the order is withdrawn because it is no longer needed (e.g., the portfolio manager no longer requires the placement). **Reject** means the Trader refuses to process the order (e.g., invalid terms, compliance concern, unreasonable conditions) and must provide a reason.

**Why this priority**: Lifecycle completeness requires a way to remove orders that will not proceed to execution.

**Independent Test**: Can be tested by receiving an order, then cancelling (or rejecting) it and verifying the terminal status.

**Acceptance Scenarios**:

1. **Given** an order in Received status, **When** a Trader cancels it, **Then** the order moves to Cancelled status.
2. **Given** an order in Received status, **When** a Trader rejects it with a reason, **Then** the order moves to Rejected status and the reason is recorded.
3. **Given** an order in Assigned status, **When** a Trader attempts to cancel it, **Then** the system rejects the action because cancellation is only allowed from Received status.
4. **Given** an order in Executed status, **When** a Trader attempts to reject it, **Then** the system rejects the action because rejection is only allowed from Received status.

---

### User Story 5 — Update an Assigned Order (Priority: P5)

The assigned Trader can modify certain fields of an order before execution: Amount, MinimumRate, ValueDate, and DesiredCounterpartyComment. The system enforces that updated values remain valid (e.g., ValueDate must still be at least two days in the future, Amount must be positive).

**Why this priority**: Allows the Trader to adjust order parameters based on market conditions before dealing. Useful but not blocking for the core workflow.

**Independent Test**: Can be tested by receiving an order, assigning it, updating a field, and verifying the change persists with revalidation.

**Acceptance Scenarios**:

1. **Given** an order assigned to a Trader, **When** the Trader updates the Amount to a new positive value, **Then** the order reflects the new Amount.
2. **Given** an order assigned to a Trader, **When** the Trader updates the ValueDate to a date less than two days in the future, **Then** the system rejects the update.
3. **Given** an order assigned to Trader A, **When** Trader B attempts to update it, **Then** the system rejects the action because only the assigned Trader can update.
4. **Given** an order in Received status, **When** a Trader attempts to update it, **Then** the system rejects the action because updates are only allowed on Assigned orders.

---

### Edge Cases

- What happens when the Portfolio Management system sends an order with an OrderOperation that is not allowed for the given OrderType (e.g., Redemption for a Term order)? The system rejects the order with a validation error specifying the invalid combination.
- What happens when the Portfolio Management system sends an order with a ValueDate in the past? The system rejects the order.
- What happens when the Portfolio Management system sends an order with Amount = 0 or a negative amount? The system rejects the order.
- What happens when a Trader executes an order but the system fails to generate the DealingReference or ContractNumber? The execution fails entirely — no partial state is persisted.
- What happens when two Traders try to assign the same Received order simultaneously? Only one succeeds; the other receives an error indicating the order is no longer in Received status.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST accept Money Market orders from the external Portfolio Management system via a dedicated intake channel.
- **FR-002**: The system MUST validate that the OrderOperation is allowed for the given OrderType. Term orders allow only Subscription. OnCall orders allow Subscription, Increase, Decrease, and Redemption.
- **FR-003**: The system MUST validate that Subscription orders contain: PortfolioNumber, ExternalOrderReference, OrderType, Currency, Amount, ValueDate, MinimumRate, and either Tenor (for Term) or NoticePeriod (for OnCall).
- **FR-004**: The system MUST validate that Increase, Decrease, and Redemption orders reference an existing ContractNumber.
- **FR-005**: The system MUST validate that Tenor values are within the allowed set: 1W, 2W, 1M, 3M, 6M, 1Y.
- **FR-006**: The system MUST validate that NoticePeriod values are within the allowed set: 24H, 48H.
- **FR-007**: The system MUST validate that ValueDate is at least two calendar days in the future. Orders violating this rule MUST be rejected.
- **FR-008**: The system MUST validate that Amount is greater than zero and MinimumRate is greater than or equal to zero.
- **FR-009**: The system MUST use exact decimal handling for all monetary and rate values. Floating-point approximation is forbidden.
- **FR-010**: Order intake MUST be idempotent using ExternalOrderReference. Receiving the same reference twice MUST NOT create a duplicate order.
- **FR-011**: The system MUST display Received Term orders and Received OnCall orders in separate views.
- **FR-012**: A Trader MUST be able to assign a Received order to themselves. An order can be assigned to only one Trader at a time.
- **FR-013**: The assigned Trader MUST be able to unassign an order, returning it to Received status.
- **FR-014**: Only the assigned Trader MUST be able to update or execute an order.
- **FR-015**: A Trader MUST be able to view a list of orders assigned to them.
- **FR-016**: The assigned Trader MUST be able to update Amount, MinimumRate, ValueDate, and DesiredCounterpartyComment on an Assigned order. Updated values MUST pass the same validation rules as initial reception.
- **FR-017**: The assigned Trader MUST be able to execute an order by providing ExecutedRate and Counterparty (free-text input). Execution MUST fail if either is missing or blank.
- **FR-018**: Upon execution, the system MUST generate a DealingReference and a ContractNumber. The system MUST record the ExecutionTime automatically.
- **FR-019**: Cancellation (order withdrawn, no longer needed) and rejection (Trader refuses to process) are allowed only from Received status. Rejection MUST include a reason.
- **FR-020**: All status transitions MUST follow the allowed state machine: Received → Assigned, Received → Cancelled, Received → Rejected, Assigned → Received (unassign), Assigned → Executed. Any other transition MUST be rejected.
- **FR-021**: Every mutating business action MUST produce an audit record capturing who performed it and when.
- **FR-022**: The DesiredCounterpartyComment MAY be provided at order reception as an optional free-text field. Counterparty itself is assigned only during execution.
- **FR-023**: Any authenticated Trader MUST be able to view the full details of any order regardless of its status or assignment. Mutating actions remain restricted to the assigned Trader.

### Key Entities

- **MoneyMarketOrder**: The central business object representing an order. Characterized by its OrderType (Term or OnCall), OrderOperation (Subscription, Increase, Decrease, Redemption), financial details (Currency, Amount, ValueDate, MinimumRate), and lifecycle status (Received, Assigned, Executed, Cancelled, Rejected).
- **Trader**: The internal user who assigns orders to themselves, manages them, and records execution outcomes.
- **Assignment**: The relationship between a Trader and an order. Only one assignment at a time. Created when a Trader assigns an order; cleared on unassignment.
- **ExecutionDetails**: The data captured when a Trader confirms execution: ExecutedRate, Counterparty (free-text name of the financial institution), ExecutionTime (system-recorded), DealingReference (system-generated), and ContractNumber (system-generated).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A Trader can view all Received Term orders within 3 seconds of opening the Term orders screen.
- **SC-002**: A Trader can view all Received OnCall orders within 3 seconds of opening the OnCall orders screen.
- **SC-003**: A Trader can complete the full workflow (view order → assign → execute) in under 2 minutes.
- **SC-004**: Duplicate order submissions from Portfolio Management never create a second order — 100% idempotency.
- **SC-005**: Every mutating action (assign, unassign, update, execute, cancel, reject) produces a traceable audit record with actor identity and timestamp.
- **SC-006**: No invalid status transition is permitted by the system — 100% of disallowed transitions are rejected.
- **SC-007**: All monetary and rate values maintain exact precision throughout the entire lifecycle — no rounding artifacts from intake through execution.
- **SC-008**: An order cannot be executed with incomplete data (missing ExecutedRate or Counterparty) — 100% enforcement.

## Assumptions

- The Portfolio Management system is the only source of orders in V1. Manual order entry is out of scope.
- Market dealing happens entirely outside this application. Execution means recording the outcome, not performing the deal.
- There is a single user role in V1: Trader. Additional roles (Manager, Auditor) are deferred.
- ValueDate "at least two days in the future" means calendar days, not business days.
- The Trader's identity is known to the system for every request, enabling assignment enforcement and audit logging.
- Integration with the downstream Deposits application is modeled as a boundary but not implemented in V1.
- Outbound email communication to counterparties is out of scope for V1.
- The application serves a small number of concurrent Traders (single digits). High-throughput optimization is not a V1 concern.
- DesiredCounterpartyComment is informational only — it does not constrain which Counterparty can be assigned at execution.
