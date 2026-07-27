# Tasks

## 1. Domain — cross-org TradingClient membership (red-first TDD)

- [x] 1.1 Write failing `LegalEntityCrossOrgTenancyTest` (`mmx-domain`): a TradingClient may connect to a TradingHub in a different Organisation; the same-org guard relaxes symmetrically; local vs remote is derived from org-code comparison, not a stored flag. `mvn test -pl mmx-domain -Dtest=LegalEntityCrossOrgTenancyTest`
- [x] 1.2 Implement the guard relaxation + `isLocalHub` / `isRemoteHub` derivation in `mmx-domain` until green
- [x] 1.3 Write failing `RemoteOrderLifecycleTest` (`mmx-domain`): for a remote client-side order, `Received` covers all pre-confirmed-signal conditions (in-flight, circuit-open, awaiting leg-B); `Routed` is an acceptable unbounded wait-state; no new `OrderStatus` value is introduced; `Received→Routed` via leg-A accept or leg-B `ACCEPTED`; propagation transitions are async for remote pairs. `mvn test -pl mmx-domain -Dtest=RemoteOrderLifecycleTest`
- [x] 1.4 Implement the remote lifecycle clarifications in `mmx-domain` until green

## 2. Domain — remote routing ports + value objects (red-first TDD)

- [x] 2.1 Write failing `RemoteRoutingGatewayContractTest` (`mmx-application`): the out-port accepts a `RemoteRoutingRequest` (carrying resolved hub-side `portfolioNumber`, hub-native institution code, order fields, `routingId`, `originatingLegalEntityCode`) and returns a `RemoteRoutingResponse` (accept or reject). `mvn test -pl mmx-application -Dtest=RemoteRoutingGatewayContractTest`
- [x] 2.2 Define `RemoteRoutingGateway` out-port + request/response value objects in `mmx-application` until green
- [ ] 2.3 Write failing `ExternalIdentityGatewayContractTest` (`mmx-application`): resolves `(client LegalEntityCode, client portfolioNumber, hub LegalEntityCode) → hub-side portfolioNumber`; unresolved signals a routing failure. `mvn test -pl mmx-application -Dtest=ExternalIdentityGatewayContractTest`
- [ ] 2.4 Define `ExternalIdentityGateway` out-port in `mmx-application` until green

## 3. Contracts — REST + AsyncAPI (contract-first, BACKWARD-compatible)

- [ ] 3.1 Create `contracts/007-cross-org-routing/openapi.yaml` (+ `api-v1.md` mirror) defining the LODH inbound `POST /api/v1/cross-org/routed-orders` (routed-order accept) and reference-data query endpoints (currencies, counterparties, rates, grants — scoped to the proven client)
- [ ] 3.2 Create `contracts/007-cross-org-routing/asyncapi.yaml` (+ `asyncapi-v1.md` mirror) defining the `mmx.routed-order-outcome.{orgCode}` channel and `RoutingOutcomeV1` message (`ACCEPTED` / `EXECUTED` / `CANCELLED` / `REJECTED` type discriminator, `(originatingLegalEntityCode, routingId)` correlation key)
- [ ] 3.3 Run codegen: `mvn compile -pl mmx-adapter-in-rest -am` to generate LODH inbound server interfaces from the new OpenAPI
- [ ] 3.4 Re-register `RoutingOutcomeV1` schema with Redpanda Schema Registry; confirm BACKWARD compatibility *(run `scripts/register-schemas.sh` when stack is up)*

## 4. Persistence — partial unique index (additive Flyway)

- [ ] 4.1 Flyway migration: add partial unique index on `(originating_legal_entity_code, routing_id) WHERE originating_legal_entity_code IS NOT NULL` (routed hub-side orders only; local desk orders with null are untouched)
- [ ] 4.2 Verify existing `routing_id` + `originating_legal_entity_code` columns suffice (no new columns): `mvn compile -pl mmx-adapter-out-persistence -am`

## 5. Application — leg-A inbound: AcceptRoutedHubOrderUseCase (red-first TDD)

- [ ] 5.1 Write failing `AcceptRoutedHubOrderUseCaseTest` (`mmx-application`): receives a proven `originatingLegalEntityCode`; validates `(institution, currency, tenor|notice)` against the originating client's grant using the hub's own reference data; on success creates the hub-side order in `RECEIVED`, emits a leg-B `ACCEPTED` event same-tx, returns accept; on grant violation returns reject, creates no order, emits no event. `mvn test -pl mmx-application -Dtest=AcceptRoutedHubOrderUseCaseTest`
- [ ] 5.2 Implement `AcceptRoutedHubOrderUseCase` in `mmx-application` until green
- [ ] 5.3 Write failing `AcceptRoutedHubOrderIdempotencyTest` (`mmx-application`): a leg-A retry triggering a unique-index violation on `(originatingLegalEntityCode, routingId)` catches the violation, resolves to the already-persisted hub-side order, and returns the same accept idempotently. `mvn test -pl mmx-application -Dtest=AcceptRoutedHubOrderIdempotencyTest`
- [ ] 5.4 Implement the unique-violation catch + idempotent resolve in `mmx-application` until green

