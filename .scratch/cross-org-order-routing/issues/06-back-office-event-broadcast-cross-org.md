<!-- label: wayfinder:grilling -->
Status: closed (resolved 2026-07-26)
Blocked by: (none — unblocked by 01, now on the frontier)

# Back-office event & contract broadcast across organisations

## Question

For a routed trade MMX emits **one** `OrderExecutedV1` from the hub-side order with a routing-context block; the back office creates the LOC contract and **broadcasts to the client (PAR) back-office instance** using `routingId` (creation, reversal, replace all follow LOC→PAR). Today "client" = PAR, same Organisation as LOC.

Decide what changes when the client is **CGED**, a different organisation:

- Does the hub-side `OrderExecutedV1` + routing context stay the single source, with the broadcast target becoming **LOC → CGED** instead of LOC→PAR?
- Is the back-office broadcast an existing cross-instance channel we inherit, or does cross-*org* introduce a new trust/routing concern for the back office too?
- `routingId` correlation across two orgs' back-office instances (depends on ticket 04's correlation decision).
- Confirm this stays back-office-internal (MMX order status unaffected; no new inbound MMX callbacks for reversal/replace) as today.

## Resolution (2026-07-26)

**Nothing changes in MMX. The cross-org back-office broadcast is a Deposits back-office programme concern, out of MMX's scope.**

Grilling opened with the one load-bearing question — the Deposits back-office deployment topology relative to Organisation (does cross-org introduce a new cross-instance channel, or is it intra-app routing?) — and got a decisive directive: **nothing changes for MMX.** That answer reframes the ticket from "what should MMX do differently?" to "confirm MMX's contract is already sufficient and the cross-org work is elsewhere." It locks the following:

1. **Single source unchanged.** MMX keeps emitting **exactly one** `OrderExecutedV1` from the **hub-side order** with the existing **routing-context block** (`routingId`, `originatingLegalEntityCode`, `clientOrderId`, `clientPortfolioNumber`, `clientCounterparty`) — exactly as today (CONTEXT *Execution event for a routed order*; ADR-0003; `schemas/OrderExecutedV1.json`). No new event, no new field, no new MMX channel. The broadcast target becomes LOC → CGED **on the back-office side only**; MMX does not participate in the broadcast — ADR-0003 already establishes the back office "never reads or writes the MMX order DB", and the LOC→PAR broadcast is and remains back-office-internal (CONTEXT L225, L248).

2. **Cross-org targeting uses data MMX already emits.** `originatingLegalEntityCode` is globally unique across organisations (CONTEXT L156), so the Deposits back office can map it → Organisation → the target org's back-office instance with **no new MMX-supplied field**. The routing context MMX already publishes is sufficient; whatever the back-office topology is (per-org, shared, other) is the Deposits programme's decision, not MMX's.

3. **Correlation key inherited from 04 — unchanged.** Back-office broadcast (creation, reversal, replace) correlates by **`(originatingLegalEntityCode, routingId)`** — the cross-boundary key 04 locked. No hub-internal order UUID crosses the *org boundary* into the back office (the MMX→BO hop within LODH still carries `clientOrderId` intra-LODH per decision 1; the cross-org LODH-BO→CGED-BO broadcast strips it and keys on `(originatingLegalEntityCode, routingId)`). This is the explicit handoff from 04: *"Back-office broadcast target keyed by `(originatingLegalEntityCode, routingId)` → 06"* — inherited, not re-decided.

4. **Stays back-office-internal — no new inbound MMX callbacks.** Reversal and replace are contract-lifecycle events in the back office; MMX order status is unaffected and `Accounted` stays terminal, exactly as today (CONTEXT *Contract reversal / replace*; ADR-0003 L15). The existing `accounted` callback is unchanged in **contract**: each MMX deployment receives `accounted` calls only for orders it owns — LODH MMX for the hub-side `orderId`, **CGED MMX** for the client-side `orderId`. Directing the client-side call at CGED's MMX (rather than LODH's) is a back-office routing matter, not an MMX change.

**Why the model holds together without an MMX change:** MMX is deployed one instance per Organisation, and each instance only ever owns — and receives callbacks for — its own orders. Every cross-org coordination step (broadcasting contract creation/reversal/replace to CGED's back office; booking in each org's Transactions 2; routing each `accounted` callback to the owning MMX) lives in the Deposits back-office programme. MMX's contract — one hub-side `OrderExecutedV1` + routing context, and the per-order `accounted` callback — is already sufficient and is left as-is.

**Handoff / out of scope for this map:**
- The Deposits back-office **cross-org broadcast channel itself** (however the back office is deployed) is implementation work in the Deposits programme, beyond this map's MMX-focused destination. Recorded in the map's **Out of scope**.
- The client-side `accounted` callback now landing on CGED MMX is a system-level routing fact, not an MMX contract change; it is consistent with 05's cross-deployment outcome model and needs no separate ticket here.

**Spec/doc note:** consistent with 04 — deliberately **not** editing CONTEXT.md: same-org is still the shipped reality and this is planning-only (avoid documenting unbuilt cross-org behaviour as fact). The cross-org clause — *"MMX emits the same single event; cross-org contract broadcast is back-office-internal, targeted via `originatingLegalEntityCode`, correlated by `(originatingLegalEntityCode, routingId)`"* — lands in the OpenSpec change proposed from this map.
