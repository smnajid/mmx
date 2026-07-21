<!-- label: wayfinder:grilling -->
Status: closed (resolved 2026-07-20)
Blocked by: (none — unblocked by 01, now on the frontier)

## Context from 01 (transport, resolved 2026-07-13)

Both legs are at-least-once (leg A sync REST with retries; leg B Kafka), so a dedupe key is required on both. Leg A lands at `AcceptRoutedHubOrderUseCase` — that's where "exactly-one hub-side order under retries" must be enforced. `RoutingId` now crosses an org trust boundary (minted by CGED, received by LODH), so the trust/namespacing question is live and real, not hypothetical.

# Routing correlation & idempotency across the trust boundary

## Question

`RoutingId` is generated **deterministically from the client-side order id** so a retry resolves to the same hub-side order and the hub dedupes on it — today safe because both records live in one DB the same code controls. Across a trust boundary the hub (LODH) receives an id minted by a *different organisation's* deployment.

Decide:

- Does LODH **trust** a `RoutingId` (and client order id) minted by CGED as its hub-side idempotency key, or does it mint its own and correlate back?
- How is exactly-one hub-side order guaranteed under at-least-once transport + retries (dedupe key, uniqueness constraint, where enforced)?
- Namespacing: client order ids are only unique within CGED — is `(originatingLegalEntityCode, routingId)` the real key at the hub?
- Correlation carried on both records for the reverse outcome path and for back-office `routingId` broadcast.

## Resolution (2026-07-20)

**LODH trusts the CGED-minted `RoutingId`; namespacing contains that trust; a DB constraint (not a read-check) enforces exactly-one; correlation across the boundary is `(originatingLegalEntityCode, routingId)` only.**

Two code facts reframed the ticket during grilling:
- **Hub-side dedupe is already DB-backed today — via `externalOrderReference`, not `routing_id`.** `MoneyMarketOrder.createHubSideFromRouting` sets the hub-side order's `externalOrderReference = routingId.toString()` (`MoneyMarketOrder.java:157-158`), and a global `UNIQUE` index `idx_order_ext_ref` (`V3__add_indexes.sql`) is what actually prevents a second hub-side order. The in-transaction read-check `findHubOrderByRoutingId` (`RoutedOrderIntake.java:107`) is a fast-path, not the guarantee. There is **no** unique index on `routing_id` itself.
- **`RoutingId` is already globally unique by construction.** It's `nameUUIDFromBytes("mmx-routing:" + clientOrderId)` over a `UUID.randomUUID()` client order id (`RoutingId.java:16`, `MoneyMarketOrder.java:128`). So namespacing is a **trust** decision, not a uniqueness one.

Decisions:

1. **Trust the minted id (not mint-and-correlate).** LODH accepts the CGED-minted `RoutingId` as its hub-side idempotency key. Minting a hub-local id would still require trusting *some* CGED-supplied correlation token, and would force a translation layer on every hop back (leg B, back-office broadcast) — pure cost, no trust gain. The single shared `routingId` keeps the reverse path and back-office broadcast keyless of any hub-internal id.

2. **The real hub-side key is `(originatingLegalEntityCode, routingId)`.** Purely a **trust-boundary containment** guarantee: a buggy or hostile CGED can only ever dedupe-collide *with its own* prior requests, never against PAR's, LOC's, or another remote client's records. Same trust axis as the ticket 07 allow-list (`originatingLegalEntityCode`). `originatingLegalEntityCode` is already carried on the hub-side record — no new field.

3. **Exactly-one is enforced by a DB constraint, and a violation is an idempotent success.**
   - The current read-then-write is safe only inside one DB/one transaction; across the boundary two concurrent leg-A retries can both pass the read-check and both insert. So the guarantee must be a constraint the DB enforces atomically.
   - Add a **partial unique index on `(originating_legal_entity_code, routing_id) WHERE originating_legal_entity_code IS NOT NULL`** (routed hub-side orders only; local desk orders keep `null` and are untouched).
   - `AcceptRoutedHubOrderUseCase` **catches the unique-violation and resolves to the already-persisted hub-side order, returning the same accept/`Routed` outcome CGED got the first time** — genuinely idempotent under at-least-once. The pre-read stays as a cheap fast-path; the index is authoritative.
   - Enforcement lives **at LODH**, the side that owns the hub-side record and the trust decision (consistent with ticket 01 / framing-given #4).
   - The existing `externalOrderReference = routingId` assignment stays **untouched** (harmless traceability, avoids touching the local path); the composite index — not that column — is now the authoritative idempotency guard. Any column cleanup is an implementation detail for the OpenSpec change.

4. **Cross-boundary correlation is `(originatingLegalEntityCode, routingId)` only — no internal order UUID crosses the boundary.**
   - Client-side order (CGED) already carries `routingId`, knows its own `legalEntityCode` (= `originatingLegalEntityCode`) and the hub `LegalEntityCode` via `connectedHubCode`.
   - Hub-side order (LODH) already carries `routingId` + `originatingLegalEntityCode` + `originatingExternalOrderReference`.
   - **Leg B (outcome) message** carries `(originatingLegalEntityCode, routingId)`; CGED's consumer resolves the client-side order by the same logical key the hub uses — symmetric, and each side's primary keys stay private.
   - **Back-office broadcast (06)** correlates by `routingId` today; cross-org it additionally needs `originatingLegalEntityCode` to disambiguate the target org's back office. That is a dependency 06 inherits, not finalized here.

**Handoff / inherited by:**
- Leg-B outcome consumer resolves the client-side order by `(originatingLegalEntityCode=self, routingId)` → [05 — Outcome propagation back](05-outcome-propagation-back.md).
- Back-office broadcast target keyed by `(originatingLegalEntityCode, routingId)` → [06 — Back-office event & contract broadcast](06-back-office-event-broadcast-cross-org.md).
- Allow-list on `originatingLegalEntityCode` is the same trust axis as the dedupe namespace → [07 — Trust & security boundary](07-trust-security-boundary.md).

**Spec/doc note:** CONTEXT.md's *Routing id* term (L144-145) currently frames dedupe for the same-org V1 case. When the OpenSpec change from this map lands, it needs a cross-org clause: hub-side idempotency key = `(originatingLegalEntityCode, routingId)`, trusting the client-minted id, DB-enforced. Deliberately **not** editing CONTEXT.md now — this is planning-only and same-org is still the shipped reality (avoid documenting unbuilt behavior as fact).
