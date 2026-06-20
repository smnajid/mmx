## Context

Contract shortcut flow today:

```
contractNumber → GET contract-info → { currency, noticePeriod }
  → OPERATION → VALUE_DATE → COUNTERPARTY (all market rates) → …
```

The executed Subscription order in mmx already stores `institutionCode` and derived `counterparty` on `OrderEntity`, but `findExecutedSubscriptionByContractNumber` maps only currency and notice period into `ExecutedSubscriptionContractInfo`. `ReceiveOrderService` validates institution is active but never compares it to the source contract.

## Goals / Non-Goals

**Goals:**
- PM cannot choose a different counterparty when operating on an existing OnCall contract (UI).
- Server rejects lifecycle intake when `institutionCode` ≠ subscription institution (defence in depth).
- Contract-info is the single source for locked institution in the widget shortcut.
- **DECREASE** and **REDEMPTION** are never blocked by a missing rate — outflows do not price new funds.
- **INCREASE** requires a rate for the locked institution (inflow / new money).

**Non-Goals:**
- Term lifecycle counterparty lock (no Term contract shortcut yet).
- Skipping the counterparty step UI entirely for outflows (show locked counterparty; rate optional).
- New counterparties query parameter or server-side filter on the list endpoint.

## Decisions

### D1: Extend contract-info (not a new endpoint)

Add `institutionCode` and `counterparty` to `ContractInfoResponse` in OpenAPI. Lookup path unchanged (`findExecutedSubscriptionByContractNumber` on executed OnCall SUBSCRIPTION).

**Rationale:** Widget already calls contract-info at shortcut init; one round-trip carries everything needed. Alternatives (filter param on counterparties, separate institution lookup) add API surface without benefit.

### D2: Widget filters client-side; operation split for rate requirement

`applyContractShortcut` stores `contractInstitutionCode` and `contractCounterparty` from contract-info.

| Operation | Rate needed? | Shortcut counterparty step behaviour |
|-----------|--------------|--------------------------------------|
| INCREASE | Yes (new funds) | Call counterparties API; filter to locked `institutionCode`. Block advance if no rate row. |
| DECREASE | No (withdrawal) | Show locked counterparty from contract-info. If API has a rate, display it (informational). If not, show "no rate" and **still allow Continue**. |
| REDEMPTION | No (full exit) | Same as DECREASE. |

`setCounterparty` for outflows without a rate row: persist `institutionCode` and `counterparty` from contract-info; leave `counterpartyRate` / `counterpartyRateDate` unset.

**Rationale:** Rate segments price new on-call business. Decrease/redemption are administrative outflows on an existing contract — the institution does not need to publish a rate to accept a withdrawal. INCREASE is the only lifecycle op that adds funds.

**Alternatives considered:**
- Skip counterparty step for outflows — rejected; explicit confirmation of locked bank is clearer.
- Backend counterparties filter by operation — unnecessary; widget logic suffices.

### D3: Preserve locked institution in wizard state

`clearDownstream` mirrors the `noticePeriod` / `sourceContractNumber` guards: when `contractShortcut` is true, changing operation or value date clears counterparty selection but **not** `contractInstitutionCode` / `contractCounterparty`.

### D4: Backend validation in ReceiveOrderService via repository lookup

For `orderOperation` ∈ {INCREASE, DECREASE, REDEMPTION} with non-null `sourceContractNumber`, load executed Subscription by contract number and compare `command.institutionCode()` to subscription `institutionCode`. Reject with `InvalidOrderException` on mismatch.

**Rationale:** Keeps rule at application layer using existing port; no new domain policy unless tests justify extraction. Validation runs after institution active check, before order creation.

**Alternatives considered:**
- Domain policy class — defer unless reuse emerges (UpdateOrderService may need same rule later).
- DB constraint — not applicable; cross-order rule.

### D5: Contract-info returns counterparty display name

Include `counterparty` (string, institution `displayName` at execution time stored on order) so the widget can show the locked name before rate load completes.

## Risks / Trade-offs

- **[Risk] Subscription missing institutionCode on legacy rows** → Executed subscriptions always have institution from intake; integration test with seeded data. Contract-info 500/validation if null (data integrity issue).
- **[Risk] Rate unavailable for INCREASE on chosen value date** → Widget blocks advance with clear message; PM must change value date or wait for rate segment. DECREASE/REDEMPTION unaffected.
- **[Trade-off] Client filter only on UI** → Backend enforcement (D4) closes the bypass for direct API callers.

## Migration Plan

Additive OpenAPI fields on `ContractInfoResponse` — existing consumers ignoring unknown fields continue to work; widget and playground update in same delivery. No Flyway migration.

## Open Questions

(none)
