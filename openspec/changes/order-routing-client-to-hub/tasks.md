# Tasks

## 1. Domain — routing model and `Routed` status (red-first TDD)

- [ ] 1.1 Add `ROUTED` to `OrderStatus` and update the state machine: `RECEIVED→ROUTED`, `ROUTED→{EXECUTED,REJECTED,CANCELLED}`; forbid `ROUTED↔ASSIGNED`. Unit test (red): assert a client-side order can go `RECEIVED→ROUTED→EXECUTED` and can never enter `ASSIGNED`; assert a hub-side order never uses `ROUTED`.
- [ ] 1.2 Model `RoutedOrderLink` value object (`routingId`, `clientOrderId`, `clientLegalEntityCode`, `hubOrderId`, `hubLegalEntityCode`) and a deterministic `RoutingId` derived from the client-side order id. Unit test (red): same client order id → same routing id; two distinct client orders → distinct routing ids.
- [ ] 1.3 Model `GlobalAccount` value object (`clientLegalEntityCode`, `hubLegalEntityCode`, `currency`, `accountRef`) and a `RoutingFailure` signal for unresolved accounts.
- [ ] 1.4 Model the routing field-mapping policy (portfolioNumber→global account; counterparty→hub native institution; preserve currency/amount/valueDate/type/operation/tenor|noticePeriod/minimumRate; store `originatingExternalOrderReference` + `originatingLegalEntityCode` as a trace). Unit test (red): mapping a client order produces the expected hub-side fields and trace.

## 2. Application — routing use case and propagation (red-first TDD)

- [ ] 2.1 Define `GlobalAccountDirectory` out-port (`port/out`) resolving `(client, hub, currency) → GlobalAccount | unresolved`. Unit test the port contract with a fake.
- [ ] 2.2 Implement `RouteOrderUseCase`: at a TradingClient intake, create client-side `RECEIVED`, resolve hub + global account, create hub-side `RECEIVED`, link by routing id, flip client-side `RECEIVED→ROUTED`, all in one transaction. Unit test (red): successful routing yields `ROUTED` + linked hub-side `RECEIVED`; unresolved account → `REJECTED`, no hub-side order; grant/enabled-set violation → `REJECTED`, no hub-side order.
- [ ] 2.3 Implement synchronous outcome propagation on execute: when a hub-side order executes, set the linked client-side order `ROUTED→EXECUTED` in the same transaction, copying `executedRate`, `executionTime`, `dealingReference`; client-side counterparty renders as the "via" name. Unit test (red): hub execute → client `EXECUTED` with copied facts; hub `ASSIGNED` does not propagate; hub reject/cancel → client `REJECTED`/`CANCELLED`.
- [ ] 2.4 Implement the routed-outbox suppression decision: the client-side `EXECUTED` (propagated) schedules no outbox row; the hub-side `EXECUTED` schedules one carrying the routing-context block. Unit test (red): routed trade → one outbox intent on hub-side, none on client-side.
- [ ] 2.5 Per-side accounted: the existing accounted use case handles each `orderId` independently; confirm the client-side order (`EXECUTED`, never `ASSIGNED`) accounts via the same path. Unit test (red): client-side `EXECUTED→ACCOUNTED`; accounting one side independent of the other.

## 3. Contracts — async first (contract-first, BACKWARD-compatible)

- [ ] 3.1 Add optional routing-context fields (`routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`) to `contracts/002-trader-orders-views/schemas/OrderExecutedV1.json` and the AsyncAPI `OrderExecutedV1` message in `contracts/002-trader-orders-views/asyncapi.yaml`; update `asyncapi-v1.md` to document the routing-context block, the routed single-event rule, and idempotency keyed by `orderId`.
- [ ] 3.2 Re-register the schema with Redpanda Schema Registry and confirm BACKWARD compatibility passes (per `back-office-outbound-messaging` schema-registry requirement). No trader HTTP contract change in this change.

## 4. REST adapter — intake branch by role

- [ ] 4.1 Branch intake by the target LegalEntity's role (Change A supplies `legalEntityCode`): TradingClient → `RouteOrderUseCase`; TradingHub → native intake. Integration test (red): TradingClient intake returns success with a `ROUTED` client-side order and a linked hub-side `RECEIVED` order; TradingHub intake returns a native `RECEIVED` order.
- [ ] 4.2 No new trader endpoints; routing is internal. The existing `accounted` callback is unchanged in shape (per-side usage is a back-office behaviour).

## 5. Persistence — JPA + Flyway (additive)

- [ ] 5.1 Flyway migration: add nullable `routing_id`, `originating_legal_entity_code`, `originating_external_order_reference` to `money_market_order`; unique constraint allowing nulls on `routing_id` per side.
- [ ] 5.2 Flyway migration: add `global_account` reference-data table (`client_legal_entity_code`, `hub_legal_entity_code`, `currency`, `account_ref`, primary key over the tuple).
- [ ] 5.3 JPA mapping for the routed-order link columns and the `global_account` entity; repository for `GlobalAccountDirectory` adapter keyed by `(client, hub, currency)`. Integration test (red): resolve returns the configured account; missing tuple signals unresolved.

## 6. Messaging adapter — outbox payload + suppression

- [ ] 6.1 Outbox payload mapper freezes the routing-context block on the hub-side `OrderExecutedV1` for routed trades and omits it for native orders. Unit test (red): routed payload includes the block; native payload omits it; both validate against the updated `OrderExecutedV1` schema.
- [ ] 6.2 Enforce the suppression: a propagated client-side `EXECUTED` writes no outbox row; the hub-side `EXECUTED` writes one. Integration test (red): exactly one outbox row per routed trade (hub-side), zero for the client-side.
- [ ] 6.3 Relay unchanged in mechanism; verify the routed message is published keyed by the hub-side `orderId` and validates against AsyncAPI.

## 7. Bootstrap — wiring and reference-data management

- [ ] 7.1 Wire `RouteOrderUseCase`, `GlobalAccountDirectory` adapter, propagation on execute, and the suppression rule into the application/bootstrap configuration.
- [ ] 7.2 Minimal TradingHub settings operation to manage `global_account` rows (`(client, hub, currency) → accountRef`); changes are prospective only (no rewrite of historical orders). Integration test (red): add/change an entry; already-routed/executed orders keep their original account.

## 8. Frontend (Angular) — minimal

- [ ] 8.1 Hub Settings: global-account reference-data management screen (`(client, hub, currency) → accountRef`) for TradingHub users only (role-gated per Change A). Component test (red): create/list/edit entries; client users cannot access.
- [ ] 8.2 No TradingClient desk UI (clients are Settings-only per Change A); routed client-side orders are not acted on from the UI.

## 9. Final verification (single gate before marking complete)

- [ ] 9.1 `openspec validate order-routing-client-to-hub` passes; spec–code parity confirmed (lifecycle `ROUTED`, messaging routing-context + suppression, accounting per-side, new `order-routing` capability).
- [ ] 9.2 Backend: `./gradlew test` green, including routing domain, synchronous propagation, global-account resolution/failure, outbox suppression, and per-side accounted integration tests (Testcontainers).
- [ ] 9.3 Schema Registry: updated `OrderExecutedV1` registers BACKWARD-compatible; async contract and codegen in sync.
- [ ] 9.4 Frontend: `ng test` (Vitest) green for the global-account management screen and role gating.