## 6. Application — leg-B inbound: ApplyRemoteOrderOutcomeUseCase (red-first TDD)

- [ ] 6.1 Write failing `ApplyRemoteOrderOutcomeUseCaseTest` (`mmx-application`): applies `ACCEPTED` (`Received→Routed`), `EXECUTED` (`Routed→Executed`), `CANCELLED` (`Routed→Cancelled`), trader-`REJECTED` (`Routed→Rejected`); idempotent (already-in-expected-state → no-op ack; mismatched-terminal → error); broken-pair (missing client) → throws `RoutedOrderPairIntegrityException`; loads by `(originatingLegalEntityCode=self, routingId)`. `mvn test -pl mmx-application -Dtest=ApplyRemoteOrderOutcomeUseCaseTest`
- [ ] 6.2 Implement `ApplyRemoteOrderOutcomeUseCase` in `mmx-application` until green
- [ ] 6.3 Write failing `RemotePairPropagationBypassTest` (`mmx-application`): when `originatingLegalEntityCode`'s org ≠ hub's org, the hub execute/cancel/reject transaction persists only the hub-side terminal + leg-B outbox row and does NOT look up or transition the client-side order; local pairs keep synchronous in-process propagation unchanged. `mvn test -pl mmx-application -Dtest=RemotePairPropagationBypassTest`
- [ ] 6.4 Implement the remote-pair propagation bypass in `mmx-application` until green

## 7. Application — gateway retry + circuit-breaker (red-first TDD)

- [ ] 7.1 Write failing `RemoteRoutingGatewayRetryTest` (`mmx-application`): on a leg-A transient failure (timeout / 5xx), the gateway retries with backoff and the order stays `Received`; on sustained unreachability past the circuit-breaker threshold, an ops signal is emitted (not a state transition); when the circuit re-closes, retries resume automatically. `mvn test -pl mmx-application -Dtest=RemoteRoutingGatewayRetryTest`
- [ ] 7.2 Implement the retry + circuit-breaker policy in `mmx-application` until green

## 8. adapter-in-rest — LODH inbound REST + transport-proven identity (red-first TDD)

- [ ] 8.1 Write failing `RoutedOrderAcceptControllerTest` (`mmx-adapter-in-rest`): the LODH inbound controller maps the transport credential to a proven `originatingLegalEntityCode`, calls `AcceptRoutedHubOrderUseCase`, and returns the accept/reject response per the OpenAPI contract. `mvn test -pl mmx-adapter-in-rest -Dtest=RoutedOrderAcceptControllerTest`
- [ ] 8.2 Implement the LODH inbound REST controller + credential-to-`originatingLegalEntityCode` binding in `mmx-adapter-in-rest` until green
- [ ] 8.3 Write failing `CrossOrgGatewaySecurityTest` (`mmx-adapter-in-rest`): unknown credentials are early-rejected at the gateway; a proven `originatingLegalEntityCode` not in the hub's TradingClient list is rejected as a domain rule (defense-in-depth). `mvn test -pl mmx-adapter-in-rest -Dtest=CrossOrgGatewaySecurityTest`
- [ ] 8.4 Implement the gateway early-reject + use-case membership re-check in `mmx-adapter-in-rest` until green
- [ ] 8.5 Write failing `RemoteReferenceDataQueryControllerTest` (`mmx-adapter-in-rest`): LODH exposes reference-data query endpoints scoped to the proven originating client per the OpenAPI contract. `mvn test -pl mmx-adapter-in-rest -Dtest=RemoteReferenceDataQueryControllerTest`
- [ ] 8.6 Implement the LODH reference-data query controllers in `mmx-adapter-in-rest` until green

## 9. adapter-out-integration — CGED outbound REST + remote-backed reads (red-first TDD)

- [ ] 9.1 Write failing `RemoteRoutingGatewayRestAdapterTest` (`mmx-adapter-out-integration`): the CGED REST client calls the LODH inbound endpoint with the resolved account + hub-native institution code and returns the accept/reject response. `mvn test -pl mmx-adapter-out-integration -Dtest=RemoteRoutingGatewayRestAdapterTest`
- [ ] 9.2 Implement the `RemoteRoutingGateway` REST client adapter in `mmx-adapter-out-integration` until green
- [ ] 9.3 Write failing `ExternalIdentityGatewayAdapterTest` (`mmx-adapter-out-integration`): resolves `(client, client portfolioNumber, hub) → hub-side portfolioNumber` from the external identity system; unresolved signals a routing failure. `mvn test -pl mmx-adapter-out-integration -Dtest=ExternalIdentityGatewayAdapterTest`
- [ ] 9.4 Implement the `ExternalIdentityGateway` adapter in `mmx-adapter-out-integration` until green
- [ ] 9.5 Write failing `RemoteReferenceDataAdapterTest` (`mmx-adapter-out-integration`): remote-backed adapters read currencies / rates / grants / counterparties live from LODH via REST; selected when `isRemoteHub`; CGED stores zero hub reference data locally; proxy indirection collapses (hub-native codes cross the boundary). `mvn test -pl mmx-adapter-out-integration -Dtest=RemoteReferenceDataAdapterTest`
- [ ] 9.6 Implement the remote-backed reference-data adapters in `mmx-adapter-out-integration` until green

