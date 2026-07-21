<!-- label: wayfinder:grilling -->
Status: proposed
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
