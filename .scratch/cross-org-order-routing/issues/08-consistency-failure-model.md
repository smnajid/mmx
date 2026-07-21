<!-- label: wayfinder:grilling -->
Status: proposed
Blocked by: 05-outcome-propagation-back

# Consistency & failure model for remote routing

## Question

With atomicity gone across the boundary, define the **end-to-end consistency contract** for a remote routed pair so the design is implementable and operable:

- The full client-side state machine for remote routing: `Received` → (hub accepts) `Routed` → terminal — plus how each failure edge is handled (transport failure before hub accept, hub reject, hub-accept-but-outcome-lost, unresolved global account).
- Compensation/timeout: if the outbound routing request never confirms, how does the client-side order avoid being stuck in `Received` forever — ret/backoff, then `Rejected` with a routing reason?
- How a remote failure becomes client-side `REJECTED` given the reject may originate at either side.
- Whether any transient/in-flight state is needed (deferred at charting — decide here) and the reconciliation story for a half-terminal pair (feeds the ops fog in the map).
