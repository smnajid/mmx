## Context

Same-organisation order routing (archived `order-routing-client-to-hub` + `routed-order-outcome-propagation`) is synchronous and atomic: a TradingClient's intake creates two linked records in one transaction, and the hub trader's execute propagates `ROUTED→EXECUTED` to the client-side order in the same transaction (ADR-0002). This works because both records live in one deployment/DB.

**CGD** (a TradingClient in Organisation **CGED**) must route to **LOC** (the TradingHub of Organisation **LODH**) — two separate MMX deployments, two separate DBs. The atomic pair guarantee is impossible across a network boundary. This change introduces **cross-org routing** as an eventually-consistent, async path that coexists with the unchanged local synchronous path. Routing is polymorphic: `isLocalHub` (same deployment) vs `isRemoteHub` (cross-deployment), derived from whether the connected hub's `OrganisationCode` matches the deployment's own.

The existing outbox/relay (`back-office-outbound-messaging`) publishes `OrderExecutedV1` on `mmx.order.executed`. The existing `POST /api/v1/back-office/orders/{orderId}/accounted` callback transitions `EXECUTED→ACCOUNTED`. This change reuses the outbox pattern for leg B and leaves the accounting callback contract unchanged.

## Goals / Non-Goals

**Goals:**

- Route a remote TradingClient's order to a cross-org TradingHub over a hybrid transport (leg A sync REST, leg B async Kafka).
- Give the client-side order a definitive `Received→Routed` (accept) or `Received→Rejected` (routing failure) in bounded time on the happy path, with **silence is never terminal** as the failure contract.
- Resolve the hub-side account at CGED via `ExternalIdentityGateway` before send; LODH trusts it.
- Provide live reference-data reads for the thin remote client (zero hub data stored in CGED).
- Enforce cross-boundary idempotency via `(originatingLegalEntityCode, routingId)` + partial unique index.
- Propagate hub outcomes (ACCEPTED, EXECUTED, CANCELLED, trader-REJECTED) back to CGED idempotently via leg B.
- Authenticate cross-org routing via transport-proven identity bound to one legal entity.
- Keep local (same-deployment) routing synchronous/atomic and unchanged.

**Non-Goals:**

- Rejection origin field (`TRADER` | `ROUTING_FAILURE`) persistence + migration — deferred to the hardening change (`cross-org-routing-hardening`).
- Pair-integrity DLQ + ops alert formalization (ADR-0002 "detect + alert" mechanism) — deferred to hardening; this change throws on missing/mismatched client (basic safety).
- `accounted` callback `routingId`-correlation support on CGED — deferred to hardening.
- Ops/observability (consumer-lag, broker-health, outbox-age monitoring, stale-Routed dashboards) — operational SLO work.
- Generalising beyond the single CGD→LOC case to N remote clients / N remote hubs.
- Unifying the local global-account model onto the portfolio key (defect 09 — changes local routing).
- The Deposits back-office cross-org broadcast channel (Deposits-programme work; MMX contract unchanged per 06).
- Booking-time account-failure runbook (ops fog — a bad CGED mapping surfaces post-`Executed` at Transactions 2; manual unwind).

## Decisions

### D1: Hybrid transport backbone (leg A REST + leg B Kafka)

**Leg A (routing request, CGED→LODH) = synchronous REST handshake.** CGED calls a LODH inbound endpoint and gets an immediate accept/reject. This gives the client-side order a definitive `Received→Routed` or `Received→Rejected` in bounded time, honouring the framing constraint that a client stays `Received` until the hub confirms the hub-side order exists. **Leg B (outcome return, LODH→CGED) = asynchronous Kafka**, reusing the existing transactional-outbox + relay. Execution happens later and must survive restarts, so it rides the durable outbox pattern MMX already trusts.

