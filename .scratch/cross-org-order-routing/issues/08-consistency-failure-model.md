<!-- label: wayfinder:grilling -->
Status: resolved
Blocker cleared: 05-outcome-propagation-back (amendment 2026-07-26)

# Consistency & failure model for remote routing

## Question

With atomicity gone across the boundary, define the **end-to-end consistency contract** for a remote routed pair so the design is implementable and operable:

- The full client-side state machine for remote routing: `Received` → (hub accepts) `Routed` → terminal — plus how each failure edge is handled (transport failure before hub accept, hub reject, hub-accept-but-outcome-lost, unresolved global account).
- Compensation/timeout: if the outbound routing request never confirms, how does the client-side order avoid being stuck in `Received` forever — ret/backoff, then `Rejected` with a routing reason?
- How a remote failure becomes client-side `REJECTED` given the reject may originate at either side.
- Whether any transient/in-flight state is needed (deferred at charting — decide here) and the reconciliation story for a half-terminal pair (feeds the ops fog in the map).

## Resolution

### Scope

08 is a **consolidation** ticket. The amendment to 05 ("silence is never terminal" + leg B authoritative) resolved the load-bearing question 08 was charted to answer ("how does the client-side order avoid being stuck in `Received` forever"). 08's job is now to (a) write the consolidated end-to-end contract as a coherent table inheriting 01–06 + the amendment, and (b) close the remaining gaps the consolidation surfaces. No new architecture beyond what prior tickets decided — this is the contract written down.

### Client-side state machine (remote)

```
   Received ──(hub accepts: leg-A response OR leg-B ACCEPTED)──▶ Routed
       │                                                              │
       │ (CGED-side unresolved account: no round-trip)                │
       │ (LODH intake routing-failure: leg-A HTTP reject)             │ (hub terminal: leg-B event)
       ▼                                                              ▼
    Rejected                                              Executed / Rejected / Cancelled
   (origin=ROUTING_FAILURE)                               (origin=TRADER for Rejected)
```

No new transient/in-flight state — see *Open* thread 3 (pending confirmation). `Received` covers every pre-confirmed-signal condition; `Routed` is the acceptable unbounded wait-state per 05 decision 2.

### Failure-edge contract (all edges)

| # | Edge | Client-side state | Closer | Source |
|---|---|---|---|---|
| 1 | CGED can't resolve global account *before send* | `Received` → `Rejected` | No round-trip; CGED rejects directly (origin=`ROUTING_FAILURE`) | 03 |
| 2 | LODH intake routing-failure (grant/currency/tenor invalid at accept) | `Received` | HTTP-only reject response → `Rejected` (origin=`ROUTING_FAILURE`). **No leg-B event** (no hub-side order created → nothing to mirror). If HTTP lost → gateway retry (edge 8); if perpetually lost → edge 7 (DR). | 08 (coherence-review correction of 05 amend) |
| 3 | Leg-A accepted at LODH, HTTP response lost | `Received` | Leg-B `ACCEPTED` → `Routed` (idempotent no-op if leg A already delivered) | 05 amend |
| 4 | Hub trader rejects | `Routed` | Leg-B `REJECTED` (origin=`TRADER`) → `Rejected` | 05 + origin |
| 5 | Hub trader executes | `Routed` | Leg-B `EXECUTED` → `Executed` | 05 |
| 6 | Hub cancels | `Routed` | Leg-B `CANCELLED` → `Cancelled` | 05 |
| 7 | Bidirectional partition (both legs down past recovery) | unchanged | DR / out-of-band reconciliation — **not** a state transition | 05 amend |
| 8 | Leg-A call transiently fails (timeout / 5xx / blip) | `Received` (unchanged) | **Gateway-owned indefinite retry + backoff + circuit-breaker** (below) | 08 (this decision) |

### Decision — edge 8: leg-A retry owned by the gateway (2026-07-26)

**Retry is owned by `RemoteRoutingGateway` (the outbound port from ticket 01), transparent to the application use case.** The use case calls `gateway.route(request)` and observes only one of: (a) a confirmed accept/reject outcome → flip the order accordingly, or (b) retries exhausted past a circuit-breaker threshold → the order stays `Received` and an ops signal is emitted (alert, **not** a state transition).

