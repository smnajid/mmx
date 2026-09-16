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

When Portfolio Management intake targets a LegalEntity whose role is TradingClient **whose connected TradingHub is in the same Organisation and deployment (local routing)**, the system SHALL route the order **synchronously inside the intake transaction**: it SHALL create the client-side order in `RECEIVED`, resolve the connected TradingHub, resolve the global account via the `GlobalAccountDirectory`, create the linked hub-side order in `RECEIVED` (hub scope), link the two records by the routing id, and transition the client-side order `RECEIVED → ROUTED` — all before intake returns. At a TradingHub, intake SHALL behave as native desk intake (`RECEIVED`, no routing). A delegated-grant or enabled-set violation, or an unresolved global account, SHALL abort routing and transition the client-side order to `REJECTED` with no hub-side order created. When the connected TradingHub is in a **different Organisation and deployment (remote routing)**, the system SHALL follow the cross-org routing requirements below.

#### Scenario: TradingClient intake routes and returns Routed

- **WHEN** Portfolio Management submits a valid order for TradingClient `PAR` connected to hub `LOC` in the same deployment
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

### Requirement: Cross-org routing transport backbone

When a TradingClient's connected TradingHub is in a different Organisation and deployment (remote routing), the system SHALL exchange routing traffic over a **hybrid transport**: **leg A** (the routing request, client-deployment → hub-deployment) SHALL be a **synchronous REST handshake** giving the client-side order a definitive outcome in bounded time; **leg B** (the outcome return, hub-deployment → client-deployment) SHALL be **asynchronous Kafka** via the existing transactional-outbox + relay. The leg-B topic SHALL be **owned by the hub deployment** and **suffixed with the hub's `OrganisationCode`** (e.g. `mmx.routed-order-outcome.LODH`), and the client deployment SHALL hold a **consume-only ACL** on it. Addressing SHALL **invert**: the hub publishes to its own topic without knowledge of which clients are listening; the client's consumer is pre-subscribed and filters by `originatingLegalEntityCode` matching its own LegalEntities before applying. Local (same-deployment) routing SHALL NOT use either leg; it stays synchronous and in-process.

#### Scenario: Leg A is a synchronous REST handshake

- **WHEN** CGD (Organisation `CGED`) routes an order to LOC (Organisation `LODH`)
- **THEN** CGED's `RemoteRoutingGateway` makes a synchronous REST call to a LODH inbound endpoint and receives an accept or reject response before the client-side order transitions

#### Scenario: Leg B is asynchronous Kafka on an org-suffixed topic

- **WHEN** LODH emits a routed-order outcome for a CGD order
- **THEN** the outcome is published to the LODH-owned topic `mmx.routed-order-outcome.LODH` via the transactional outbox, and CGED consumes it under a consume-only ACL

#### Scenario: The hub producer is blind to consumers

- **WHEN** LODH publishes a leg-B outcome
- **THEN** LODH does not address a specific client deployment; CGED's consumer filters `originatingLegalEntityCode` to select its own orders

#### Scenario: Local routing uses neither leg

- **WHEN** PAR (Organisation `LODH`) routes to LOC (same Organisation, same deployment)
- **THEN** routing is synchronous and in-process; no REST call, no Kafka topic, and no outbox row on `mmx.routed-order-outcome.LODH` is produced for the routing handshake

---

### Requirement: Remote account resolution via ExternalIdentityGateway

For a remote routed order, the client deployment SHALL resolve the hub-side `portfolioNumber` **before** sending the leg-A request, via an `ExternalIdentityGateway` **out-port** that maps `(client LegalEntityCode, client portfolioNumber, hub LegalEntityCode) → hub-side portfolioNumber`. The resolved account SHALL travel **in the leg-A payload**. The hub deployment SHALL NOT revalidate the supplied account — it is trusted as the authoritative resolution for the cross-org path, by the same principle local routing trusts its own `GlobalAccountDirectory`. If the `ExternalIdentityGateway` cannot resolve an account, the client deployment SHALL transition the client-side order to `REJECTED` directly (no round-trip to the hub), with no hub-side order created. The hub deployment SHALL need no account-validation port for the remote path.

#### Scenario: Resolved account travels in the leg-A payload

