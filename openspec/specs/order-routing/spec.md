# order-routing Specification

## Purpose

Client→hub order routing for TradingClient LegalEntities: two linked order records correlated by a routing id, synchronous routing at intake, `GlobalAccountDirectory` resolution, field mapping to hub-side desk intake, synchronous outcome propagation, a single routed `OrderExecutedV1` from the hub-side order, and per-side accounting callbacks.

## Requirements

### Requirement: A routed order is two linked records correlated by a routing id

When a TradingClient's order is routed to its TradingHub, the system SHALL create **two linked order records**: a **client-side order** owned by the TradingClient's `LegalEntityCode` (keyed by the client PM's `portfolioNumber` and `externalOrderReference`) and a **hub-side order** owned by the TradingHub's `LegalEntityCode` (keyed by the client's **global account** at the hub as `portfolioNumber` and the hub-native institution). The two records SHALL be correlated by a **routing id** generated **deterministically from the client-side order id**, persisted on both records. The hub-side order SHALL be idempotent on the routing id: a repeated routing attempt for the same client-side order SHALL resolve to the same hub-side order and SHALL NOT create a duplicate. The system SHALL NOT model a routed order as a single shared record.

#### Scenario: Routing creates two linked records

- **WHEN** a TradingClient `PAR` order is routed to its TradingHub `LOC`
- **THEN** a client-side order owned by `PAR` and a hub-side order owned by `LOC` are persisted, both carrying the same `routingId` derived from the client-side order id

#### Scenario: Repeated routing does not duplicate the hub-side order

- **WHEN** routing is attempted twice for the same client-side order
- **THEN** the second attempt resolves to the existing hub-side order and no second hub-side order is created

#### Scenario: A routed order is never a single shared record

- **WHEN** the persisted state of a routed trade is inspected
- **THEN** two distinct order rows exist, each scoped to its own `LegalEntityCode`; no single row carries both entities' portfolio or counterparty data

---

### Requirement: Intake at a TradingClient routes synchronously to its TradingHub

When Portfolio Management intake targets a LegalEntity whose role is TradingClient, the system SHALL route the order **synchronously inside the intake transaction**: it SHALL create the client-side order in `RECEIVED`, resolve the connected TradingHub (same Organisation), resolve the global account via the `GlobalAccountDirectory`, create the linked hub-side order in `RECEIVED` (hub scope), link the two records by the routing id, and transition the client-side order `RECEIVED → ROUTED` — all before intake returns. At a TradingHub, intake SHALL behave as native desk intake (`RECEIVED`, no routing). A delegated-grant or enabled-set violation, or an unresolved global account, SHALL abort routing and transition the client-side order to `REJECTED` with no hub-side order created.

#### Scenario: TradingClient intake routes and returns Routed

- **WHEN** Portfolio Management submits a valid order for TradingClient `PAR` connected to hub `LOC`
- **THEN** intake returns success, the client-side order is `ROUTED`, and a linked hub-side order in `RECEIVED` exists on the `LOC` desk in the same transaction

#### Scenario: TradingHub intake is not routed

- **WHEN** Portfolio Management submits a valid order for TradingHub `LOC`
- **THEN** a single native order is persisted in `RECEIVED` owned by `LOC` and no routing link is created

#### Scenario: Unresolved global account rejects at intake

- **WHEN** Portfolio Management submits an order for TradingClient `PAR` and no global account exists for `(PAR, LOC, currency)`
- **THEN** intake rejects, the client-side order is `REJECTED`, and no hub-side order is created

#### Scenario: Grant violation rejects at intake

- **WHEN** Portfolio Management submits an order for TradingClient `PAR` whose institution/tenor is not enabled by the delegated grant from `LOC`
- **THEN** intake rejects, the client-side order is `REJECTED`, and no hub-side order is created

---

### Requirement: GlobalAccountDirectory resolves the client's global account

The system SHALL provide a `GlobalAccountDirectory` **out-port** that resolves the global account reference for a tuple `(client LegalEntityCode, hub LegalEntityCode, currency)`. In V1 the port SHALL be backed by MMX-managed reference data keyed by that tuple. The resolved account SHALL be used as the hub-side order's `portfolioNumber`. If no account is resolvable for the tuple, the port SHALL signal a routing failure.

#### Scenario: Global account resolved per client, hub, and currency

- **WHEN** routing resolves the global account for `(PAR, LOC, EUR)`
- **THEN** the hub-side order's `portfolioNumber` is the resolved account reference for that tuple

#### Scenario: Missing account signals routing failure

- **WHEN** the `GlobalAccountDirectory` is queried for a tuple with no configured account
- **THEN** it signals a routing failure and no hub-side order is created

---

### Requirement: Global account reference data is managed by the TradingHub

A TradingHub SHALL manage the global-account reference data that backs the `GlobalAccountDirectory` for its connected TradingClients: one account reference per `(client LegalEntityCode, hub LegalEntityCode, currency)`. Adding, changing, or removing an entry SHALL NOT retroactively rewrite already-routed or executed orders — historical orders keep the account in effect when they were routed.

#### Scenario: Hub configures a global account for a client and currency

- **WHEN** TradingHub `LOC` configures global account `PAR-EUR-001` for `(PAR, LOC, EUR)`
- **THEN** subsequent routing for `(PAR, LOC, EUR)` resolves to `PAR-EUR-001`

#### Scenario: Reference-data change does not rewrite history

- **WHEN** the global account for `(PAR, LOC, EUR)` is changed from `PAR-EUR-001` to `PAR-EUR-002`
- **THEN** orders already routed or executed with `PAR-EUR-001` are unchanged; only subsequently routed orders use `PAR-EUR-002`

---

### Requirement: Routing field mapping from client-side to hub-side order

When creating the hub-side order, the system SHALL map the client-side order's fields as follows: `portfolioNumber` → the resolved global account; counterparty (institution) → the TradingHub's **native** institution corresponding to the client's delegated-institution proxy; `currency`, `amount`, `valueDate`, `orderType`, `orderOperation`, `tenor` or `noticePeriod`, and `minimumRate` → preserved unchanged. The client-side order's `externalOrderReference` and `LegalEntityCode` SHALL be stored read-only on the hub-side order as `originatingExternalOrderReference` and `originatingLegalEntityCode` (a trace), and SHALL NOT serve as the hub-side order's intake idempotency key (the routing id is the hub-side idempotency key).

#### Scenario: Counterparty maps to the hub's native institution

- **WHEN** a client-side order for `PAR` names the delegated institution proxy "BNP via LOC"
- **THEN** the hub-side order's counterparty is `LOC`'s native institution `BNP`

#### Scenario: PortfolioNumber maps to the global account

- **WHEN** a client-side order carries `portfolioNumber` `PAR-PM-77`
- **THEN** the hub-side order carries the resolved global account (e.g. `PAR-EUR-001`) as its `portfolioNumber`, not `PAR-PM-77`

#### Scenario: Originating reference is a trace, not the hub idempotency key

- **WHEN** the hub-side order is inspected
- **THEN** it carries `originatingExternalOrderReference` and `originatingLegalEntityCode` from the client-side order, and its intake idempotency key is the `routingId`

---

### Requirement: Routing outcome propagation to the client-side order

When the hub trader executes the hub-side order, the system SHALL **synchronously, in the same transaction** transition the linked client-side order `ROUTED → EXECUTED`, copying `executedRate`, `executionTime`, and `dealingReference` from the hub-side order. The client-side order's counterparty SHALL render as the client's delegated-institution display name (e.g. "BNP via LOC"). The hub-side `ASSIGNED` transition SHALL NOT propagate to the client-side order. A hub-side `REJECTED` or `CANCELLED` SHALL propagate to the client-side order as `REJECTED` or `CANCELLED` respectively. A routing failure SHALL transition the client-side order to `REJECTED`.

#### Scenario: Hub execute propagates Executed to the client-side order

- **WHEN** the hub trader executes the hub-side order at rate `2.5`
- **THEN** the client-side order transitions to `EXECUTED` with `executedRate = 2.5`, the same `executionTime` and `dealingReference`, in the same transaction

#### Scenario: Hub Assigned does not propagate

- **WHEN** the hub trader assigns the hub-side order (`RECEIVED → ASSIGNED`)
- **THEN** the client-side order remains `ROUTED`

#### Scenario: Hub reject propagates to the client-side order

- **WHEN** the hub trader rejects the hub-side order with a reason
- **THEN** the client-side order transitions to `REJECTED`

---

### Requirement: A routed trade emits a single execution event from the hub-side order

For a routed trade, the system SHALL emit **exactly one** `OrderExecutedV1` from the **hub-side order**, carrying the hub-side booking facts plus a routing-context block (`routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`) so the back office can build both the hub-side and the originating client-side contracts without reading any MMX routing table. The client-side order's `EXECUTED` transition SHALL NOT emit its own back-office event. For a native (non-routed) order, the routing-context block SHALL be absent and behaviour is unchanged.

#### Scenario: One routed event carries routing context

- **WHEN** the hub trader executes a routed hub-side order
- **THEN** exactly one `OrderExecutedV1` is emitted from the hub-side order and its body includes the routing-context block matching the link

#### Scenario: Client-side Executed emits no back-office event

- **WHEN** the client-side order transitions to `EXECUTED` via propagation
- **THEN** no `OrderExecutedV1` is emitted for the client-side order

---

### Requirement: Per-side accounting for a routed order

A routed trade SHALL be accounted by **two independent** `accounted` callbacks — one for the hub-side `orderId` and one for the client-side `orderId` — each transitioning its own order `EXECUTED → ACCOUNTED` after the corresponding Transactions 2 booking is confirmed. The client-side order SHALL reach `ACCOUNTED` from `EXECUTED` and SHALL never pass through `ASSIGNED`. Accounting one side SHALL NOT require the other side to be accounted.

#### Scenario: Both sides accounted independently

- **WHEN** the back office posts the accounted callback for the hub-side orderId and later for the client-side orderId
- **THEN** each order transitions to `ACCOUNTED` independently of the other

#### Scenario: Client-side order never Assigned

- **WHEN** the lifecycle of a routed client-side order is inspected
- **THEN** it passes `RECEIVED → ROUTED → EXECUTED → ACCOUNTED` and never enters `ASSIGNED`

---

### Requirement: Contract reversal and replace are back-office-internal and do not affect MMX order status

Contract reversal and replace are back-office-internal lifecycle events of the Deposits back office, correlated by the `routingId` captured at contract creation, and broadcast by the back office between the hub and client sides. MMX order status SHALL NOT be affected by reversal or replace; `ACCOUNTED` SHALL remain terminal. MMX SHALL NOT expose a reversal or replace operation on its orders in V1.

#### Scenario: Reversal does not change MMX order status

- **WHEN** the back office reverses a routed contract correlated by `routingId`
- **THEN** the MMX hub-side and client-side orders remain `ACCOUNTED` and no MMX status transition occurs

#### Scenario: MMX exposes no reversal operation

- **WHEN** the MMX Trader and back-office HTTP contracts are inspected
- **THEN** no order reversal or replace operation is present
