## Why

Same-organisation order routing (the archived `order-routing-client-to-hub` + `routed-order-outcome-propagation`) is synchronous and atomic — two linked records committed in one transaction. **CGD**, a TradingClient in Organisation **CGED**, must route its orders to **LOC**, the TradingHub of Organisation **LODH** — the first cross-organisation, cross-deployment routing case. The two deployments cannot share a transaction, so the atomic pair guarantee (ADR-0002) must be explicitly relaxed to an eventually-consistent model with a defined failure contract, without regressing the local synchronous path.

## What Changes

- **TradingClient membership spans organisations.** A TradingHub's client list may include legal entities from other Organisations; CGD is a genuine TradingClient of LOC. The same-org guard relaxes symmetrically (a client may point at a foreign hub; a hub's client list may contain a foreign-org entity). Connection is bidirectional, wired by a registration task.
- **Hybrid transport backbone.** **Leg A** (routing request, CGED→LODH) = synchronous REST handshake — immediate accept→`Routed` or reject→`Rejected` in bounded time. **Leg B** (outcome return, LODH→CGED) = asynchronous Kafka, reusing the existing transactional-outbox + relay; the topic is LODH-owned and org-suffixed (`mmx.routed-order-outcome.LODH`), with CGED holding a consume-only ACL.
- **Remote account resolution at CGED.** A new `ExternalIdentityGateway` out-port (CGED-side) resolves `(client, client portfolioNumber, hub) → hub-side portfolioNumber` before send; the account travels in the leg-A payload. LODH trusts the resolved account and does **not** revalidate (account failures shift to booking time, not a routing reject). Unresolved account → CGED-side `Rejected` directly (no round-trip).
- **Thin remote client.** CGED stores zero hub reference data; a remote client reads currencies / rates / grants / counterparties **live from LODH** per request via remote-backed adapters (no replication — leg A is sync, so caching buys no availability). LODH's in-process grant check at leg-A accept is the one true validation. Proxy indirection collapses: hub-native institution codes cross the boundary; `"BNP via LOC"` is display-only on CGED.
- **Cross-boundary correlation.** LODH trusts the CGED-minted `RoutingId`; the hub-side idempotency key is `(originatingLegalEntityCode, routingId)`, enforced by a **partial unique index** (`WHERE originating_legal_entity_code IS NOT NULL`). A unique-violation resolves to an idempotent success. No internal order UUID crosses the boundary.
- **Dedicated remote inbound use cases.** LODH gains `AcceptRoutedHubOrderUseCase` (leg-A inbound — validates the grant in-process, returns accept/reject synchronously, emits the leg-B `ACCEPTED` event same-tx). CGED gains `ApplyRemoteOrderOutcomeUseCase` (leg-B inbound — idempotent apply of `ACCEPTED` / `EXECUTED` / `CANCELLED` / trader-`REJECTED`).
- **Hub-side propagation bypassed for remote pairs.** LODH's synchronous client-side propagation does **not** run when `originatingLegalEntityCode`'s Organisation ≠ the hub's Organisation; the hub transaction persists only the hub terminal transition + the leg-B outbox row.
- **Silence is never terminal.** A remote client-side order in `Received` is never auto-terminalized on transport silence. Leg B is the authoritative lifecycle mirror (carries a non-terminal `ACCEPTED` event, emitted same-tx as hub-side order creation); leg A is the latency fast path (sub-second `Received→Routed` on the happy path). `RemoteRoutingGateway` owns indefinite retry + backoff + circuit-breaker for leg-A transients (order stays `Received`; circuit-open emits an ops signal, never a state transition). No new `OrderStatus` value.
- **Routing-failure reject is HTTP-only.** A leg-A routing-failure reject (grant/currency/tenor invalid at LODH accept) creates no hub-side order, so it rides **no** leg-B event — the HTTP reject response closes `Received→Rejected` directly. Trader-reject (origin=`TRADER`) rides leg-B `REJECTED`.
- **Transport-proven identity.** The leg-A credential binds to exactly one remote legal entity; the gateway hands `AcceptRoutedHubOrderUseCase` a **proven** `originatingLegalEntityCode` (never a payload-claimed identity). The allow-list is the TradingClient membership (defense-in-depth: gateway early-reject + use-case domain check).

