<!-- label: wayfinder:grilling -->
Status: closed (resolved 2026-07-21)
Blocked by: (none — frontier)

# Remote client reference-data & delegated grants access

## Question

Today a ClientRepresentative on a TradingClient reads its hub's reference data **in-process** — `HubScopeResolver` resolves the connected hub's `LegalEntityCode` and reads the *same database* (currencies read-only, delegated institution grants, proxy institutions "BNP via LOC", term/on-call rates read-only). Across deployments that in-process read is impossible: CGED's DB has none of LOC's reference data.

Decide how a **remote** client obtains what it needs to place a valid order:

- **Currencies / rates / grants**: replicated/cached into CGED, fetched live from LODH per request, or a hybrid? Freshness vs availability trade-off.
- **Delegated institution grant** for `(institution, CGD, currency)` and the derived proxy display name `"BNP via LOC"`: does the grant live on the hub (LODH) and project to CGED, or is it seeded into CGED?
- Where intake validation of `(institution, currency, tenor|noticePeriod)` against the client's enabled set runs when the enabled set is defined on a remote hub.
- Impact on `HubScopeResolver` (must now branch local vs remote).

(Absorbs the delegated-grant sub-question — grants are the sharpest instance of remote reference-data.)

## Resolution (2026-07-21)

**Thin client, single authority at LODH: CGED stores no hub reference data; a remote client reads everything it needs live from LODH and lets LODH's in-process check at leg-A accept be the one true validation. CGD is a genuine TradingClient of LOC — the TradingClient relationship is a hub-owned membership that may span organisations.**

Grounding facts confirmed in code during grilling:
- **`HubScopeResolver` is narrow** — only currency-settings list and global-account management resolve through it (`resolveHubLegalEntityCode`). Most order-construction reads (`OrderCreationOptionsController`, term/on-call queries, `ManagedCurrencyRepository.findByCode`) read hub-owned rows **unscoped, in-process** — safe today only because one deployment = one shared DB.
- **Delegated grants live in the *client's* DB today** (`delegated_institution_grant`, client-scoped rows keyed by hub institution) — works only because a same-deployment client shares the hub's DB. Grants are **hub-Trader-authored** (`ManageDelegatedGrantsService.listGrants` = hub trader).
- **Grant validation already runs in-process** at intake (`RoutedOrderIntake.resolveGrant` → `DelegatedGrantDirectory`).
- **Clients see per-counterparty rate values today.** `TermOrderCreationOptionsService.listCounterparties` returns `OrderCreationCounterparty` including `row.rate()`, sorted by rate desc, with a stale-rate flag. Rate exposure is real, not hypothetical.
- The `"BNP via LOC"` display is derived in-process at proxy-onboard time (`ThinProxyInstitution.deriveDisplayName`, hub display name + `connectedHubCode`).

Decisions:

1. **Authority model = thin client, single authority at LODH.** CGD holds only a **non-authoritative read-model** of hub reference data, purely to render the order form and pre-filter. The one true validation is LODH's in-process check at leg-A accept (`AcceptRoutedHubOrderUseCase`, per ticket 01). A stale CGD view is tolerable because leg A re-checks authoritatively → `Received → Rejected`. This falls straight out of the resolved transport and honours the effort's accepted eventual-consistency stance; the alternative (CGD holds an authoritative local copy) would reintroduce exactly the cross-deployment atomicity this effort set out to relax. **Delegated grants for a remote client are mastered and stored in the LODH deployment**, keyed by CGD's `LegalEntityCode` — consistent with grants being hub-Trader-authored.

2. **Read-model transport = live read-through from LODH, no replication into CGED.** Add synchronous REST query endpoint(s) on LODH (same channel/trust boundary as leg A), scoped to the authenticated originating client, called by CGD when building the order form. Load-bearing argument: because leg A (actual routing) is **synchronous** and already requires LODH to be up, caching reference data buys **zero availability** — if LODH is down CGD can't route anyway. A cache would only add staleness + a projection/reconciliation pipeline for no availability gain; live read is always as-fresh-as-authority and tightens the data-exposure surface.

