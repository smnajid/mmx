## MODIFIED Requirements

### Requirement: Order status state machine

The system SHALL recognise `OrderStatus` values `RECEIVED`, `ROUTED`, `ASSIGNED`, `EXECUTED`, `CANCELLED`, and `REJECTED`. `ACCOUNTED` is governed by `back-office-accounting-handoff`. `ROUTED` SHALL apply only to a **client-side order** that has been handed to a TradingHub for execution; a **hub-side order** (and any native desk order) SHALL never use `ROUTED`. Allowed transitions SHALL be:

- `RECEIVED` → `ASSIGNED` (assign)
- `RECEIVED` → `CANCELLED` (cancel)
- `RECEIVED` → `REJECTED` (reject)
- `RECEIVED` → `ROUTED` (route at a TradingClient intake — client-side only)
- `ASSIGNED` → `RECEIVED` (unassign)
- `ASSIGNED` → `EXECUTED` (execute)
- `ASSIGNED` → `REJECTED` (reject)
- `ROUTED` → `EXECUTED` (synchronous propagation from a hub-side execute)
- `ROUTED` → `REJECTED` (routing failure or hub-side reject propagation)
- `ROUTED` → `CANCELLED` (hub-side cancel propagation)

A client-side order SHALL never transition to or from `ASSIGNED` (a TradingClient has no desk). Any other transition MUST be rejected. Routing semantics, the two-record model, and propagation rules are governed by `order-routing`.

#### Scenario: Assign from Received

- **WHEN** a trader assigns an order in `RECEIVED` status
- **THEN** the order transitions to `ASSIGNED` with the trader recorded as assignee

#### Scenario: Disallowed transition rejected

- **WHEN** a client attempts to cancel an order in `ASSIGNED` status
- **THEN** the system rejects the request and the status remains unchanged

#### Scenario: Routed from Received at a TradingClient intake

- **WHEN** a TradingClient's intake routes the order to its TradingHub
- **THEN** the client-side order transitions `RECEIVED → ROUTED` and a linked hub-side order is created in `RECEIVED`

#### Scenario: Routed order is never Assigned

- **WHEN** a client-side order is in `ROUTED` status
- **THEN** no `ASSIGNED` transition is permitted and no assignee can be recorded

#### Scenario: Hub-side order never uses Routed

- **WHEN** a hub-side order is created via routing
- **THEN** its status is `RECEIVED` and it follows the `RECEIVED → ASSIGNED → EXECUTED` desk path; `ROUTED` is never used for a hub-side order