## Capabilities

### New Capabilities

(none — extends existing `order-routing`)

### Modified Capabilities

- `order-routing`: cross-org routing transport (leg A REST + leg B Kafka), remote account resolution via `ExternalIdentityGateway`, thin remote client reference-data, cross-boundary correlation key + partial unique index, remote outcome propagation (`ApplyRemoteOrderOutcomeUseCase`), silence-is-never-terminal failure contract, hub-side propagation bypass for remote pairs, routing-failure HTTP-only reject.
- `money-market-order-lifecycle`: remote client-side state machine clarification — no new status value; `Received` covers all pre-confirmed-signal conditions (in-flight, circuit-open, awaiting leg-B).
- `back-office-outbound-messaging`: new LODH-owned org-suffixed leg-B topic (CGED consume-only ACL) + `ACCEPTED` non-terminal event type emitted from `AcceptRoutedHubOrderUseCase` via the existing outbox relay.
- `legal-entity-tenancy`: TradingClient membership may span organisations; same-org guard relaxed symmetrically; bidirectional connection (CGED `connectedHubCode=LOC` + CGD in LOC's client list).

## Impact

- **Backend — hexagonal (contract-first):**
  - `mmx-domain`: TradingClient cross-org membership model + same-org guard relaxation; remote routing domain concepts (`RemoteRoutingGateway` port contract, `ExternalIdentityGateway` port contract, remote routing request/response value objects, remote-pair detection, silence-is-never-terminal rule, `AcceptRoutedHubOrderUseCase` / `ApplyRemoteOrderOutcomeUseCase` domain rules). No Spring/framework.
  - `mmx-application`: new out-ports (`RemoteRoutingGateway`, `ExternalIdentityGateway`, remote reference-data read ports); new use cases (`AcceptRoutedHubOrderUseCase`, `ApplyRemoteOrderOutcomeUseCase`); hub-side propagation bypass; gateway retry/circuit-breaker policy.
  - `mmx-adapter-in-rest`: new LODH inbound REST endpoint for routed-order accept + LODH reference-data query endpoints (contract-first OpenAPI); CGED leg-A outbound REST client adapter. Transport-proven identity binding (credential → `originatingLegalEntityCode`).
  - `mmx-adapter-out-persistence`: Flyway partial unique index on `(originating_legal_entity_code, routing_id) WHERE originating_legal_entity_code IS NOT NULL`; remote-backed read adapter implementations.
  - `mmx-adapter-out-messaging`: CGED Kafka consumer adapter for leg-B outcomes; `ACCEPTED` event type on the LODH outbox relay; topic + ACL provisioning.
  - `mmx-bootstrap`: wiring for all new ports/adapters; connection-registration (both sides: CGED `connectedHubCode=LOC` + CGD in LOC's client list + credentials/endpoints).
- **Contracts (BACKWARD-compatible):** new LODH inbound REST contract (`contracts/` — contract-first OpenAPI + `api-v1.md` mirror) for routed-order accept + reference-data queries; new AsyncAPI event schema for the leg-B outcome topic (`ACCEPTED` / `EXECUTED` / `CANCELLED` / `REJECTED` with type discriminator); Schema Registry BACKWARD-compatible registration.
- **TDD:** red-first JUnit 5 for remote routing domain rules, cross-boundary idempotency (unique-violation → idempotent success), grant validation at accept, outcome apply idempotency, silence-is-never-terminal, gateway retry/circuit-breaker; REST integration tests for leg-A accept/reject; Kafka integration tests for leg-B outcome apply (Testcontainers).
- **ADRs:** ADR-0002 relaxation (atomic pair-integrity → detect + alert across deployments); ADR-0006 (silence is never terminal + leg B authoritative lifecycle mirror + gateway-owned retry); ADR-0007 (cross-org trust boundary — transport-proven identity, trust authoritative resolution, validate only the grant).
- **Depends on:** existing same-org `order-routing`; `legal-entity-tenancy`; `delegated-institution-grants`. **Coexistence:** local (same-deployment) routing stays synchronous/atomic and unchanged; remote routing is polymorphic (`isLocalHub` / `isRemoteHub`).