3. **CGED stores zero hub reference data; the seam is remote-backed existing read ports.** No `managed_currency`, proxy `institution`, `delegated_institution_grant`, or term/on-call rows in CGED. CGED stores only genuinely client-side data (its own orders) + the connection registration. The existing read ports (`ManagedCurrencyRepository`, `ProxyInstitutionRepository`, `DelegatedGrantRepository`/`Directory`, term/on-call repos, and `HubScopeResolver`'s downstream reads) get a **remote-backed adapter** selected when the connected hub is remote (derived local-vs-remote, per framing given #2 `isLocalHub`/`isRemoteHub`). `HubScopeResolver` still just resolves `connectedHubCode`; a remote code routes the *subsequent* reads through the remote adapter. Rejected a separate bespoke "remote order-form" path because it would fork the read model into two shapes and undercut the coexistence goal.

4. **Proxy indirection collapses for remote clients.** LODH's live read returns **hub-native institution codes** (+ native display name); CGD renders `"BNP via LOC"` client-side via the existing `deriveDisplayName` logic (it knows `connectedHubCode = LOC`). The leg-A request carries the **hub-native institution code**, so LODH does **no proxy resolution** for remote orders (it already holds the native code, validates the grant `(nativeInstitution, CGD, currency, tenor)` in-process). Proxy institutions remain a **local-routing-only** concept; `"via LOC"` is a pure display concern. (Explored the keep-proxy-codes alternative: LODH could only "reverse-map" a CGD-minted alias by holding a per-client alias registry or by CGD echoing the native code in the payload — both redundant with the live read-through, which already hands CGD the native code.)

5. **Intake validation runs at LODH, in-process, at leg-A accept.** `AcceptRoutedHubOrderUseCase` validates `(institution, currency, tenor|notice)` against CGD's grant using LOC's own reference data (`DelegatedGrantDirectory` + currency policy). CGD performs no authoritative pre-validation.

6. **Rate exposure = parity, bounded by grant; final veto to ticket 07.** The remote read-through preserves the rate-visible UX (per-counterparty rate values, rate-ranked list), strictly bounded to the `(institution, currency, tenor|notice)` slice CGD is granted — the **delegated grant is itself the exposure control** (a hub that won't expose an institution's rate simply doesn't grant it). Keeps a remote client behaving identically to a local one (framing #2). Because rate curves are commercially sensitive, the final exposure-policy **veto is delegated to ticket 07** (data-exposure limits).

7. **CGD is represented at LODH as a genuine TradingClient of LOC (hub-owned, cross-org membership).** *(User-authored domain correction to the agent's "lightweight registration" recommendation.)*
   - **Organisation owns a roster of legal entities** — a legal entity's identity belongs to exactly one Organisation (CGED owns CGD; LODH owns LOC).
   - **A TradingHub owns its TradingClient list, and that list may include legal entities from other Organisations** — LOC's client list includes CGD even though CGD is a CGED legal entity.
   - So the same-org guard relaxes **symmetrically**: not only "a client may point at a foreign hub" (`connectedHubCode` on CGD, framing #3) but also "a hub's client list may contain foreign-org legal entities" (CGD in LOC's list, in LODH).
   - LODH's authority anchor is **LOC's TradingClient list**: CGD's grants (`clientLegalEntityCode = CGD`), the read-through scoping, the hub-side order's `originatingLegalEntityCode`, and the ticket-07 allow-list all key on CGD's **membership in LOC's client list** (in the LODH deployment). LODH references CGD's identity by code + org but does not own it.
   - **Connection is bidirectional, wired by the connection-registration task:** CGED holds the reciprocal `connectedHubCode = LOC` pointer; LODH holds CGD in LOC's client list. Both sides provisioned by the registration task (map fog).

**Handoff / inherited by:**
- Allow-list = *"is this `originatingLegalEntityCode` a member of LOC's TradingClient list?"*; rate-exposure policy veto → [07 — Trust & security boundary](07-trust-security-boundary.md).
- Connection-registration task must wire **both** sides (CGED `connectedHubCode=LOC` pointer + CGD in LOC's client list) and provision credentials/endpoints — sharpens the map's registration fog; still gated on 07's credential/trust decisions.
- Live read-through + LODH-in-process authority is consistent with [03 — Global-account resolution](03-global-account-ownership.md) (CGED-side resolve, authoritative check at the owning side); no conflict.

**Spec/doc note:** Planning-only — deliberately **not** editing CONTEXT.md/ADRs now for the forward-looking bits (following the ticket-04 precedent: same-org is still the shipped reality; don't document unbuilt behaviour as fact). When the OpenSpec change from this map lands it needs: (a) *TradingClient* redefined as a **hub-owned, cross-org-capable membership** (Organisation owns legal-entity identity; TradingHub owns the client list); (b) the remote read-model as **advisory-only** with LODH as sole authority; (c) delegated grants **mastered on the hub** for remote clients. Items (a) and the guard relaxation are ADR-worthy (hard to reverse, surprising, a real trade-off) — defer to the OpenSpec change.
