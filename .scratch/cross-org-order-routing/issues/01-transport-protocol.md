<!-- label: wayfinder:grilling -->
Status: closed (resolved 2026-07-13)
Blocked by: (none — frontier)

# Transport & protocol for cross-deployment routing

## Question

How do the CGED and LODH deployments actually exchange routing traffic — the outbound routing request (CGD → LOC) and, later, the return channel? Decide the **backbone** the whole async path hangs on:

- Synchronous request/response (REST call from CGED to a LODH intake endpoint) vs asynchronous messaging (queue/bus / outbox+webhook)?
- Does this reuse or mirror the **existing back-office LOC→PAR broadcast pattern** (see CONTEXT *Back office* / *Execution event for a routed order*), or is order routing a distinct channel from contract broadcast?
- Delivery guarantees (at-least-once + idempotency vs exactly-once illusion), ordering, and ret/backoff expectations.
- Where the seam sits in hexagonal terms: a new outbound port on CGED (e.g. `RemoteRoutingGateway`) + a new inbound adapter on LODH.

This is the frontier root: correlation/idempotency, the reverse outcome path, back-office broadcast, and trust boundary all build on the transport choice.

## Resolution (2026-07-13)

**Backbone = hybrid.** Two legs, each matched to its natural timing:

- **Leg A — routing request (CGD → LOC): synchronous REST handshake.** CGED calls a LODH inbound endpoint and gets an immediate accept/reject. This gives the client-side order a definitive `Received → Routed` (on accept) or `Received → Rejected` (on reject) in bounded time, honouring framing given #4 (client stays `Received` until the hub confirms the hub-side order exists) with no "stuck in `Received`, needs a timeout" state. Rejected the fully-synchronous and fully-async/event-driven alternatives: sync-only can't serve the deferred outcome; async-only would reintroduce a transient wait-state + timeout on the handshake we deliberately avoided.
- **Leg B — outcome return (LOC → CGD): asynchronous, reusing the existing transactional-outbox + Kafka relay.** Execution can happen much later and must survive restarts, so it rides the durable outbox pattern MMX already trusts (`BackOfficeOutboxRelayWorker`: durable row → scheduled drain → at-least-once with retry cap).

**Return transport = shared broker (Kafka), not point-to-point callback.** Initially recommended point-to-point HTTPS (fault isolation + trust boundary). Reversed on two load-bearing facts supplied during grilling: (1) all org deployments sit in one cloud provider (makes the network objection to either option moot); (2) **a shared broker already exists** — so Kafka doesn't *reintroduce* a shared failure domain (it's already accepted), and it lets us reuse the existing outbox relay rather than build a new HTTP-POST relay + inbound controller + bespoke retries.

**Topic ownership (broker governance rule).** A topic has exactly one owning deployment and the **OrganisationCode is a suffix** on the topic name. The outcome is produced by LODH and consumed by CGED → the topic is **LODH-owned, org-suffixed** (e.g. `mmx.routed-order-outcome.LODH`), with **CGED granted a consume-only ACL**. Consistent with the producer-owns-topic pattern already in use. Only one topic is needed (outcomes flow LODH→CGD only); no CGED-owned topic, because leg A is sync REST and a TradingClient never initiates a cancel (the hub cancels the hub-side order to stop an in-flight order — CONTEXT grant-lifecycle).

**Seam (hexagonal).**
- Leg A: CGED **outbound port** `RemoteRoutingGateway` (sync REST client adapter) → LODH **inbound REST adapter** → **new dedicated `AcceptRoutedHubOrderUseCase`** → `MoneyMarketOrder.createHubSideFromRouting`, returning accept/reject synchronously. Chosen distinct from the PM `IntakeUseCase`/`IntakeService` because a routed inbound order's authority is the **grant**, not the hub's own intake enablement (CONTEXT *Grant vs hub own-intake enablement*); overloading PM intake would tangle two authorities. Mirrors the existing local split (`receiveHub` vs `RoutedOrderIntake`), now across a network seam.
- Leg B: LODH's existing outbox publishes the outcome to the LODH-owned topic → CGED gets a **new inbound Kafka consumer adapter** → a use case that applies the outcome to the client-side order.

**Deliberately out of this ticket (handed off):**
- *What LODH validates/trusts* about an inbound routed order, and *how CGD obtains reference data/grants* → [02 — Remote client reference-data & delegated grants access](02-remote-client-reference-data.md).
- *Idempotency/dedupe key and correlation* across the boundary (at-least-once on both legs needs it) → [04 — Routing correlation & idempotency](04-routing-correlation-idempotency.md).
- *Applying the outcome to the client-side order* and the eventual-consistency semantics → [05 — Outcome propagation back to the remote client](05-outcome-propagation-back.md).
- *Cross-org topic ACL as the segregation control; authn on the leg-A endpoint* → [07 — Trust & security boundary](07-trust-security-boundary.md).
