<!-- label: wayfinder:grilling -->
Status: in progress (claimed 2026-07-21)
Blocked by: (none — unblocked by 04 on 2026-07-20, now on the frontier)

## Context from 01 (transport, resolved 2026-07-13)

Mechanism is settled: LODH's existing outbox publishes the outcome to a LODH-owned, org-suffixed Kafka topic; **CGED gets a new inbound Kafka consumer adapter** that applies `EXECUTED`/`CANCELLED`/`REJECTED` to the client-side order. This ticket no longer decides *the channel* — it decides the **application semantics**: eventual-consistency state model, avoiding stuck-non-terminal, how `RoutedOrderPairIntegrityException`/missing-pair translates when the hub can't see the client record, and whether client-side `EXECUTED` still emits no back-office event.

# Outcome propagation back to the remote client

## Question

Today when the hub trader executes/cancels/rejects, MMX **synchronously, same-transaction** sets the linked client-side order (ADR-0002; hardened in the outcome-propagation change). Across deployments this is impossible — LODH cannot write CGED's order table.

Decide the **reverse async path** (LOC → CGD):

- Mechanism: outbox+delivery from LODH → inbound callback/consumer on CGED that applies `EXECUTED` / `CANCELLED` / `REJECTED` to the client-side order, copying `executedRate` / `executionTime` / `dealingReference`, rendering counterparty as `"BNP via LOC"`.
- What replaces the atomic guarantee: the pair is now **eventually** consistent — define the intermediate/terminal states and how a client-side order avoids being stuck non-terminal.
- How the existing `RoutedOrderPairIntegrityException` / missing-pair semantics translate when the pair spans deployments (the hub can't see the client record to assert it exists).
- Does the client-side `EXECUTED` still emit no back-office event (the scoped exception), now that the sides are in different orgs?
