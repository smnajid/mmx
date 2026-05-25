## Context

`OrderAgainstCurrencyPolicy` already validates **Term** orders against `enabledTenors` and **OnCall** orders against `enabledNoticePeriods`. `ManagedCurrency` and the settings UI still require both sets to be non-empty—a stricter invariant than intake needs.

## Goals / Non-Goals

**Goals:**

- Term-only: non-empty tenors, empty notices, `active: true` ⇒ Term intake OK, OnCall rejected.
- OnCall-only: symmetric.
- Prevent “dead” catalog rows: both sets empty rejected on onboard/update.
- Explicit PATCH `[]` clears a workspace.

**Non-Goals:**

- New DB columns or `termEnabled` flags.
- Changing `active` / disable / enable flows.

## Decisions

### 1. Empty set = workspace off (Option A)

**Choice:** Allow zero tenors or zero notices; require `!tenors.isEmpty() || !notices.isEmpty()` on save.

**Rationale:** Matches existing policy branches; smallest change.

### 2. PATCH empty array clears workspace

**Choice:** `null`/absent field ⇒ unchanged; `[]` ⇒ set to empty `EnumSet`.

**Rationale:** Traders must be able to clear all notices in one save without sending every notice code as false.

### 3. UI guard mirrors domain

Block toggling off the last tenor **only when** no notices remain enabled (and symmetrically for notices).

### 4. Global `active` unchanged

**Deactivate** still blocks all intake. Partial workspace control is via enabled sets, not `active`.

## Risks / Trade-offs

| Risk | Mitigation |
|------|------------|
| Client sends `[]` unintentionally | Document PATCH semantics in `api-v1.md` |
| List shows empty notices as `—` | Already in rules summary; optional later: “OnCall: off” label |

## Migration Plan

Deploy application only. Existing rows with both workspaces enabled unchanged. Traders may PATCH to Term-only as needed.