## 10. adapter-out-messaging — leg-B outbox + Kafka consumer (red-first TDD)

- [ ] 10.1 Write failing `RoutingOutcomeOutboxTest` (`mmx-adapter-out-messaging`): LODH's outbox commits an `ACCEPTED` row same-tx as hub-side order creation; commits terminal-outcome rows same-tx as the hub-side transition; routing-failure reject commits no row. `mvn test -pl mmx-adapter-out-messaging -Dtest=RoutingOutcomeOutboxTest`
- [ ] 10.2 Implement the routing-outcome outbox scheduling in `mmx-adapter-out-messaging` until green
- [ ] 10.3 Write failing `RoutingOutcomeConsumerTest` (`mmx-adapter-out-messaging`): CGED's Kafka consumer decodes `RoutingOutcomeV1`, filters by `originatingLegalEntityCode ∈ {its own LEs}`, and calls `ApplyRemoteOrderOutcomeUseCase`; non-matching events are skipped. `mvn test -pl mmx-adapter-out-messaging -Dtest=RoutingOutcomeConsumerTest`
- [ ] 10.4 Implement the CGED Kafka consumer adapter in `mmx-adapter-out-messaging` until green
- [ ] 10.5 Provision the `mmx.routed-order-outcome.LODH` topic + CGED consume-only ACL (broker config / infra task)

## 11. Bootstrap — wiring + connection-registration

- [ ] 11.1 Wire all new ports/adapters in `mmx-bootstrap`: `RemoteRoutingGateway`, `ExternalIdentityGateway`, `AcceptRoutedHubOrderUseCase`, `ApplyRemoteOrderOutcomeUseCase`, remote-backed reference-data adapters, Kafka consumer, retry/circuit-breaker policy
- [ ] 11.2 Connection-registration task: CGED `connectedHubCode=LOC` + CGD in LOC's TradingClient list (LODH) + credentials/endpoints provisioned on both sides
- [ ] 11.3 Integration test (`mmx-bootstrap`, Testcontainers): end-to-end remote route — CGD places order → `ExternalIdentityGateway` resolves account → leg-A REST → LODH `AcceptRoutedHubOrderUseCase` accepts → leg-B `ACCEPTED` → CGED `ApplyRemoteOrderOutcomeUseCase` → client-side `Routed`; then hub execute → leg-B `EXECUTED` → client-side `Executed`; plus a local-routing regression assertion (PAR→LOC stays synchronous/atomic)

## 12. Docs — CONTEXT.md + ADRs

- [ ] 12.1 Update `CONTEXT.md`: redefine TradingClient as hub-owned cross-org-capable membership; add cross-org routing terms (leg A, leg B, `ExternalIdentityGateway`, `RemoteRoutingGateway`, silence-is-never-terminal); add cross-org caveat to `Rejected` routing-failure (CGED-side for account; LODH trusts the account)
- [ ] 12.2 Write `docs/adr/0006-silence-is-never-terminal-remote-routing.md` (leg B authoritative lifecycle mirror + gateway-owned retry + no new state)
- [ ] 12.3 Write `docs/adr/0007-cross-org-trust-boundary.md` (transport-proven identity, trust authoritative resolution, validate only the grant)
- [ ] 12.4 Update `docs/adr/0002-routed-order-is-two-linked-records.md` with the cross-org relaxation clause (atomic pair-integrity → detect + alert across deployments; local stays prevent via throw+rollback)

## 13. Final verification (single gate before marking complete)

- [ ] 13.1 Run full `cd backend && mvn test` — all modules green, including remote routing domain rules, idempotency, grant validation at accept, outcome apply, gateway retry/circuit-breaker, propagation bypass, and end-to-end Testcontainers integration
- [ ] 13.2 `openspec validate cross-org-routing-transport` passes; spec–code parity confirmed (cross-org transport, remote account resolution, thin client, correlation/idempotency, outcome propagation, silence-is-never-terminal, trust boundary, no local-routing regression)
- [ ] 13.3 Schema Registry: `RoutingOutcomeV1` registers BACKWARD-compatible; async contract and codegen in sync
