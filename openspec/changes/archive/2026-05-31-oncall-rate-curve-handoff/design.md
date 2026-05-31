## Context

OnCall rates form a **value-dated curve** per institution. A curve point is keyed by **`(institution, currency, noticePeriod)`** (**O-01**). When an institution notifies the desk of a new rate, the trader records a new **segment** with a value date, the desk **notifies the back office (BO)**, and the BO refreshes **contracts in life** with that institution. This reuses the transactional **outbox + Kafka relay** and **inbound HTTP callback** pattern already shipped for `back-office-accounting-handoff` (outbound `OrderExecutedV1` + inbound `/accounted` callback).

Programme context: Phase 3 of hybrid C — see [`../institution-onboarding-rates/PROGRAMME.md`](../institution-onboarding-rates/PROGRAMME.md). Decisions **O-01–O-05** are locked in [`../institution-onboarding-rates/OPEN-DECISIONS.md`](../institution-onboarding-rates/OPEN-DECISIONS.md).

## Goals / Non-Goals

**Goals:**

- Value-dated **OnCall rate segments** per curve point with a **three-state lifecycle** and provisional supersede.
- Trader REST + UI to add a rate (with value date) and cancel a still-pending rate, institution-scoped.
- **Contract-first** outbound `OnCallRateUpdatedV1` (AsyncAPI) written to the outbox in the same transaction as the segment, relayed to Kafka; **contract-first** inbound BO confirmation callback (OpenAPI), idempotent.

**Non-Goals (phase 3):**

- Term CSV upload (phase 2) and full historical curve charts / analytics.
- PM-initiated rate feeds.
- BO **rejection** path (BO cannot reject — O-03).
- Any BREAKING order-lifecycle change.

## Decisions

### 1. Segment lifecycle (O-03) — three states, Option A supersede

```
   trader adds rate                BO ack
   (valueDate V ≥ today)          (keyed by segmentId)
          │                            │
          ▼                            ▼
   ┌──────────────────┐  confirm  ┌─────────┐
   │ PENDING_         │──────────▶│  VALID  │  in-life contracts refreshed by BO;
   │ CONFIRMATION     │           └─────────┘  segment immutable
   │ (notified BO;    │
   │  awaiting ack)   │  ◄── at most ONE PENDING per (institution, currency, noticePeriod)
   └────────┬─────────┘
            │ trader cancels (legal only here)
            ▼
   ┌──────────────────┐
   │ CANCELED         │  cancel notification sent; prior segment end → 2999-12-31
   └──────────────────┘  (or, for a first-ever segment, the pending segment is discarded)
```

- **Cancel** is legal **only** from `PENDING_CONFIRMATION`. The BO **cannot reject**; `PENDING` resolves only to `VALID` or `CANCELED`.
- **One pending segment per curve point** at a time — a second add is blocked until the current pending segment resolves.
- **Pending ≠ inactive for pricing.** A `PENDING_CONFIRMATION` segment is **already active for pricing new orders** whose value date falls within `[valueDate, …]`. The `PENDING_CONFIRMATION` status is a trader-facing signal of whether the **BO has refreshed in-life contracts** — not a gate on new-order pricing.

### 2. Dates & supersede (O-02) — inclusive, provisional (Option A)

- Dates are **inclusive**. The open (last) segment ends at sentinel **`2999-12-31`** (no-end).
- Adding a rate with `valueDate = V` sets the prior segment's end to **`V − 1`**. This supersede is **provisional** (Option A): it is applied at *propose* time, while the new segment is `PENDING_CONFIRMATION`, and **reverts to `2999-12-31` on cancel**.
- `valueDate ≥ today` (no backdating). **No further ordering restriction** relative to the prior segment — the curve reflects the institution's actual rates.
- **First-ever segment** for a curve point spans `[valueDate, 2999-12-31]`; there is no prior segment to supersede, and cancel simply discards the pending segment.

### 3. Addressing & contracts (O-01, O-04, O-05)

- **`segmentId`** (UUID) is the stable identity: the Kafka **record key** and the **address** the BO confirmation callback targets — mirroring how `orderId` keys the accounting handoff.
- **Outbound `OnCallRateUpdatedV1`** (thin delta — O-04/O-05):

```
OnCallRateUpdatedV1 {
  eventType:    "OnCallRateUpdatedV1"
  segmentId:    <uuid>
  institution:  <institutionCode>
  currency:     <code>
  noticePeriod: <…>
  rate:         <decimal>
  valueDate:    <date, inclusive start>
}
```

  No `priorEndDate` — it is **implicit** (a segment ends the day before the next starts; the BO derives `prior.end = valueDate − 1`).
- **Inbound confirmation** is a minimal **ack keyed by `segmentId`** (body optional; mmx stamps its own `validatedAt`), and is **idempotent** — a duplicate ack for an already-`VALID` segment is a no-op (mirrors the accounting callback).

