# order-routing Delta — Routed-order pair outcome-propagation

## MODIFIED Requirements

### Requirement: Routing outcome propagation to the client-side order

When the hub trader executes the hub-side order, the system SHALL **synchronously, in the same transaction** transition the linked client-side order `ROUTED → EXECUTED`, copying `executedRate`, `executionTime`, and `dealingReference` from the hub-side order. The client-side order's counterparty SHALL render as the client's delegated-institution display name (e.g. "BNP via LOC"). The hub-side `ASSIGNED` transition SHALL NOT propagate to the client-side order. A hub-side `REJECTED` or `CANCELLED` SHALL propagate to the client-side order as `REJECTED` or `CANCELLED` respectively, **synchronously in the same transaction** as the hub-side terminal transition. A routing failure SHALL transition the client-side order to `REJECTED`.

The hub-side terminal transition and the propagated client-side transition SHALL commit atomically in one transaction across all three outcomes (execute, cancel, reject). If the propagated client-side transition cannot be applied, the hub-side terminal transition SHALL NOT commit.

If the hub-side order is a routed link (`routingId` present and `originatingLegalEntityCode` present) and reaches a terminal outcome, the system SHALL locate the linked client-side order by `routingId`. If no linked client-side order exists, this is a **routed-pair integrity violation** (a breach of the two-linked-records invariant); the system SHALL reject the terminal transition with a `RoutedOrderPairIntegrityException` and SHALL NOT leave the hub-side order in a terminal state while the client-side order remains in a non-terminal state. The system SHALL NOT silently complete a hub-side terminal transition when the linked client-side order is missing.

#### Scenario: Hub execute propagates Executed to the client-side order

- **WHEN** the hub trader executes the hub-side order at rate `2.5`
- **THEN** the client-side order transitions to `EXECUTED` with `executedRate = 2.5`, the same `executionTime` and `dealingReference`, in the same transaction as the hub-side execute

#### Scenario: Hub Assigned does not propagate

- **WHEN** the hub trader assigns the hub-side order (`RECEIVED → ASSIGNED`)
- **THEN** the client-side order remains `ROUTED`

#### Scenario: Hub reject propagates to the client-side order

- **WHEN** the hub trader rejects the hub-side order with a reason
- **THEN** the client-side order transitions to `REJECTED` in the same transaction as the hub-side reject

#### Scenario: Hub cancel propagates to the client-side order

- **WHEN** the hub trader cancels the hub-side order
- **THEN** the client-side order transitions to `CANCELLED` in the same transaction as the hub-side cancel

#### Scenario: Hub terminal transition and client propagation commit atomically

- **WHEN** the hub-side order reaches a terminal outcome (execute, cancel, or reject) for a routed link
- **THEN** the hub-side terminal transition and the propagated client-side transition commit in one transaction, and a failure to apply the client-side transition rolls back the hub-side terminal transition

#### Scenario: Missing client-side order on hub execute is an integrity violation

- **WHEN** the hub trader executes a routed hub-side order and no linked client-side order is found by `routingId`
- **THEN** the system rejects the terminal transition with a `RoutedOrderPairIntegrityException` and does not leave the hub-side order `EXECUTED` while the client-side order is missing

#### Scenario: Missing client-side order on hub cancel is an integrity violation

- **WHEN** the hub trader cancels a routed hub-side order and no linked client-side order is found by `routingId`
- **THEN** the system rejects the terminal transition with a `RoutedOrderPairIntegrityException` and does not leave the hub-side order `CANCELLED` while the client-side order remains `ROUTED`

#### Scenario: Missing client-side order on hub reject is an integrity violation

- **WHEN** the hub trader rejects a routed hub-side order and no linked client-side order is found by `routingId`
- **THEN** the system rejects the terminal transition with a `RoutedOrderPairIntegrityException` and does not leave the hub-side order `REJECTED` while the client-side order remains `ROUTED`

#### Scenario: The system does not silently desync a broken pair

- **WHEN** a routed hub-side order reaches a terminal outcome and its linked client-side order is missing
- **THEN** the system surfaces the integrity violation rather than completing the hub-side terminal transition with the client-side order left in a non-terminal state
