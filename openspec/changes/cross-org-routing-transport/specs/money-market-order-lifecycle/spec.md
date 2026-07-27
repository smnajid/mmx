## MODIFIED Requirements

### Requirement: Order status state machine

The system SHALL recognise `OrderStatus` values `RECEIVED`, `ROUTED`, `ASSIGNED`, `EXECUTED`, `CANCELLED`, and `REJECTED`. `ACCOUNTED` is governed by `back-office-accounting-handoff`. `ROUTED` SHALL apply only to a **client-side order** that has been handed to a TradingHub for execution; a **hub-side order** (and any native desk order) SHALL never use `ROUTED`. Allowed transitions SHALL be:

- `RECEIVED` → `ASSIGNED` (assign)
- `RECEIVED` → `CANCELLED` (cancel)
- `RECEIVED` → `REJECTED` (reject, including routing failure)
- `RECEIVED` → `ROUTED` (route at a TradingClient intake — client-side only; via synchronous local routing, or via leg-A accept / leg-B `ACCEPTED` for remote routing)
- `ASSIGNED` → `RECEIVED` (unassign)
- `ASSIGNED` → `EXECUTED` (execute)
- `ASSIGNED` → `REJECTED` (reject)
- `ROUTED` → `EXECUTED` (propagation from a hub-side execute — synchronous for local pairs, asynchronous via leg B for remote pairs)
- `ROUTED` → `REJECTED` (routing failure, or hub-side reject propagation — synchronous for local pairs, asynchronous via leg B for remote pairs)
- `ROUTED` → `CANCELLED` (hub-side cancel propagation — synchronous for local pairs, asynchronous via leg B for remote pairs)

A client-side order SHALL never transition to or from `ASSIGNED` (a TradingClient has no desk). Any other transition MUST be rejected. For a **remote** client-side order, `Received` SHALL cover all pre-confirmed-signal conditions (leg A in flight, leg A lost with circuit-breaker open, leg A succeeded at the hub but awaiting leg-B `ACCEPTED`) — these are operational metadata, not distinct domain states. Routing semantics, the two-record model, and propagation rules are governed by `order-routing`. No new `OrderStatus` value SHALL be introduced for remote routing.

#### Scenario: Assign from Received

- **WHEN** a trader assigns an order in `RECEIVED` status
- **THEN** the order transitions to `ASSIGNED` with the trader recorded as assignee

#### Scenario: Disallowed transition rejected

- **WHEN** a client attempts to cancel an order in `ASSIGNED` status
- **THEN** the system rejects the request and the status remains unchanged

#### Scenario: Routed from Received at a TradingClient intake (local)

- **WHEN** a local TradingClient's intake routes the order to its TradingHub in the same deployment
- **THEN** the client-side order transitions `RECEIVED → ROUTED` synchronously and a linked hub-side order is created in `RECEIVED`

#### Scenario: Routed from Received via leg B (remote)

- **WHEN** a remote TradingClient's order is accepted at the hub and the leg-A response is lost
- **THEN** the client-side order transitions `RECEIVED → ROUTED` when the leg-B `ACCEPTED` event arrives

#### Scenario: Remote Received covers in-flight and circuit-open

- **WHEN** a remote client-side order's leg-A request is in flight or the circuit-breaker is open
- **THEN** the order is in `RECEIVED` with no additional status value distinguishing in-flight from awaiting-confirm

#### Scenario: Routed order is never Assigned

- **WHEN** a client-side order is in `ROUTED` status
- **THEN** no `ASSIGNED` transition is permitted and no assignee can be recorded

#### Scenario: Hub-side order never uses Routed

- **WHEN** a hub-side order is created via routing
- **THEN** its status is `RECEIVED` and it follows the `RECEIVED → ASSIGNED → EXECUTED` desk path; `ROUTED` is never used for a hub-side order
