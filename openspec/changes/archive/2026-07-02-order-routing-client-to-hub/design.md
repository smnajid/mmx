## Context

Change A introduced LegalEntity tenancy and the TradingHub/TradingClient role. A TradingClient has no desk, so its PM orders must be executed at its connected TradingHub. The legacy FiduTrader system routed by having the Deposits back office poll the order DB and copy rows between entity databases; MMX replaces that with in-process routing inside the one-deployment-per-Organisation model (ADR-0001) and decouples the back office via events + callbacks (ADR-0003). This change implements routing and the back-office decoupling. It depends on Change A (LegalEntity role, scope, intake `legalEntityCode`) and cooperates with Change C (delegated institution grants), which supplies the client intake validation this change assumes.

The existing outbox/relay (`back-office-outbound-messaging`) publishes `OrderExecutedV1` (Schema-Registry-enforced, BACKWARD-compatible) on `mmx.order.executed`; the existing `POST /api/v1/back-office/orders/{orderId}/accounted` callback transitions `EXECUTED → ACCOUNTED` (terminal). This change extends the event payload for routed orders and uses the callback twice per routed trade.

## Goals / Non-Goals

**Goals:**

- Route a TradingClient's intake to its connected TradingHub: create a client-side order and a linked hub-side order synchronously, in one transaction, before intake returns.
- Introduce `Routed` status and the client-side lifecycle `RECEIVED → ROUTED → EXECUTED → ACCOUNTED`.
- Resolve the client's global account at the hub via a `GlobalAccountDirectory` port (V1 reference data).
- Propagate the hub trader's execution to the client-side order synchronously (rate/time/references), with the client-side counterparty rendered as the "via" name.
- Emit one routed `OrderExecutedV1` from the hub-side order carrying routing context; suppress the client-side outbox row.
- Support per-side `Accounted` via the existing callback (two independent calls).
- Keep `Accounted` terminal; reversal/replace stay back-office-internal, correlated by `routingId`.

**Non-Goals:**

- Delegated institution grants, proxy institutions, "via {hub}" naming, per-currency tenor grants, ClientRepresentative settings UI (Change C).
- Cross-organisation routing (out of scope for V1; same-Org only).
- Hub-of-hubs / a LegalEntity being both hub and client (V2).
- Back-office contract reversal/replace implementation (back-office-internal; MMX only exposes `routingId`).
- Reference-data LegalEntity scoping of institutions/currencies/rates (Change C).

## Decisions

### D1: Two linked records, deterministic routing id (ADR-0002)

A routed order is a **client-side order** (client LegalEntity scope, client PM `portfolioNumber` + `ExternalOrderReference`) and a **hub-side order** (hub LegalEntity scope, global account as `portfolioNumber`, hub-native institution), linked by a **routing id** generated **deterministically from the client-side order id**. The hub-side order's intake idempotency key is the routing id, so a routing retry resolves to the same hub-side order (no duplicate).

**Rationale:** Each LegalEntity has a different accounting reality and identity; the per-LegalEntity scope rule forbids one record carrying both. Deterministic id enables safe retry. Alternatives: one shared record (rejected — violates scope, breaks idempotency); random routing id with lookup (rejected — retry needs a lookup table).

**Adapter placement:** the link is persisted as columns on the order row (`routing_id`, `originating_legal_entity_code`, `originating_external_order_reference`) in `mmx-adapter-out-persistence`; the domain models a `RoutedOrderLink` value object in `mmx-domain`. Routing orchestration is an application use case (`RouteOrderUseCase`) in `mmx-application`.

### D2: Synchronous routing at intake (one transaction)

The intake use case, when the active LegalEntity is a TradingClient, performs routing **inside the intake transaction**: validate (grant — Change C), resolve the connected hub, resolve the global account via `GlobalAccountDirectory`, create the client-side order (`RECEIVED`), create the hub-side order (`RECEIVED`, hub scope), link them, and flip the client-side order to `ROUTED`. Intake returns only after the hub-side order exists on the hub desk. A grant/enabled-set violation or an unresolved global account → intake rejects (client-side `REJECTED`, no hub-side order).

**Rationale:** Same deployment (in-process); deterministic id makes it safe; gives the PM a deterministic answer (routed or rejected) at intake time. Alternatives: async routing worker (rejected — async "received, will route later" limbo; retry complexity).

### D3: GlobalAccountDirectory port, reference-data backed (V1)

A `port/out` `GlobalAccountDirectory` resolves the global account for `(client LegalEntityCode, hub LegalEntityCode, currency)`. V1 implementation reads MMX-managed reference data (`global_account` table). The port is the seam for a future external account-management system.

**Rationale:** Treasury/nostro accounts are currency-segregated; the order carries currency, so the lookup is deterministic. A port keeps the routing path free of a hard external dependency in V1 while allowing later externalisation. Unresolved account is a routing failure → client-side `REJECTED`.

### D4: Synchronous outcome propagation on execute

When the hub trader executes the hub-side order, the execute use case **synchronously** sets the linked client-side order to `EXECUTED` **in the same transaction**, copying `executedRate`, `executionTime`, `dealingReference` (and contract-number rules per operation). The client-side counterparty is rendered as the client's delegated-institution display name ("BNP via LOC"), not the hub's native institution. Hub `ASSIGNED` does not propagate. Hub `REJECTED`/`CANCELLED` propagate to client `REJECTED`/`CANCELLED`.