### 4. Reuse of the outbound/inbound handoff pattern

| Accounting handoff (shipped) | OnCall rate handoff (this change) |
|---|---|
| Outbox row in same txn as `EXECUTED` | Outbox row in same txn as segment persist |
| Relay → `mmx.order.executed` (`OrderExecutedV1`) | Relay → OnCall channel (`OnCallRateUpdatedV1`) |
| Inbound `POST /back-office/orders/{orderId}/accounted` | Inbound BO confirmation callback keyed by `segmentId` |
| `handoffStatus`: PENDING → PUBLISHED → ACCOUNTED | segment: PENDING_CONFIRMATION → VALID (cancel branch is mmx-internal) |

### 5. Cancel/confirm race — atomic confirmation, not a pre-check

A trader can cancel while the BO is mid-confirm. Rather than have the BO call a **synchronous pre-check** endpoint ("is this segment canceled?") before confirming, the **confirmation endpoint itself is an atomic compare-and-set** inside mmx's transaction:

```
BO ──confirm(segmentId)──▶ mmx (single transaction):
        PENDING        → set VALID, return 200
        CANCELED       → return 409  (no transition)
        unknown id     → return 404
        already VALID  → return 200  (idempotent no-op)

BO: impact in-life contracts ONLY after a 200  (confirm-first, impact-second)
```

- A separate pre-check is **rejected**: it leaves a **TOCTOU** window (trader can cancel between check and confirm), adds a redundant round-trip, and reintroduces a runtime read dependency that the outbox pattern deliberately avoids. mmx is the system of record, so folding the check into the transition removes the race entirely.
- Whichever of `{cancel, confirm}` reaches mmx's guard first wins; the loser gets a clear status code. This mirrors the existing accounting callback returning **409 `INVALID_STATUS_TRANSITION`** for wrong-state orders.
- The **async cancel notification** (see spec) is therefore an **early-discard / observability** signal for the BO, not the correctness mechanism.

### 6. Inbound confirmation transport — synchronous HTTP + idempotent retry

The BO confirmation is delivered as a **synchronous HTTP callback** (contract-first OpenAPI), not an async event. This is an intentional asymmetry, consistent with the shipped accounting handoff:

```
 OUTBOUND  mmx ──▶ BO   : mmx's write path must NOT depend on BO  →  async (outbox + Kafka)
 INBOUND   BO  ──▶ mmx  : BO writes its decision into mmx          →  synchronous HTTP
```

- The inbound call is BO **writing a decision (a command)**, not BO **reading facts** — so it does not violate the capability rule that consumers must not call mmx HTTP to obtain facts.
- The dependency is on **mmx's own availability**, which mmx controls — not on a third party. It never threatens mmx's ability to record rates or price orders (the outbox already guarantees mmx never blocks on BO).
- **Resilience contract:** confirmation is **idempotent**; the BO **retries with backoff** on `5xx`/unavailable; terminal codes (`200/409/404`) tell the BO when to stop. While mmx is down the segment stays `PENDING_CONFIRMATION` and **still prices new orders**, so mmx downtime degrades to **delayed validation**, not a business stall.

**Async-event confirmation considered and rejected:** publishing the confirmation as an event would decouple availability but (a) lose the synchronous `200/409` answer that makes the §5 confirm-first/impact-second rule race-free, reopening the cancel/confirm race as a compensation problem, and (b) add a new inbound topic, consumer, schema, and idempotency surface in mmx. Not worth it for a single BO consumer in this POC.

## Open consequences / risks

- **Cancel after new-order pricing.** Because a pending segment prices new orders immediately, an order priced during the pending window on a rate that is later **canceled** carries a rate that was withdrawn before BO confirmation. Acceptable per O-03 (the rate was "active"); flagged here so the trader UX makes the pending state visible.
- **Provisional boundary visibility.** Under Option A the curve shows `prior.end = V − 1` before confirmation; read models should expose the segment's `PENDING_CONFIRMATION` status so consumers can distinguish a confirmed boundary from a pending one.
- **BO must defer the contract impact** until after a successful `200` confirm for §5 to hold. If the BO instead impacts contracts on *receipt* of the update, no design prevents acting on a later-canceled rate — the cancel notification then drives a BO-side rollback. Nail this down with the BO team (heart of O-05).

## References

- Pattern: [`openspec/specs/back-office-accounting-handoff/spec.md`](../../specs/back-office-accounting-handoff/spec.md), [`openspec/specs/back-office-outbound-messaging/spec.md`](../../specs/back-office-outbound-messaging/spec.md)
- Decisions: [`../institution-onboarding-rates/OPEN-DECISIONS.md`](../institution-onboarding-rates/OPEN-DECISIONS.md) O-01–O-05
- Contracts (canonical): `specs/002-trader-orders-views/contracts/asyncapi.yaml` (async), `.../openapi.yaml` (sync)
