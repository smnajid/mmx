<!-- label: wayfinder:defect -->
Status: parked — out of scope for this map (surfaced 2026-07-20 while resolving 03)

# Unify global-account keying: local path still currency-keyed

## Defect

`GlobalAccount` and `GlobalAccountDirectory` key the global account on
`(client LegalEntity, hub LegalEntity, currency)`. This is factually wrong: an
account is **multi-currency with a single reference currency**, and one client
can hold **several** accounts at the same hub, discriminated by the originating
**client portfolioNumber** — not by currency. The true key is
`(client LegalEntity, client portfolioNumber, hub LegalEntity)`.

Corrected in the domain model at [CONTEXT.md](../../../CONTEXT.md) (**Global account**);
the code still reflects the old currency-keying. This is a spec–code parity gap
(AGENTS.md Principle VI) held open deliberately.

## Why parked (not on this map's frontier)

Fixing it changes the **local** (same-deployment) routing path — `GlobalAccount`,
`GlobalAccountDirectory`, the `global_account` schema/ID, and `RoutedOrderIntake`.
The cross-org routing map fixes remote routing only and keeps local routing
unchanged (framing given #2). So this correction sits **past this map's
destination** and does not graduate into a frontier ticket here.

## Route to fix (future, separate effort)

Likely an OpenSpec change: re-key `GlobalAccount` on client portfolioNumber, drop
currency from the key, migrate `global_account` reference data, and update
`GlobalAccountDirectory.resolve(...)` + `RoutedOrderIntake`. TDD per AGENTS.md.
