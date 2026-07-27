## ADDED Requirements

### Requirement: Rejection origin is classified and persisted

Every order that transitions to `REJECTED` SHALL carry a `RejectionOrigin` of `TRADER` or `ROUTING_FAILURE`, persisted alongside the existing free-text `rejectionReason`. `ROUTING_FAILURE` SHALL denote a failure of the route itself (no hub-side order lifecycle exists, or the route never completed) — set at: a local intake routing failure (unresolved global account, delegated-grant or enabled-set violation); a remote client-side routing failure (unresolved `ExternalIdentityGateway` account); and a remote accept routing failure at the hub's `AcceptRoutedHubOrderUseCase` (grant/currency/tenor violation) that closes the client-side order via the leg-A HTTP reject. `TRADER` SHALL denote a desk decision rejecting an existing order — set at a trader `reject(...)` and at a propagated hub-trader reject (`propagateRejectFromHub`, whether synchronous for a local pair or via a leg-B `REJECTED` event for a remote pair). The system SHALL NOT introduce any additional `RejectionOrigin` value; an unclassified reject is a defect, not a category. A Flyway migration SHALL add a nullable `rejection_origin` column; pre-existing rejected rows SHALL remain `null` (read as "pre-origin-tracking") and SHALL NOT be back-filled to a default value. Every newly-rejected order SHALL set a non-null `RejectionOrigin`.

#### Scenario: Routing failure at local intake records ROUTING_FAILURE

- **WHEN** Portfolio Management submits an order for a local TradingClient whose global account cannot be resolved or whose institution/tenor violates the delegated grant
- **THEN** the client-side order transitions to `REJECTED` with `rejectionOrigin = ROUTING_FAILURE` and a persisted reason

#### Scenario: Remote account unresolved records ROUTING_FAILURE

- **WHEN** the `ExternalIdentityGateway` cannot resolve an account for a remote routed order and the client deployment rejects client-side
- **THEN** the client-side order transitions to `REJECTED` with `rejectionOrigin = ROUTING_FAILURE` and no hub-side order is created

#### Scenario: Remote accept routing failure records ROUTING_FAILURE

- **WHEN** the hub's `AcceptRoutedHubOrderUseCase` rejects a leg-A request for a grant/currency/tenor violation and the client deployment closes the order from the HTTP reject
- **THEN** the client-side order transitions to `REJECTED` with `rejectionOrigin = ROUTING_FAILURE`

#### Scenario: Trader reject records TRADER

- **WHEN** a trader rejects an order from `RECEIVED` or `ASSIGNED` with a reason
- **THEN** the order transitions to `REJECTED` with `rejectionOrigin = TRADER`

#### Scenario: Propagated hub-trader reject records TRADER

- **WHEN** a hub-trader reject propagates to the client-side order (synchronously for a local pair, or via a leg-B `REJECTED` event for a remote pair)
- **THEN** the client-side order transitions to `REJECTED` with `rejectionOrigin = TRADER`

#### Scenario: Historical rejects remain unclassified

- **WHEN** a row rejected before this change is inspected
- **THEN** its `rejection_origin` is `null` (not a silent default) and it remains queryable by `rejectionReason`