**Rationale:** In-process, same deployment; two records of one trade should commit atomically; no window where the hub is `EXECUTED` and the client is still `ROUTED`. Alternatives: async outbox between records (rejected — that's for external consumers; adds an eventual-consistency window and retry/idempotency for intra-trade sync).

### D5: Single routed OrderExecutedV1 with routing context; client-side outbox suppression

For a routed trade, MMX emits **one** `OrderExecutedV1` from the **hub-side order** (not one per order). The payload carries the hub-side booking facts plus a routing-context block: `routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`. The **client-side `EXECUTED` transition does not schedule an outbox row** — a deliberate, scoped exception to "one outbox row per EXECUTED order", applying only to routed client-side orders. For native (non-routed) orders the routing context is absent and behaviour is unchanged.

**Rationale:** Contract creation and contract reversal/replace then follow the same LOC→PAR back-office broadcast pattern, correlated by `routingId` captured at contract creation. Two self-contained events were rejected because the user wants one consistent broadcast pattern for creation and reversal/replace. The client-side order's `EXECUTED` is MMX-internal (PM-facing status only), so it needs no back-office event.

**Adapter placement:** the outbox payload mapper in `mmx-adapter-out-messaging` freezes the routing-context block at schedule time; the execute use case (application) decides whether to schedule an outbox row (suppress for routed client-side). The `OrderExecutedV1` JSON schema (`contracts/002-trader-orders-views/schemas/OrderExecutedV1.json`) gains the optional fields — BACKWARD-compatible per the Schema Registry gate.

### D6: Per-side Accounted via the existing callback

The back office calls `POST /api/v1/back-office/orders/{orderId}/accounted` **twice** for a routed trade: once with the hub-side orderId (after the hub's Transactions 2 confirms the hub booking), once with the client-side orderId (after the client's Transactions 2 confirms the client booking). The two flows are independent. `ACCOUNTED` remains terminal; the client-side order transitions `EXECUTED → ACCOUNTED` (never `ASSIGNED`).

**Rationale:** Reuses the existing callback and the `EXECUTED → ACCOUNTED` terminal rule from `back-office-accounting-handoff`; each side books independently. No new callback contract.

### D7: Contract-first async schema change (BACKWARD-compatible)

Update `contracts/002-trader-orders-views/asyncapi.yaml` + `schemas/OrderExecutedV1.json` (+ `asyncapi-v1.md`) to add the optional routing-context fields **before** changing the producer serialiser. Re-register with the Schema Registry; BACKWARD compatibility must pass. No trader HTTP contract change in this change (Change A owns the header/intake fields).

**Rationale:** Governance Principle I (contract-first) and the Schema-Registry compatibility gate. Adding optional fields is BACKWARD-compatible.

## Risks / Trade-offs

- **[Risk] Cross-transactional aggregate update on execute** — execute touches two LegalEntity-scoped aggregates (hub-side + client-side) in one tx. Mitigation: same deployment, single DB; the link makes the client-side lookup deterministic. Test with `mmx-bootstrap` Testcontainers integration.
- **[Risk] Outbox-suppression exception is easy to mis-implement** — the client-side `EXECUTED` must not schedule an outbox row, which contradicts the existing "every EXECUTED → one outbox row" rule. Mitigation: an explicit domain rule + a unit test asserting no outbox row is scheduled for a routed client-side `EXECUTED`; document the scoped exception in `back-office-outbound-messaging`.
- **[Risk] Dependency on Change C for client intake validation** — full client intake validation needs the delegated grant store (Change C). Mitigation: this change routes against a `GlobalAccountDirectory` and a grant lookup port; if Change C is not yet landed, the port is stubbed and integration tests use a seeded grant. Ordering: Change C can land before or alongside; both must be present before production client intake.
- **[Trade-off] Synchronous routing couples intake to the hub desk** — intake waits for hub-side order creation. Acceptable: in-process, fast; gives a deterministic PM answer.
- **[Trade-off] Single routed event bloats the payload** — the routing-context block adds five optional fields. Acceptable: BACKWARD-compatible; chosen so creation and reversal/replace share one broadcast pattern.

## Migration Plan

1. Flyway: add `routing_id`, `originating_legal_entity_code`, `originating_external_order_reference` to `money_market_order` (nullable); add `global_account` reference-data table.
2. Async contract: add optional routing-context fields to `OrderExecutedV1` (+ mirror); re-register schema (BACKWARD).
3. Implement domain/application routing + propagation + global-account port; adapt outbox payload mapper and the suppression rule.
4. REST: intake branches by role (Change A provides `legalEntityCode`); no new trader endpoints.
5. Rollback: migrations are additive (nullable columns + new table); revert code; existing native-order flow is untouched.

## Open Questions

- **Grant lookup port shape (with Change C)** — does routing call a `DelegatedGrantDirectory` port (Change C) at intake, or is grant validation a separate intake step that precedes routing? (Lean: a single intake use case calls grant validation then routing, both in tx.)
- **Hub-side order visibility on the hub desk** — should hub-side routed orders be visually distinguished from native orders (e.g. a "via {client}" marker)? (Lean: no visual distinction in V1; it is a normal `RECEIVED` order on the hub desk. Confirm.)
- **Global-account reference-data management UI** — is managing `global_account` rows a TradingHub settings operation in this change, or deferred? (Lean: a minimal hub settings operation in this change, since routing cannot work without it.)
