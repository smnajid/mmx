<!-- label: wayfinder:grilling -->
Status: closed (resolved 2026-07-20)
Blocked by: (none — frontier)

# Global-account resolution ownership across deployments

## Question

`GlobalAccountDirectory` resolves the **global account** for `(client LegalEntity, hub LegalEntity, currency)` at routing time — today an in-process read of MMX-managed reference data, used to set the hub-side order's `portfolioNumber`. For CGD → LOC the two legal entities live in different deployments.

Decide:

- **Which side owns the `(CGD, LOC, ccy)` global-account record** — CGED, LODH, or both (mirrored)? The hub-side order is created in LODH, so LODH must ultimately know the account; but routing failure on unresolved account must reject the *client-side* order in CGED.
- Is resolution done **before** the outbound routing request (in CGED) or **on receipt** at LODH? This interacts with remote `Routed` timing (client stays `Received` until hub accepts).
- Whether `GlobalAccountDirectory` gains a remote-backed implementation or the account travels *in* the routing request payload.

## Resolution (2026-07-20)

**Resolution owner = CGED, via an external account-management system ("External Identity").** Grilling surfaced that account resolution is **not** an in-process MMX read at all. CGED integrates with an external **External Identity** system that maps `(client LegalEntity, client portfolioNumber, target hub LegalEntity) → hub-side portfolioNumber`. This reverses the initial "LODH owns it" instinct: the mapping is authored/held on the **client (CGED) side** and LODH never resolves it.

Answering the three sub-questions:

- **Which side owns `(CGD, LOC)` account resolution — CGED.** Not LODH, not mirrored. External Identity lives on the CGED side; CGED resolves the LOC account before routing. LODH receives the already-resolved `portfolioNumber` and never queries the directory for a remote order.
- **Resolved before the outbound request, in CGED** (not on receipt at LODH). CGED resolves via External Identity, then sends. An **unresolved account rejects the client-side order in CGED directly** (no round-trip needed for this failure edge) — client-side `Rejected` with a routing reason, honouring the meaning of `Received → Rejected`. (Contrast: an account LODH rejects *after* receipt is a different edge, handed to 07/08.)
- **Account travels IN the routing request payload** (leg A, ticket 01). No remote-backed `GlobalAccountDirectory`. The seam is a **new CGED outbound port `ExternalIdentityGateway`** (adapter over the external system) — deliberately *not* a `GlobalAccountDirectory` implementation, because it has a different key (client portfolioNumber, not currency), a different location (CGED-side, pre-send), and a different trust profile.

**Domain-model correction (load-bearing, general — not remote-only).** The global account is keyed by **`(client LegalEntity, client portfolioNumber, hub LegalEntity)`**, and accounts are **multi-currency with one reference currency** — **currency is not part of the key** (it comes from the order at booking). The existing `GlobalAccount` / `GlobalAccountDirectory` currency-keying is a known V1 simplification. Corrected in [CONTEXT.md](../../../CONTEXT.md) (**Global account**); the code drift is tracked as a defect ([09](09-unify-global-account-keying.md)) and is **out of scope** for this map (fixing it changes local routing — framing given #2).

**Coexistence (path i, chosen).** Remote routing resolves via `ExternalIdentityGateway` at CGED; local (same-deployment) routing keeps its existing in-process `GlobalAccountDirectory` unchanged. The two paths are polymorphic in resolution — consistent with framing given #2 (local routing untouched).

**Handed off:**
- *Does LODH validate/trust the CGED-supplied hub-side `portfolioNumber`* (account exists at LOC, belongs to CGD) before booking, or accept it as-authored? → extends **07 — Trust & security boundary** (and relates to 04's "CGED authors values in LODH's namespace" theme). Not resolved here.
- *Unresolved-account-at-LODH as a reject edge* (vs the CGED-side unresolved reject settled here) → enumerated in **08 — Consistency & failure model**.
