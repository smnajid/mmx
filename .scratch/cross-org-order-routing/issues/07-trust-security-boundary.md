<!-- label: wayfinder:grilling -->
Status: resolved
Blocked by: (none — unblocked by 01, now on the frontier)

## Context from 01 (transport, resolved 2026-07-13)

Transport is fixed, so the trust surface is now concrete:
- **Leg A (sync REST, CGD → LOC)**: authn is on the LODH inbound routing endpoint that fronts `AcceptRoutedHubOrderUseCase`. This is where the "accept only from explicitly-connected remote clients (allow-list on `originatingLegalEntityCode`)" rule lives.
- **Leg B (Kafka, LOC → CGD)**: the segregation control is the **broker ACL** — the outcome topic is LODH-owned and org-suffixed; CGED holds a **consume-only** ACL. Nothing is jointly owned. Confirm this ACL model is sufficient cross-org data segregation for the shared broker.

# Trust & security boundary between deployments

## Question

Same-org routing is in-process — no authn/authz between the two sides. Cross-org CGD → LOC crosses an **organisational trust boundary** between two independently deployed banks' MMX instances.

Decide:

- How the CGED deployment authenticates to LODH's inbound routing endpoint and vice-versa (mTLS, signed requests, service credentials — where they live).
- Authorisation: LODH must accept routed orders **only** from CGED legal entities it has explicitly connected as remote clients (allow-list keyed by `originatingLegalEntityCode`), not any caller.
- What LODH validates about an inbound routed order it did not create locally (grant authority, currency, tenor) vs what it trusts from CGED.
- Data-exposure limits: what reference data LODH is willing to expose to CGED (ties to ticket 02).

## Decision — account validation: trust, do not revalidate (bullet 3) (2026-07-26)

LODH **does not** revalidate the CGED-supplied hub-side `portfolioNumber` at leg-A accept. The `ExternalIdentityGateway` (CGED-side, per 03) is treated as the **authoritative resolver** for the cross-org path — the equivalent of the local `GlobalAccountDirectory`, trusted by the same principle that local routing trusts its own directory's resolution (no re-check after resolve). "CGED-owned" describes where the resolver lives, not whether it's authoritative.

**Consequence — failure mode shifts to booking time.** A bad account (stale/wrong CGED mapping) no longer surfaces at leg-A as a `ROUTING_FAILURE` reject. It surfaces **after** the trader executes, when the back office / Transactions 2 rejects the contract — the order is stuck `Executed` with `HandoffStatus` not advancing. Accepted (per user, 2026-07-26) as **ops fog**: a rare data-integrity incident handled by manual unwind (reverse the deal + fix the CGED mapping) via an operational runbook, not MMX architecture. The `ROUTING_FAILURE` reject origin thus fires for grant/currency/tenor errors (which LODH *does* validate — it masters the grant) but **not** for account errors.

**Architectural simplification.** LODH needs **no account-validation port for the remote path.** The local `GlobalAccountDirectory` stays a local-resolution seam; the remote path has no LODH-side account query. Hub stays thin.

**Trust model (symmetric, locked):** trust identity, intent, and authoritative resolution (account, routingId); validate only what LODH masters (grant). Currency/reference-currency compatibility at booking is out of MMX's scope (Transactions-2 / back-office rule) — logged as fog.

**Spec/doc note (CONTEXT caveat for the OpenSpec change):** CONTEXT's `Rejected` routing-failure definition lists "unresolved global account" as a routing failure. Post-this-decision, that is **CGED-side only** (edge 1 — CGED can't resolve via `ExternalIdentityGateway`); LODH trusts the account, so an invalid account surfaces at booking time (ops fog), not as a routing-time reject. The OpenSpec change needs this cross-org caveat in the `Rejected` entry.

**ADR candidate.** Surprising-without-context (a future reader will read "trusts a foreign org's account assertion" as a security hole without the "authoritative resolver" rationale) + real trade-off (leg-A reject cheap vs booking-time failure messy). Defer write per 05's precedent; folds into a trust-boundary ADR (candidate **ADR-0007**) alongside the authn-identity decision (Q2, pending).

## Decision — authn identity binding: transport-proven (bullet 1) (2026-07-26)

**Leg A:** the transport credential binds to exactly one remote legal entity; the gateway (LODH's inbound counterpart to CGED's `RemoteRoutingGateway`) maps credential → `originatingLegalEntityCode` and hands `AcceptRoutedHubOrderUseCase` a **proven** principal. The use case never trusts a payload-claimed identity. This mirrors MMX's existing identity model (`X-User-Id` and session scope are all transport-proven); introducing a payload-claimed identity for remote routing would be the sole inconsistency and a cross-client spoofing surface.

**Leg B** was closed by 01: the broker ACL *is* the authz (CGED consume-only on LODH's org-suffixed topic); Kafka client authn (SASL/SSL) is infra. No new decision.

**Credential granularity:** one credential per remote legal entity (the clean default). For V1 this is moot — CGD is the only CGED entity routing to LOC, so per-legal-entity and per-org coincide. The choice belongs to the N-client generalization (fog); if credential proliferation ever matters, per-org-with-payload-assertion is a viable relaxation (with the caveat that it re-introduces a payload-claimed identity within the org).

**The specific mechanism** (mTLS client cert, per-client signing key, OAuth2 client-credentials) is infra/platform, decided at implementation — not architecture.

## Confirmed-closed — bullets 2 & 4 (2026-07-26)

**Bullet 2 (allow-list):** already implied by 02. The allow-list *is* the **TradingClient membership** (domain reference data — "CGD is a client of LOC"), queried by `AcceptRoutedHubOrderUseCase` against the transport-proven `originatingLegalEntityCode`. With the authn decision above, the check operates on a *proven* identity, not a claimed one — so the allow-list is meaningful, not theatrical. Defense-in-depth: the gateway early-rejects unknown credentials; the use case re-checks membership as a domain rule (testable, transactional). No new architecture.

**Bullet 4 (data-exposure limits):** closed by 02 + 01. Read direction (CGED reads LODH reference data): 02 — thin client, live reads, grant is the exposure control. Leg A (CGD's order flows to LODH): not an exposure risk — LODH is entitled to see its client's order. Leg B (LODH's outcomes flow to CGED): 01 — org-suffixed topic, CGED consume-only ACL, payload is CGD's own order facts. No new exposure surface introduced by 07.

---

**Resolution complete (2026-07-26).** Two decisions (account trust, transport-proven authn) + two confirm-and-close (allow-list, data exposure). The trust model is locked: **trust identity, intent, authoritative resolution; validate only what LODH masters (grant).** Folds into ADR candidate **ADR-0007** (cross-org trust boundary). 07 was the last architecture ticket — the wayfinder's destination ("nothing about *which* architecture is left to decide") is now met.
