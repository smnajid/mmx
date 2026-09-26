## Context

The widget already models two entry modes in `WizardStateService`:

- **Full flow** (`FULL_FLOW_STEPS`) — fresh order, optionally with `orderType` pre-selected.
- **Contract shortcut** (`CONTRACT_SHORTCUT_STEPS`) — `applyContractShortcut(currency, noticePeriod, sourceContractNumber)` seeds `contractShortcut: true`, `orderType: 'ON_CALL'`, and `sourceContractNumber`, starting at the operation step.

`sourceContractNumber` is therefore already available from the shortcut path. The only reason the order-details field exists is the leak in the operation step that lets lifecycle ops be chosen in the full flow.

## Goals / Non-Goals

**Goals**
- Make "source contract number is never user-entered" a structural invariant of the widget.
- Keep `sourceContractNumber` in the emitted `OrderCreationPayload` (lifecycle orders still carry it).

**Non-Goals**
- Changing the intake contract (`POST /api/v1/orders`) or any backend rule.
- Building the live-contracts picker (separate change).

## Decisions

### D1: Lifecycle requires contract context

The OnCall operation step filters by mode:

- `contractShortcut === true` → `[INCREASE, DECREASE, REDEMPTION]` (today's behaviour).
- otherwise (fresh flow) → `[SUBSCRIPTION]` only.

Term is unaffected (already SUBSCRIPTION-only). This guarantees no full-flow path can reach a lifecycle operation, so no source contract ever needs hand-entry.

### D2: Order details fields are amount / valueDate / minimumRate only

Remove the `sourceContractNumber` control and template block. `setOrderDetails(amount, valueDate, minimumRate?)` no longer accepts or writes `sourceContractNumber`; the value persisted by `applyContractShortcut` remains untouched through to review/emit.

### D3: Preserve sourceContractNumber across downstream clears

`clearDownstream` currently nulls `sourceContractNumber` when `ORDER_DETAILS` is downstream. Mirror the existing `noticePeriod` guard:

```
state.sourceContractNumber = state.contractShortcut ? state.sourceContractNumber : undefined;
```

so re-selecting an operation in shortcut mode keeps the contract identity. (In fresh flow it stays undefined regardless.)

## Risks / Trade-offs

- **[Trade-off] Fresh-flow OnCall can no longer create lifecycle orders.** Intended: lifecycle without a contract is invalid by domain rules. Lifecycle is reachable only when the host supplies a contract (and, after the picker change, when the user selects one).
- **[Risk] Existing tests assume "all four operations in full flow".** The spec scenario and `step-operation.spec` must be updated in lockstep.

## Test plan

Vitest unit tests (TDD, red-first), co-located with each component/service:
- `step-operation` — fresh OnCall flow shows only SUBSCRIPTION; shortcut shows lifecycle ops.
- `step-order-details` — no source-contract field renders for any operation; form valid with amount/valueDate only.
- `wizard-state.service` — `setOrderDetails` no longer touches `sourceContractNumber`; `clearDownstream` preserves it in shortcut mode.
- `order-creation-wizard` — shortcut still emits `sourceContractNumber` in payload end-to-end.