- **WHEN** CGD resolves account `CGD-LOC-001` via `ExternalIdentityGateway` for a route to LOC
- **THEN** the leg-A request carries `portfolioNumber = CGD-LOC-001` and LODH persists it as the hub-side order's `portfolioNumber` without querying any directory

#### Scenario: Unresolved account rejects client-side directly

- **WHEN** the `ExternalIdentityGateway` cannot resolve an account for `(CGD, CGD-PM-77, LOC)`
- **THEN** the client-side order transitions to `REJECTED`, no leg-A request is sent, and no hub-side order is created

#### Scenario: The hub does not revalidate the supplied account

- **WHEN** LODH receives a leg-A request with a resolved `portfolioNumber`
- **THEN** LODH persists the hub-side order with that `portfolioNumber` and performs no account-existence or account-ownership check

---

### Requirement: Thin remote client reads hub reference data live

For a remote TradingClient, the client deployment SHALL store **zero** hub reference data (no managed currencies, proxy institutions, delegated grants, or term/on-call rates). The client SHALL read currencies, rates, grants, and counterparties **live from the hub deployment** per request via remote-backed adapters over the existing read ports, selected when the connected hub is remote. The hub deployment's in-process grant check at leg-A accept SHALL be the **one true validation**; the client performs no authoritative pre-validation. Delegated grants for a remote client SHALL be mastered and stored in the hub deployment, keyed by the client's `LegalEntityCode`. Proxy indirection SHALL collapse for remote clients: the hub's live read returns hub-native institution codes, and the client renders the `"via {hub}"` display name client-side; the leg-A request carries the hub-native institution code.

#### Scenario: A remote client reads reference data live from the hub

- **WHEN** a CGD ClientRepresentative opens the order-creation form
- **THEN** the form is populated by live reads from LODH (currencies, counterparties, rates), and CGED stores none of that reference data locally

#### Scenario: The hub validates the grant at leg-A accept

- **WHEN** `AcceptRoutedHubOrderUseCase` at LODH receives a leg-A request for CGD
- **THEN** it validates `(institution, currency, tenor|noticePeriod)` against CGD's grant using LODH's own reference data, and a grant violation rejects the request

#### Scenario: Hub-native institution code crosses the boundary

- **WHEN** a CGD order form offers counterparty "BNP via LOC"
- **THEN** the leg-A request carries LOC's hub-native institution code `BNP`, and LODH performs no proxy resolution

---

### Requirement: Cross-boundary correlation and idempotency for remote routing

For a remote routed order, the hub deployment SHALL trust the client-minted `routingId` as its hub-side idempotency key. The hub-side idempotency key SHALL be the composite `(originatingLegalEntityCode, routingId)`, enforced by a **partial unique index** on `(originating_legal_entity_code, routing_id) WHERE originating_legal_entity_code IS NOT NULL` (routed hub-side orders only; local desk orders with null `originating_legal_entity_code` are untouched). When a leg-A retry triggers a unique-index violation, `AcceptRoutedHubOrderUseCase` SHALL catch the violation, resolve to the already-persisted hub-side order, and return the same accept outcome — idempotently. No internal order UUID SHALL cross the organisation boundary; the cross-boundary correlation key on both the leg-B message and the back-office broadcast SHALL be `(originatingLegalEntityCode, routingId)` only.

#### Scenario: A retry resolves to the existing hub-side order

- **WHEN** CGED retries a leg-A request for the same client-side order and the hub-side order already exists
- **THEN** the unique-index violation is caught, the existing hub-side order is returned, and no duplicate is created

#### Scenario: The partial unique index does not affect local desk orders

- **WHEN** a native (non-routed) hub-side order is created with null `originating_legal_entity_code`
- **THEN** the partial unique index does not constrain it

#### Scenario: No internal order UUID crosses the boundary

- **WHEN** the leg-B outcome message and the back-office routing context are inspected
- **THEN** they carry `(originatingLegalEntityCode, routingId)` and do not carry any deployment-internal order UUID across the organisation boundary

---

### Requirement: Remote routed order intake at the hub

