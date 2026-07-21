<!-- label: wayfinder:grilling -->
Status: proposed
Blocked by: (none — unblocked by 01, now on the frontier)

# Back-office event & contract broadcast across organisations

## Question

For a routed trade MMX emits **one** `OrderExecutedV1` from the hub-side order with a routing-context block; the back office creates the LOC contract and **broadcasts to the client (PAR) back-office instance** using `routingId` (creation, reversal, replace all follow LOC→PAR). Today "client" = PAR, same Organisation as LOC.

Decide what changes when the client is **CGED**, a different organisation:

- Does the hub-side `OrderExecutedV1` + routing context stay the single source, with the broadcast target becoming **LOC → CGED** instead of LOC→PAR?
- Is the back-office broadcast an existing cross-instance channel we inherit, or does cross-*org* introduce a new trust/routing concern for the back office too?
- `routingId` correlation across two orgs' back-office instances (depends on ticket 04's correlation decision).
- Confirm this stays back-office-internal (MMX order status unaffected; no new inbound MMX callbacks for reversal/replace) as today.
