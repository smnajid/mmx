# Spec Delta

## MODIFIED Requirements

### Requirement: Intake at a TradingClient routes synchronously to its TradingHub

When Portfolio Management intake targets a LegalEntity whose role is TradingClient **whose connected TradingHub is in the same Organisation and deployment (local routing)**, the system SHALL route the order **synchronously inside the intake transaction**. All of the following SHALL happen before intake returns:

1. create the client-side order in `RECEIVED`;
2. resolve the connected TradingHub;
3. resolve the global account via the `GlobalAccountDirectory`;
4. snapshot the client's counterparty account for the order's OrderType;
5. create the linked hub-side order in `RECEIVED` (hub scope);
6. link the two records by the routing id;
7. transition the client-side order `RECEIVED → ROUTED`.

At a TradingHub, intake SHALL behave as native desk intake (`RECEIVED`, no routing). Any of the following SHALL abort routing and transition the client-side order to `REJECTED`, with no hub-side order created:

- a delegated-grant or enabled-set violation (for Subscription/Increase);
- an institution that is not onboarded, is closed to new business, or whose tenor/notice period is outside the client's **effective enablement** (for Subscription/Increase);
- a missing counterparty account for the order's OrderType, at either the client's onboarded institution or the hub's native institution;
- an unresolved global account.

When the connected TradingHub is in a **different Organisation and deployment (remote routing)**, the system SHALL follow the cross-org routing requirements below.

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

- **WHEN** Portfolio Management submits a Subscription for TradingClient `PAR` whose institution/tenor is not enabled by the delegated grant from `LOC`
- **THEN** intake rejects, the client-side order is `REJECTED`, and no hub-side order is created

#### Scenario: Missing hub counterparty account rejects at intake

- **WHEN** Portfolio Management submits a Term order for `PAR` on `BNP via LOC` and `LOC`'s native `BNP` has no Term counterparty account
- **THEN** intake rejects, the client-side order is `REJECTED` with a routing-failure reason, and no hub-side order is created

---

### Requirement: Routing field mapping from client-side to hub-side order

When creating the hub-side order, the system SHALL map the client-side order's fields as follows:

| Client-side field | Hub-side field |
|---|---|
| `portfolioNumber` | the resolved global account |
| counterparty (institution) | the TradingHub's **native** institution linked to the client's onboarded institution |
| `currency`, `amount`, `valueDate`, `orderType`, `orderOperation`, `tenor` or `noticePeriod`, `minimumRate` | preserved unchanged |

The following client-side values SHALL be stored read-only on the hub-side order as a trace:

- `externalOrderReference`, as `originatingExternalOrderReference`;
- `LegalEntityCode`, as `originatingLegalEntityCode`;
- the client's counterparty account for the order's OrderType, taken at routing, as `clientCounterpartyAccount`.

These values SHALL NOT serve as the hub-side order's intake idempotency key; the routing id is the hub-side idempotency key. A later change to the client's counterparty account SHALL NOT alter `clientCounterpartyAccount` on an existing hub-side order.

#### Scenario: Counterparty maps to the hub's native institution

- **WHEN** a client-side order for `PAR` names the onboarded institution "BNP via LOC"
- **THEN** the hub-side order's counterparty is `LOC`'s native institution `BNP`

#### Scenario: PortfolioNumber maps to the global account

- **WHEN** a client-side order carries `portfolioNumber` `PAR-PM-77`
- **THEN** the hub-side order carries the resolved global account (e.g. `PAR-EUR-001`) as its `portfolioNumber`, not `PAR-PM-77`

#### Scenario: Originating reference is a trace, not the hub idempotency key

- **WHEN** the hub-side order is inspected
- **THEN** it carries `originatingExternalOrderReference` and `originatingLegalEntityCode` from the client-side order, and its intake idempotency key is the `routingId`

#### Scenario: Client counterparty account is snapshotted at routing

- **WHEN** a `PAR` OnCall order on `BNP via LOC` is routed while `PAR`'s OnCall counterparty account for it is `PAR-BNP-OC`, and the account is later changed to `PAR-BNP-OC2`
- **THEN** the hub-side order keeps `clientCounterpartyAccount` `PAR-BNP-OC`

---

### Requirement: A routed trade emits a single execution event from the hub-side order

For a routed trade, the system SHALL emit **exactly one** `OrderExecutedV1`, from the **hub-side order**. It SHALL carry the hub-side booking facts, including the hub's counterparty account snapshot. It SHALL also carry a routing-context block: `routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`, `clientCounterpartyAccount`. With these, the back office can build both the hub-side and the originating client-side contracts, each against its own LegalEntity's counterparty account, without reading any MMX routing table. The client-side order's `EXECUTED` transition SHALL NOT emit its own back-office event. For a native (non-routed) order, the routing-context block SHALL be absent and behaviour is unchanged.

#### Scenario: One routed event carries routing context

- **WHEN** the hub trader executes a routed hub-side order
- **THEN** exactly one `OrderExecutedV1` is emitted from the hub-side order and its body includes the routing-context block matching the link, including `clientCounterpartyAccount` from the hub-side order

#### Scenario: Client-side Executed emits no back-office event

- **WHEN** the client-side order transitions to `EXECUTED` via propagation
- **THEN** no `OrderExecutedV1` is emitted for the client-side order

---

### Requirement: Thin remote client reads hub reference data live