The hub deployment SHALL handle an inbound remote routed order via a dedicated `AcceptRoutedHubOrderUseCase`, distinct from the PM `IntakeUseCase`, because a routed inbound order's authority is the **grant** (not the hub's own intake enablement). The use case SHALL: receive the transport-proven `originatingLegalEntityCode`; validate `(institution, currency, tenor|noticePeriod)` against the originating client's grant using the hub's own reference data; on success, create the hub-side order in `RECEIVED` (hub scope), emit a leg-B `ACCEPTED` event in the same transaction (outbox row committed with the hub-side order creation), and return an accept response synchronously; on a grant/currency/tenor validation failure, return a reject response synchronously and create no hub-side order. The use case SHALL NOT resolve the global account (it travels in the payload) and SHALL NOT revalidate the supplied `portfolioNumber`.

#### Scenario: Valid remote order is accepted and creates a hub-side order

- **WHEN** LODH's `AcceptRoutedHubOrderUseCase` receives a valid leg-A request for CGD
- **THEN** a hub-side order owned by `LOC` is created in `RECEIVED`, a leg-B `ACCEPTED` event is committed in the same transaction, and an accept response is returned

#### Scenario: Grant violation rejects at the hub

- **WHEN** LODH's `AcceptRoutedHubOrderUseCase` receives a leg-A request whose `(institution, currency, tenor)` is not granted to CGD
- **THEN** a reject response is returned, no hub-side order is created, and no leg-B outbox row is committed

#### Scenario: ACCEPTED event is committed with hub-side order creation

- **WHEN** a remote order is accepted at LODH
- **THEN** the `ACCEPTED` outbox row and the hub-side order row are committed in the same transaction, so the accept signal is exactly as durable as the hub-side order

---

### Requirement: Remote outcome propagation to the client-side order

For a remote pair (where `originatingLegalEntityCode`'s Organisation ≠ the hub's Organisation), the hub deployment SHALL **bypass** its synchronous client-side propagation: the hub execute/cancel/reject transaction SHALL persist only the hub-side terminal transition and schedule the leg-B outbox row, and SHALL NOT look up or transition the client-side order. The client deployment SHALL apply outcomes via a dedicated `ApplyRemoteOrderOutcomeUseCase` that owns: load-by-`(originatingLegalEntityCode=self, routingId)`, the idempotency check, broken-pair detection, and the apply. The Kafka consumer adapter SHALL be a thin decode-and-call shell. The apply SHALL be **idempotent**: a client order already in the event's expected terminal or non-terminal state → no-op ack (offset advances); a mismatched terminal → error. The existing hub-side `RoutedOrderOutcomePropagationService` SHALL NOT be reused for remote pairs. Local pairs SHALL keep the existing synchronous in-process propagation unchanged.

#### Scenario: Hub-side propagation is bypassed for remote pairs

- **WHEN** the hub trader executes a remote hub-side order (originating org ≠ hub org)
- **THEN** the hub transaction persists only the hub-side `EXECUTED` transition and schedules the leg-B outbox row; it does not look up the client-side order

#### Scenario: Local pairs keep synchronous propagation

- **WHEN** the hub trader executes a local hub-side order (originating org = hub org)
- **THEN** the existing synchronous in-process propagation runs unchanged, setting the client-side order `EXECUTED` in the same transaction

#### Scenario: Duplicate leg-B delivery is a no-op

- **WHEN** a leg-B `EXECUTED` event is redelivered for a client-side order already `EXECUTED`
- **THEN** the apply is a no-op ack and the offset advances without error

---

### Requirement: Silence is never terminal for a remote client-side order

A remote client-side order in `Received` SHALL **never** be auto-terminalized on transport silence. The only closers for `Received` SHALL be: (a) a confirmed accept signal (leg-A response or leg-B `ACCEPTED`) → `Routed`; (b) a confirmed business reject (leg-A HTTP reject) → `Rejected`; (c) a confirmed hub cancel (leg-B `CANCELLED`) → `Cancelled`. Transport silence (lost response, partition, circuit-open) SHALL leave the order in `Received`; the bidirectional-partition residual SHALL be a disaster-recovery / out-of-band-reconciliation concern, not a state transition. The leg-B `ACCEPTED` event SHALL close `Received → Routed` idempotently (no-op if leg A already delivered). For a leg-A transient failure (timeout, 5xx), the `RemoteRoutingGateway` SHALL own indefinite retry with backoff and a circuit-breaker, transparent to the application use case: the order stays `Received`; a sustained circuit-open SHALL emit an operational signal (alert), never a state transition; when the circuit re-closes, retries resume automatically. No new `OrderStatus` value SHALL be introduced; `Received` covers all pre-confirmed-signal conditions.

