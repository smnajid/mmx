## Why

With tenancy in place (Change A), a **TradingClient** LegalEntity has no desk and must have its orders executed at its connected **TradingHub**. Today MMX has no notion of order routing — every order lands on the single desk. This change introduces **order routing**: a TradingClient's PM intake becomes a **client-side order** plus a linked **hub-side order** on the hub's desk, executed by the hub trader, with the outcome propagated back to the client. It also decouples the Deposits back office from the order DB (ADR-0003): the back office consumes one routed `OrderExecutedV1` carrying routing context and calls the existing `accounted` callback per side — it never reads or writes the MMX order DB, replacing the legacy FiduTrader cross-DB polling.

## What Changes

- **New `Routed` OrderStatus** (client-side only). Client-side lifecycle: `RECEIVED → ROUTED → EXECUTED → ACCOUNTED` (terminal alternatives `REJECTED` / `CANCELLED`); never `ASSIGNED` (a client has no desk).
- **Two linked records per routed order** — a **client-side order** (client LegalEntity scope, client PM portfolioNumber + ExternalOrderReference) and a **hub-side order** (hub LegalEntity scope, **global account** as portfolioNumber, hub-native institution), correlated by a **deterministic routing id** (ADR-0002). Never one shared record.
- **Synchronous routing at intake** — at a TradingClient, the intake use case validates against the delegated grant (Change C), resolves the connected hub, resolves the global account via **GlobalAccountDirectory**, creates both records, links them, and flips the client-side order `RECEIVED → ROUTED` in one transaction, before intake returns. At a TradingHub, intake is unchanged (desk `RECEIVED`).
- **GlobalAccountDirectory port** — resolves the global account per `(client LegalEntity, hub LegalEntity, currency)`; V1 backed by MMX-managed reference data. Unresolved account → routing failure → client-side `REJECTED`.
- **Routing field mapping** — `portfolioNumber` → global account; counterparty → hub's native institution; currency/amount/valueDate/type/operation/tenor|noticePeriod/minimumRate preserved; `externalOrderReference` stored read-only on the hub-side as `originatingExternalOrderReference` + `originatingLegalEntityCode` (not the hub idempotency key).
- **Synchronous outcome propagation** — hub trader execute sets the client-side order `EXECUTED` in the same transaction, copying rate/time/`dealingReference`; client-side counterparty renders as the client's "via" name. Hub `ASSIGNED` does not propagate. `REJECTED`/`CANCELLED` propagate; routing failures → client `REJECTED`.
- **Routed `OrderExecutedV1` with routing context** — for a routed trade, MMX emits **one** event from the hub-side order carrying a routing-context block (`routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`). The client-side `EXECUTED` transition does **not** emit its own back-office event — a scoped exception to "one outbox row per EXECUTED order".
- **Per-side `Accounted`** — the back office calls `POST /api/v1/back-office/orders/{orderId}/accounted` twice: once for the hub-side orderId, once for the client-side orderId (each side has its own contract, booking, and Transactions 2 confirmation).
- **Contract reversal/replace are back-office-internal** — correlated by `routingId` captured at contract creation; MMX order status is not affected and `ACCOUNTED` stays terminal.

## Capabilities

### New Capabilities

- `order-routing`: client→hub order routing — two linked records, `Routed` status, synchronous routing at intake, `GlobalAccountDirectory` port + reference data, deterministic routing id, field mapping, synchronous outcome propagation, per-side `Accounted`.

### Modified Capabilities

- `money-market-order-lifecycle`: add `ROUTED` to the state machine (client-side only); client-side lifecycle; synchronous routing at TradingClient intake; hub-side order created via routing; synchronous outcome propagation; per-side accounted on routed orders.
- `back-office-outbound-messaging`: routed `OrderExecutedV1` carries the routing-context block; the client-side `EXECUTED` transition does not schedule an outbox row (scoped exception); the routed event is emitted from the hub-side order.
- `back-office-accounting-handoff`: a routed order is accounted by two independent `accounted` callbacks (hub-side and client-side orderIds); the client-side order reaches `ACCOUNTED` from `EXECUTED` (never `ASSIGNED`); `ACCOUNTED` remains terminal.

## Impact

- **Backend — hexagonal (contract-first):**
  - `mmx-domain`: `Routed` status + state-machine update; routing domain concepts (routing id, global account value object, routed-order link, field-mapping policy, outcome propagation rules). No Spring/framework.
  - `mmx-application`: routing use case (intake branch for TradingClients), `GlobalAccountDirectory` `port/out`, hub-side order creation, synchronous propagation on execute; per-side accounted handling. New `port/out` for global-account reference data.
  - `mmx-adapter-in-rest`: intake controller branches by entity role (already carries `legalEntityCode` from Change A); no new trader endpoints (routing is internal). Contract: `OrderExecutedV1` schema gains the optional routing-context block (BACKWARD-compatible) in `contracts/002-trader-orders-views/asyncapi.yaml` (+ `asyncapi-v1.md`).
  - `mmx-adapter-out-persistence`: JPA + Flyway for the routed-order link (`routing_id`, `originating_legal_entity_code`, `originating_external_order_reference` on the order row) and `global_account` reference data (`client_legal_entity_code`, `hub_legal_entity_code`, `currency`, `account_ref`); outbox payload mapper for the routing-context block.
  - `mmx-adapter-out-messaging`: outbox relay/payload unchanged in mechanism; the routed event payload includes the routing-context block; client-side `EXECUTED` suppresses its outbox row.
  - `mmx-bootstrap`: wiring for the routing use case, `GlobalAccountDirectory` adapter, and global-account reference-data management.
- **Contracts (BACKWARD-compatible):** `contracts/002-trader-orders-views/asyncapi.yaml` `OrderExecutedV1` gains optional `routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`; Schema Registry BACKWARD-compatible. No trader HTTP contract change beyond Change A.
- **TDD:** red-first JUnit 5 for routing domain rules, synchronous propagation, global-account resolution/failure, and the outbox-suppression exception; REST integration tests for synchronous routing at intake and per-side accounted. Spec–code parity is blocking.
- **ADRs:** 0002 (two linked records), 0003 (MMX owns order state; back office is event consumer + callback caller).
- **Depends on:** Change A (`legal-entity-tenancy-and-identity`) — LegalEntity role, scope, intake `legalEntityCode`. **Delegated institution grants (Change C) are required for full client intake validation**; until Change C lands, this change routes client orders assuming the grant exists and validates the institution/tenor against a stub or the Change C grant store.
