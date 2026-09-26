# A TradingClient owns its onboarded institutions and counterparty accounts

**Status:** accepted — partially supersedes ADR 0007 ("Replicate reference data across deployments — rejected") for institutions only

A TradingClient previously held no institutions of its own. A same-Organisation client had thin proxy rows, and a cross-Organisation client read the hub's institutions live over REST (ADR 0007). We now require each LegalEntity to hold its own **counterparty accounts** per institution (BNP's accounts at PAR differ from BNP's accounts at LOC), to export them to its own back-office instance, and to let the ClientRepresentative decide which **granted institutions** to onboard. The hub must never hold the client's accounts, so the client needs an institution record it owns. We decided that **institution onboarding** creates an **onboarded institution** in the client's own deployment. That record is owned by the client LegalEntity and permanently linked to the hub institution it was granted from. **Grants, rates, and managed currencies stay hub-owned and are still read live** from the hub.

## Considered options

- **Keep live reads and store accounts in a client-side side table keyed by hub institution code**: rejected. The client would still need onboarding and offboarding state and an export trigger per institution, which amounts to an institution record in all but name.
- **Store the client's accounts at the hub**: rejected. The accounts belong to the client LegalEntity's books and to its own back-office instance. Holding them at the hub would put one Organisation's reference data in another Organisation's deployment (ADR 0001).
- **Client-owned onboarded institution linked to the hub institution (chosen).** It is not a replica: it holds only client-owned facts (onboarding state, counterparty accounts, derived display name) plus the link. Hub-owned facts (grants, active flag, rates) are never copied, so ADR 0007's consistency concern does not apply.

## Consequences

- Routing still maps the onboarded institution to the hub-native institution code on Leg A. The client also sends its **counterparty account snapshot**, which the hub stores read-only and returns in `OrderExecutedV1`'s routing context, so the client back office books against the client's account.
- If the hub revokes every grant for an onboarded institution, the onboarded institution is not removed. It becomes **closed to new business**, and Decrease/Redemption against existing contracts remain allowed.
- Same-Organisation clients migrate from thin proxies to onboarded institutions, so both deployment shapes share one model. Each existing proxy is converted in place, with empty counterparty accounts; nothing is dropped. The system is still pre-production, so test and seed scenarios are updated to carry counterparty accounts rather than preserving legacy proxy data.