#### Scenario: A lost leg-A response is closed by leg-B ACCEPTED

- **WHEN** CGED sends a leg-A request, LODH accepts and creates the hub-side order, but the HTTP response is lost
- **THEN** the client-side order stays `Received` until the leg-B `ACCEPTED` event arrives and transitions it to `Routed`

#### Scenario: Leg-B ACCEPTED no-ops when leg A already delivered

- **WHEN** leg A delivered accept (client-side order is `Routed`) and the leg-B `ACCEPTED` event later arrives
- **THEN** the apply is a no-op ack and the order remains `Routed`

#### Scenario: Leg-A transient failure retries without state change

- **WHEN** the leg-A REST call times out and LODH is reachable on retry
- **THEN** the gateway retries with backoff, the order stays `Received`, and a successful retry transitions it to `Routed` or `Rejected`

#### Scenario: Sustained circuit-open emits an ops signal, not a state transition

- **WHEN** LODH is unreachable for a sustained period past the circuit-breaker threshold
- **THEN** the gateway backs off, the order stays `Received`, an operational alert is emitted, and no order status transition occurs

---

### Requirement: Remote routing-failure reject is HTTP-only

A remote routing-failure reject (grant/currency/tenor invalid at the hub's `AcceptRoutedHubOrderUseCase`) creates **no hub-side order** and SHALL ride **no leg-B event** — the accept was refused, so there is no hub-side lifecycle to mirror. The client deployment SHALL transition the client-side order to `Rejected` when it receives the leg-A HTTP reject response. If the HTTP reject response is lost, the `RemoteRoutingGateway` SHALL retry (leg-A idempotency ensures a safe retry); if the response is perpetually lost, the order stays `Received` (disaster-recovery concern), consistent with "silence is never terminal". A trader-reject (the hub trader rejects an **existing** hub-side order) SHALL ride leg-B `REJECTED` — it is always a hub-side order transitioning to terminal, which leg B genuinely mirrors.

#### Scenario: Routing-failure reject closes via HTTP only

- **WHEN** LODH's `AcceptRoutedHubOrderUseCase` rejects a leg-A request for a grant violation
- **THEN** CGED transitions the client-side order to `Rejected` from the HTTP reject response, and no leg-B event is emitted for this reject

#### Scenario: Trader-reject rides leg B

- **WHEN** the hub trader rejects an existing remote hub-side order
- **THEN** a leg-B `REJECTED` event is emitted and CGED applies it to transition the client-side order to `Rejected`

#### Scenario: Lost routing-failure reject response retries

- **WHEN** LODH rejects a leg-A request but the HTTP reject response is lost
- **THEN** the gateway retries the leg-A call; LODH's idempotent `AcceptRoutedHubOrderUseCase` re-evaluates and re-rejects; the order stays `Received` until a retry response arrives

---

### Requirement: Transport-proven identity for cross-org routing

For a leg-A remote routing request, the transport credential SHALL bind to exactly one remote `LegalEntityCode`. The hub's inbound gateway SHALL map credential → `originatingLegalEntityCode` and hand `AcceptRoutedHubOrderUseCase` a **proven** principal; the use case SHALL never trust a payload-claimed identity. The allow-list SHALL be the TradingClient membership (the hub's client list), checked against the proven `originatingLegalEntityCode`. The gateway SHALL early-reject unknown credentials; the use case SHALL re-check membership as a domain rule. For leg B, the broker consume-only ACL SHALL be the authorisation (the client deployment may consume only the hub's org-suffixed topic).

#### Scenario: Credential binds to one legal entity

- **WHEN** CGED's `RemoteRoutingGateway` calls LODH's inbound endpoint
- **THEN** LODH's gateway derives `originatingLegalEntityCode = CGD` from the transport credential, not from the request payload

#### Scenario: Unknown credential is rejected at the gateway

- **WHEN** a leg-A request arrives with a credential that does not map to a known remote LegalEntity
- **THEN** the gateway rejects the request before it reaches `AcceptRoutedHubOrderUseCase`

#### Scenario: Non-member legal entity is rejected at the use case

- **WHEN** `AcceptRoutedHubOrderUseCase` receives a proven `originatingLegalEntityCode` that is not in LOC's TradingClient list
- **THEN** the use case rejects the request as a domain rule violation