For a remote TradingClient, the client deployment SHALL store **no hub-owned reference data**: no managed currencies, no delegated grants, and no term/on-call rates. It SHALL read currencies, rates, and grants **live from the hub deployment** per request, via remote-backed adapters over the existing read ports, selected when the connected hub is remote. The client deployment SHALL store its own **onboarded institutions** and their counterparty accounts (ADR 0008), each linked to a hub-native institution code. These are client-owned facts, not copied hub data. Delegated grants for a remote client SHALL be mastered and stored in the hub deployment, keyed by the client's `LegalEntityCode`.

- **At the client:** the client SHALL validate what it owns before sending leg A: that the institution is onboarded, open to new business, and the tenor/notice period is within its **client enablement** (for Subscription/Increase), and that its counterparty account for the order's OrderType exists. A failure SHALL reject synchronously as a routing failure. For Increase, Decrease and Redemption the client SHALL also apply the lifecycle rule (the institution matches the source contract's onboarded institution), because the hub cannot check it.
- **At the hub:** the hub deployment's in-process grant check at leg-A accept SHALL remain the **one true validation** of the grant.
- **On the wire:** the leg-A request SHALL carry the hub-native institution code linked to the onboarded institution.

#### Scenario: A remote client reads reference data live from the hub

- **WHEN** a CGD ClientRepresentative opens the order-creation form
- **THEN** currencies, rates, and grants are read live from LODH, and counterparties are CGD's own onboarded institutions filtered by those live grants

#### Scenario: The hub validates the grant at leg-A accept

- **WHEN** `AcceptRoutedHubOrderUseCase` at LODH receives a leg-A Subscription request for CGD
- **THEN** it validates `(institution, currency, tenor|noticePeriod)` against CGD's grant using LODH's own reference data, and a grant violation rejects the request

#### Scenario: Hub-native institution code crosses the boundary

- **WHEN** a CGD order is placed on onboarded "BNP via LOC"
- **THEN** the leg-A request carries LOC's hub-native institution code `BNP`

#### Scenario: Remote client refuses an order on a non-onboarded institution

- **WHEN** CGD holds a grant for `SG` at `LOC` but has not onboarded `SG`, and Portfolio Management submits a CGD order on `SG`
- **THEN** the client-side order is `REJECTED` with a routing-failure reason and no leg-A request is sent

---

### Requirement: Remote routed order intake at the hub

The hub deployment SHALL handle an inbound remote routed order via a dedicated `AcceptRoutedHubOrderUseCase`, distinct from the PM `IntakeUseCase`. The reason is that a routed inbound order's authority is the **grant**, not the hub's own intake enablement. The use case SHALL:

1. receive the transport-proven `originatingLegalEntityCode` and the `clientCounterpartyAccount` in the payload;
2. for a **Subscription** or **Increase**, validate `(institution, currency, tenor|noticePeriod)` against the originating client's active grant using the hub's own reference data, and reject if the hub institution is closed to new business. For a **Decrease** or **Redemption**, skip the grant's active state and enabled set and the closed-to-new-business check, but reject when no grant `(institution, client, currency)` exists in any state (the institution was never granted to the client). For every operation, the institution SHALL be one of the hub's own native institutions;
3. for every operation, reject if the hub's native institution has no counterparty account for the order's OrderType;
4. on success:
   - create the hub-side order in `RECEIVED` (hub scope), storing `clientCounterpartyAccount` read-only;
   - emit a leg-B `ACCEPTED` event in the same transaction (the outbox row is committed with the hub-side order creation);
   - return an accept response synchronously;
5. on any validation failure, return a reject response synchronously and create no hub-side order.

The use case SHALL NOT resolve the global account (it travels in the payload), SHALL NOT revalidate the supplied `portfolioNumber`, and SHALL NOT validate `clientCounterpartyAccount` beyond its presence.

#### Scenario: Valid remote order is accepted and creates a hub-side order

- **WHEN** LODH's `AcceptRoutedHubOrderUseCase` receives a valid leg-A request for CGD
- **THEN** a hub-side order owned by `LOC` is created in `RECEIVED` with the supplied `clientCounterpartyAccount`, a leg-B `ACCEPTED` event is committed in the same transaction, and an accept response is returned

#### Scenario: Grant violation rejects at the hub

- **WHEN** LODH's `AcceptRoutedHubOrderUseCase` receives a leg-A Subscription request whose `(institution, currency, tenor)` is not granted to CGD
- **THEN** a reject response is returned, no hub-side order is created, and no leg-B outbox row is committed

#### Scenario: Redemption on a revoked grant is accepted at the hub

- **WHEN** CGD's grant `(BNP, CGD, EUR)` is inactive and LODH receives a leg-A OnCall Redemption for an existing `EUR` contract on `BNP`
- **THEN** the request is accepted and a hub-side order is created

#### Scenario: Missing hub counterparty account rejects at the hub

- **WHEN** LODH receives a valid leg-A Term request for CGD on `BNP` and `LOC`'s `BNP` has no Term counterparty account
- **THEN** a reject response is returned and no hub-side order is created

#### Scenario: ACCEPTED event is committed with hub-side order creation

- **WHEN** a remote order is accepted at LODH
- **THEN** the `ACCEPTED` outbox row and the hub-side order row are committed in the same transaction, so the accept signal is exactly as durable as the hub-side order