**Return transport = shared broker (Kafka), not point-to-point callback.** All org deployments sit in one cloud provider and a shared broker already exists, so Kafka doesn't introduce a new shared failure domain. It lets us reuse the existing outbox relay rather than build a new HTTP-POST relay + inbound controller + bespoke retries.

**Topic ownership:** a topic has exactly one owning deployment; the `OrganisationCode` is a suffix. The outcome is produced by LODH and consumed by CGED → the topic is **LODH-owned, org-suffixed** (`mmx.routed-order-outcome.LODH`), with **CGED granted a consume-only ACL**. Addressing **inverts**: CGED's consumer is pre-subscribed and filters `originatingLegalEntityCode ∈ {its own LEs}` before applying. The producer is blind to who is listening; the model generalises to N remote clients on one topic.

**Rationale:** Each leg is matched to its natural timing. Leg A needs bounded-time accept/reject (sync); leg B needs durable eventual delivery (async). Rejected alternatives: fully-synchronous (can't serve the deferred outcome); fully-async/event-driven (reintroduces a transient wait-state + timeout on the handshake we deliberately avoided).

**Adapter placement:** Leg A — CGED **outbound port** `RemoteRoutingGateway` (`port/out`, `mmx-application`) with a REST client adapter (`mmx-adapter-out-rest` or equivalent `adapter-out-*`); LODH **inbound REST adapter** (`mmx-adapter-in-rest`) fronting `AcceptRoutedHubOrderUseCase` (`mmx-application`). Leg B — LODH's existing outbox publishes to the org-suffixed topic; CGED gains a **new inbound Kafka consumer adapter** (`mmx-adapter-out-messaging` or a dedicated `adapter-in-messaging`) calling `ApplyRemoteOrderOutcomeUseCase`.

### D2: TradingClient membership spans organisations

A **TradingHub owns its TradingClient list, and that list may include legal entities from other Organisations.** Organisation owns legal-entity identity (CGED owns CGD; LODH owns LOC); TradingHub owns its client list (LOC's list includes CGD even though CGD is a CGED legal entity). So the same-org guard relaxes **symmetrically**: a client may point at a foreign hub (`connectedHubCode=LOC` on CGD) and a hub's client list may contain a foreign-org entity (CGD in LOC's list, in LODH).

LODH's authority anchor is LOC's TradingClient list: CGD's grants, the read-through scoping, the hub-side order's `originatingLegalEntityCode`, and the allow-list all key on CGD's **membership** in LOC's client list. LODH references CGD's identity by code + org but does not own it.

**Connection is bidirectional, wired by the connection-registration task:** CGED holds `connectedHubCode=LOC`; LODH holds CGD in LOC's client list. Both sides provisioned with credentials/endpoints. `local` vs `remote` is **derived** (`isLocalHub` / `isRemoteHub`) from whether the connected hub's org matches the deployment's org — not a stored flag.

**Rationale:** CGD is a genuine TradingClient of LOC, not a lightweight registration. The TradingClient relationship is a hub-owned membership that may span organisations. This is the domain correction from grilling issue 02. **ADR-worthy** (hard to reverse, surprising, real trade-off) — candidate for an ADR update to the tenancy model.

**Adapter placement:** the same-org guard relaxation lives in `mmx-domain` (`LegalEntity.tradingClient()` / the connection-validation domain rule). The connection-registration task lives in `mmx-bootstrap` (wiring both sides). The `isLocalHub`/`isRemoteHub` derivation lives in `mmx-application` (a domain service or the `HubScopeResolver` extension).

### D3: Remote account resolution at CGED via ExternalIdentityGateway

CGED integrates with an external **External Identity** system that maps `(client LegalEntity, client portfolioNumber, target hub LegalEntity) → hub-side portfolioNumber`. A new CGED outbound port `ExternalIdentityGateway` (`port/out`, `mmx-application`) wraps this. CGED resolves the LOC account **before** the leg-A send; the resolved account **travels in the leg-A payload**. LODH receives the already-resolved `portfolioNumber` and never queries a directory for a remote order.

**LODH does not revalidate** the CGED-supplied account — trusted by the same principle local routing trusts its own `GlobalAccountDirectory` (no re-check after resolve). Failure shifts to booking time (post-`Executed`, back office/Transactions 2 rejects) → ops fog, not MMX architecture.

**Unresolved account rejects CGED-side directly** (no round-trip): `Received→Rejected`, no hub-side order created.

**Coexistence:** remote routing resolves via `ExternalIdentityGateway` at CGED; local routing keeps its existing in-process `GlobalAccountDirectory` unchanged.

**Rationale:** The mapping is authored/held on the client side; LODH never resolves it. A new port (not a `GlobalAccountDirectory` implementation) because it has a different key (client portfolioNumber, not currency), a different location (CGED-side, pre-send), and a different trust profile.

### D4: Thin remote client — live reference-data reads, zero replication

CGED stores **zero** hub reference data (no `managed_currency`, proxy `institution`, `delegated_institution_grant`, or term/on-call rows). A remote client reads currencies / rates / grants / counterparties **live from LODH** per request via **remote-backed adapters** over the existing read ports (`ManagedCurrencyRepository`, `ProxyInstitutionRepository`, `DelegatedGrantRepository`/`Directory`, term/on-call repos). Selected when the connected hub is remote (derived `isRemoteHub`); `HubScopeResolver` still resolves `connectedHubCode`, a remote code routes subsequent reads through the remote adapter.

**Delegated grants for a remote client are mastered and stored in the LODH deployment**, keyed by CGD's `LegalEntityCode`. LODH's in-process grant check at leg-A accept (`AcceptRoutedHubOrderUseCase`) is the **one true validation**. CGED performs no authoritative pre-validation; a stale CGED view is tolerable because leg A re-checks authoritatively.

**Proxy indirection collapses for remote clients:** LODH's live read returns **hub-native institution codes**; CGD renders `"BNP via LOC"` client-side via existing `deriveDisplayName` logic. The leg-A request carries the hub-native code. Proxy institutions remain a local-routing-only concept.

**Rationale:** Because leg A is synchronous and already requires LODH to be up, caching reference data buys zero availability — if LODH is down CGD can't route anyway. A cache would only add staleness + a projection pipeline for no gain. Rate-visible UX is preserved, bounded by grant (the grant IS the exposure control).

**Adapter placement:** remote-backed adapter implementations in `mmx-adapter-out-rest` (REST clients to LODH query endpoints). LODH gains reference-data query endpoints (`mmx-adapter-in-rest`, contract-first OpenAPI). Tests: unit-test the port contract with a fake; integration-test the REST adapter.

### D5: Cross-boundary correlation — `(originatingLegalEntityCode, routingId)` + partial unique index

LODH **trusts** the CGED-minted `RoutingId` (no hub-minted second id). `RoutingId` is already globally unique by construction (`nameUUIDFromBytes("mmx-routing:" + clientOrderId)` over a `UUID.randomUUID()` client order id). The hub-side idempotency key is `(originatingLegalEntityCode, routingId)` — purely a **trust-boundary containment** guarantee: a buggy or hostile CGED can only dedupe-collide with its own prior requests, never against another client's records.

**Exactly-one enforced by a DB constraint, not a read-check.** The current read-then-write is safe only inside one DB/transaction; across the boundary two concurrent leg-A retries can both pass the read-check and both insert. Add a **partial unique index on `(originating_legal_entity_code, routing_id) WHERE originating_legal_entity_code IS NOT NULL`** (routed hub-side orders only; local desk orders keep `null` and are untouched).

`AcceptRoutedHubOrderUseCase` **catches the unique-violation and resolves to the already-persisted hub-side order**, returning the same accept/`Routed` outcome — genuinely idempotent under at-least-once. The pre-read stays as a cheap fast-path; the index is authoritative.

**Cross-boundary correlation is `(originatingLegalEntityCode, routingId)` only** — no internal order UUID crosses the boundary. Leg-B message carries this key; CGED's consumer resolves the client-side order by `(originatingLegalEntityCode=self, routingId)`.

**Adapter placement:** Flyway migration in `mmx-adapter-out-persistence`; the unique-violation catch in `AcceptRoutedHubOrderUseCase` (`mmx-application`). Tests: unit-test idempotency (unique-violation → success); integration-test the partial index.

### D6: Dedicated remote inbound use cases + hub-side propagation bypass

**LODH gains `AcceptRoutedHubOrderUseCase`** (leg-A inbound). Distinct from PM `IntakeUseCase` because a routed inbound order's authority is the **grant**, not the hub's own intake enablement. It: validates `(institution, currency, tenor|notice)` against CGD's grant in-process using LOC's own reference data; creates the hub-side order (`MoneyMarketOrder.createHubSideFromRouting`); emits the leg-B `ACCEPTED` event same-tx (outbox row committed in the same tx as hub-side order creation); returns accept/reject synchronously.

**CGED gains `ApplyRemoteOrderOutcomeUseCase`** (leg-B inbound). Symmetric counterpart to `AcceptRoutedHubOrderUseCase`. Owns: load-by-`(originatingLegalEntityCode=self, routingId)`, the idempotency check, broken-pair handling, and the apply. The Kafka consumer adapter is a thin decode-and-call shell. The existing `RoutedOrderOutcomePropagationService` is **not reused** — it is hub-side with a throw-on-missing contract and a throw-on-already-terminal that local propagation depends on; forcing remote semantics into it would change local behaviour.

**Idempotency mirrors D5:** a client order already in the event's expected terminal/non-terminal state → **no-op ack** (offset advances); only a mismatched terminal is an error. This is critical because today's `propagate*FromHub` → `status.transitionTo(...)` throws on already-terminal — correct for in-process local propagation (called once), but a poison-message trap under at-least-once Kafka.

**Hub-side propagation bypassed for remote pairs.** LODH's `RoutedOrderOutcomePropagation` port (today: `requireClientOrder` → `findRoutedClientOrderByRoutingId` in the same DB → throw `RoutedOrderPairIntegrityException` on missing) does **not** run when `originatingLegalEntityCode`'s org ≠ the hub's org. The hub execute/cancel/reject transaction persists only the hub-side terminal transition + the leg-B outbox row; it never looks up the client. Local pairs keep the existing in-process atomic propagation unchanged.

**Rationale:** LODH cannot see CGED's order table, so running the propagation port would throw spuriously on every remote terminal. The bypass is the mechanism by which atomic pair-integrity is relaxed (ADR-0002) — the hub commits independently; the client catches up via leg B.

### D7: Silence is never terminal — leg B authoritative, gateway retry, no new state

**Locked principle:** a remote client-side order in `Received` is **never** auto-terminalized on transport silence. The only closers are: (a) confirmed accept signal → `Routed`; (b) confirmed business reject → `Rejected`; (c) confirmed hub cancel → `Cancelled`. Transport silence stays `Received`; the bidirectional-partition residual is a DR / out-of-band reconciliation concern, not a state transition.

**Leg B is the authoritative lifecycle mirror; leg A is the latency fast path.** For "silence is never terminal" to hold without indefinite `Received` on the common case (leg-A response lost, LODH healthy), leg B must close `Received→Routed` itself. Therefore leg B carries a non-terminal **`ACCEPTED`** event, emitted from `AcceptRoutedHubOrderUseCase`'s transaction (outbox row committed same-tx as hub-side order creation — the accept signal is exactly as durable as the hub-side order). Payload: `(originatingLegalEntityCode, routingId, acceptedAt)`. Consumer: `ApplyRemoteOrderOutcomeUseCase` applies `Received→markRouted`; already-`Routed` (leg A delivered first) → no-op ack. **Benign redundancy** accepted (leg-B ACCEPTED no-ops when leg A already delivered).

**Gateway-owned retry for leg-A transients (edge 8).** `RemoteRoutingGateway` owns indefinite retry + backoff + circuit-breaker, transparent to the use case. The use case calls `gateway.route(request)` and observes only: (a) a confirmed accept/reject → flip the order; or (b) circuit-breaker open → order stays `Received` + ops signal (alert, not a state transition). The deterministic routing id (D5) makes retry safe: LODH dedupes via the partial unique index. Circuit-breaker on sustained unreachability backs off hard and emits the signal; when the circuit re-closes, retries resume automatically. No domain state for retry — retry/backoff/circuit are infrastructure-owned metadata.

**Routing-failure reject is HTTP-only.** A leg-A routing-failure reject (grant/currency/tenor invalid at LODH accept) creates **no hub-side order** → nothing to mirror → no leg-B event. The HTTP reject response closes `Received→Rejected`. If the HTTP response is lost, the gateway retries (edge 8) or the order stays `Received` (edge 7 / DR). Trader-reject (origin=`TRADER`) rides leg-B `REJECTED` — always a hub-side order transitioning to terminal, which leg B genuinely mirrors.

**No new `OrderStatus` value.** The state machine stays `Received → Routed → Executed | Rejected | Cancelled → Accounted`. `Received` covers all pre-confirmed-signal conditions (in-flight, circuit-open, awaiting leg-B). These are operational metadata, not domain state. A future implementer who feels the urge to add `Pending`/`InFlight`/`Retrying` should re-read this decision.

**ADR-0006 candidate:** "silence is never terminal + leg B authoritative + gateway-owned retry" — ADR-worthy (hard-to-reverse: shapes the whole failure model; surprising without context; real trade-off vs auto-reject-on-timeout). Captured here; elevates to `docs/adr/0006` when this change lands.

### D8: Trust boundary — transport-proven identity, trust authoritative resolution

**Leg A authn:** the transport credential binds to exactly one remote legal entity; the gateway (LODH's inbound counterpart to CGED's `RemoteRoutingGateway`) maps credential → `originatingLegalEntityCode` and hands `AcceptRoutedHubOrderUseCase` a **proven** principal. The use case never trusts a payload-claimed identity. This mirrors MMX's existing identity model (`X-User-Id` and session scope are all transport-proven).

**Leg B authz:** the broker ACL IS the authz (CGED consume-only on LODH's org-suffixed topic); Kafka client authn (SASL/SSL) is infra.

**Allow-list = TradingClient membership** (D2), checked against the proven identity. Defense-in-depth: the gateway early-rejects unknown credentials; the use case re-checks membership as a domain rule (testable, transactional).

**Trust model (symmetric, locked):** trust identity, intent, and authoritative resolution (account via D3, routingId via D5); validate only what LODH masters (grant). LODH needs no account-validation port for the remote path — the local `GlobalAccountDirectory` stays a local-resolution seam.

**Credential granularity:** one credential per remote legal entity (V1-moot — CGD is the only CGED entity routing to LOC). The specific mechanism (mTLS client cert, per-client signing key, OAuth2 client-credentials) is infra/platform, decided at implementation.

**ADR-0007 candidate:** "cross-org trust boundary" — ADR-worthy (surprising-without-context: "trusts a foreign org's account assertion" reads as a security hole without the "authoritative resolver" rationale). Captured here; elevates to `docs/adr/0007` when this change lands.

**ADR-0002 relaxation:** the atomic pair-integrity guarantee (throw + rollback inside the hub's execute tx) is explicitly relaxed to **detect + alert** across deployments. Locally the throw fires inside the hub's execute tx and rolls back the hub terminal; remotely that is impossible — LODH is already terminal before CGED sees the event. The remote broken-pair is detected after the fact (in `ApplyRemoteOrderOutcomeUseCase`), not prevented. This change implements basic safety (throw on missing/mismatched client); the DLQ + ops-alert formalization is deferred to the hardening change.

## Risks / Trade-offs

- **[Risk] Leg-A response lost + leg-B ACCEPTED delayed** — the client-side order stays `Received` longer than the happy path. Mitigation: leg B ACCEPTED is emitted same-tx as hub-side order creation (if the order exists, the row exists); the outbox relay drains on its normal schedule. Consumer-lag monitoring (ops fog) catches a stalled consumer.
- **[Risk] Remote broken-pair detected after the fact** — LODH is terminal, CGED cannot mirror, reconciliation is an ops act. Mitigation: `ApplyRemoteOrderOutcomeUseCase` throws `RoutedOrderPairIntegrityException` on missing/mismatched client (basic safety this change); DLQ + alert formalized in hardening. Accepted trade-off: the alternative (2PC/distributed-tx) was rejected at charting.
- **[Risk] Two concurrent leg-A retries pass the read-check** — the in-transaction read-then-write is unsafe across the boundary. Mitigation: the partial unique index (D5) is authoritative; `AcceptRoutedHubOrderUseCase` catches the violation idempotently.
- **[Risk] `ApplyRemoteOrderOutcomeUseCase` idempotency is easy to mis-implement** — the existing throw-on-already-terminal is a poison-message trap under at-least-once. Mitigation: explicit unit test asserting already-terminal-in-expected-state → no-op ack; mismatched terminal → error.
- **[Trade-off] Leg-B ACCEPTED benign redundancy** — when leg A delivered accept (common case), the leg-B ACCEPTED no-ops. Accepted: the price of leg B being authoritative rather than advisory; acceptable at money-market order volume.
- **[Trade-off] Thin client requires LODH up for order-form rendering** — a remote client cannot render the order form when LODH is down. Accepted: leg A is sync and requires LODH up anyway; caching would add staleness for zero availability gain.
- **[Trade-off] Account failures surface at booking time, not at routing** — LODH trusts the CGED-resolved account; a bad mapping surfaces post-`Executed`. Accepted: manual unwind runbook (ops fog); the alternative (LODH revalidates) was rejected as hub-thickening.

## Migration Plan

1. **Domain:** relax the same-org guard in `LegalEntity.tradingClient()`; add remote routing domain concepts (ports, value objects, remote-pair detection, silence rule). Local path untouched.
2. **Persistence (Flyway, additive):** partial unique index on `(originating_legal_entity_code, routing_id) WHERE originating_legal_entity_code IS NOT NULL`. No new columns (existing `routing_id` + `originating_legal_entity_code` suffice). No data backfill.
3. **Contracts (contract-first, BACKWARD-compatible):** new LODH inbound REST OpenAPI for routed-order accept + reference-data queries (+ `api-v1.md` mirror); new AsyncAPI for the leg-B outcome topic (`ACCEPTED`/`EXECUTED`/`CANCELLED`/`REJECTED` with type discriminator) (+ `asyncapi-v1.md`); Schema Registry BACKWARD-compatible registration.
4. **Application + adapters:** implement `RemoteRoutingGateway`, `ExternalIdentityGateway`, `AcceptRoutedHubOrderUseCase`, `ApplyRemoteOrderOutcomeUseCase`, remote-backed read adapters, hub-side propagation bypass, gateway retry/circuit-breaker, Kafka consumer.
5. **Bootstrap:** wire all new ports/adapters; connection-registration task (both sides + credentials).
6. **Rollback:** migrations are additive (index only); revert code; local routing is untouched; existing `OrderExecutedV1` contract unchanged. Remote routing simply stops functioning (no data corruption — remote orders were never created).

## Open Questions

(None — all architecture decisions resolved during the wayfinder grill, issues 01–08. Deferred items are listed in Non-Goals and tracked in the hardening change.)