Properties:
- **Indefinite retry with backoff** — the deterministic routing id (ticket 04) makes retry safe: LODH dedupes via the partial unique index on `(originatingLegalEntityCode, routingId)`, so a retry can never create a duplicate hub-side order; a retry against an already-created order returns the existing accept idempotently.
- **Circuit-breaker on sustained LODH unreachability** — when the hub is unreachable for a sustained period, the gateway backs off hard (don't hammer a dead hub) and emits the ops signal; it does **not** terminalize the order. When the circuit re-closes (LODH returns), retries resume automatically. This keeps "silence is never terminal" operationally viable rather than a slogan.
- **No domain state for retry** — retry attempts, backoff schedule, and circuit state are infrastructure-owned metadata (metrics/logs), never order state. The use case never sees "retrying" as a domain concept.
- **Terminal closers for edge 8** are, and only: leg A eventually succeeds (→ `Routed` or `Rejected`); a leg-B event arrives proving LODH has the order (→ `Routed`/`Rejected`); or DR for true bidirectional partition (edge 7). Transport silence alone never closes `Received`.

This decision operationalizes the amendment's "silence is never terminal" for the leg-A direction, and folds into the **ADR-0006 candidate** ("silence is never terminal + leg B authoritative + gateway-owned retry as the leg-A closer") rather than standing as a separate ADR.

### Decision — thread 2: no domain-level desync sweep (2026-07-26)

Pair-integrity detection stays **purely event-driven** (at apply time in `ApplyRemoteOrderOutcomeUseCase`, per 05 decision 4). No domain-level periodic reconciliation sweep is added.

The "leg B never fired" case (LODH terminal, CGED consumer down / broker partitioned / LODH outbox relay stalled) is covered by three existing infra-layer signals, not by domain architecture:
- **Kafka consumer-lag monitoring** (CGED) — catches a down/lagging consumer.
- **Broker health monitoring** — catches broker partitions.
- **Outbox-table age monitoring** (LODH) — catches a stalled outbox relay (rows older than threshold T).

As long as these three are operational, there is no "half-terminal pair that nobody noticed" gap: apply-time integrity catches apply failures, infra monitoring catches delays, DR (edge 7) catches catastrophic data loss. A "safety net" domain sweep would duplicate infra monitoring without being able to definitively detect desync from CGED's own data (CGED can't know whether LODH is terminal — it would only surface stale-`Routed`, which is an ops dashboard query, not a mechanism).

Boundary: pair-integrity at apply time = **architecture** (in scope). The three infra signals + stale-`Routed` dashboards = **ops SLO work** (the fog). Catastrophic loss = **DR** (edge 7).

### Decision — thread 3: no transient/in-flight state (2026-07-26)

The client-side `OrderStatus` enum gains **no new value** for the remote path. The state machine stays exactly:

```
Received → Routed → Executed | Rejected | Cancelled → Accounted
```

`Received` covers all pre-confirmed-signal conditions (leg A in flight; leg A lost with circuit open; leg A succeeded at LODH, awaiting leg B). These are distinguished only as operational metadata (gateway metrics/logs, consumer-lag monitoring), never as domain state. Retry attempt count, circuit-breaker state, and consumer position are infrastructure-owned.

Rationale: the domain state machine encodes *business* facts ("does a hub-side order exist?" → `Routed`; "was it executed?" → `Executed`). Infrastructure timing ("are we retrying?") is not a business fact. This mirrors 05 decision 2's treatment of `Routed` as an acceptable unbounded wait-state rather than splitting it into timed sub-states. A future implementer who feels the urge to add `Pending`/`InFlight`/`Retrying`/`AwaitingHubConfirm` should re-read this decision and 05 dec 2 first.

---

**Resolution complete (2026-07-26).** All three threads closed; all eight failure edges have defined closers; the consistency contract is fully specified. Notable: 08 was charted expecting "ret/backoff, then `Rejected` with a routing reason" as the timeout answer; the actual answer — surfaced by grilling 01's "no stuck-in-`Received`" claim — is **never `Rejected` on silence** (the amendment), with gateway-owned retry as the leg-A closer and leg B as the authoritative lifecycle mirror. 08's decisions fold into the **ADR-0006 candidate** alongside the amendment.
