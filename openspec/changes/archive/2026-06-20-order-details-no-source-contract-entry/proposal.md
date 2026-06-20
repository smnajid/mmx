## Why

In the order creation widget the **source contract number is currently a user-entered field** in the *Order details* step: `step-order-details.component.ts` renders a free-text `sourceContractNumber` input whenever the operation is a lifecycle op (INCREASE / DECREASE / REDEMPTION). This violates the domain reality that the source contract is **never** something the user types:

- **Lifecycle on an existing contract (case 1):** the contract is already known — it is provided to the widget as the `contractNumber` input (host/playground config) and resolved via `GET /api/v1/order-creation/oncall/contract-info`. The value belongs to the chosen contract, not to a form field.
- **Fresh subscription (case 2):** there is no source contract — mmx allocates a new `generatedContractNumber` at execution time (see `execution-contract-number` spec). Nothing to type.

The current code also leaks a third, invalid path: in the **full fresh flow** the operation step shows all four OnCall operations, so a user can pick REDEMPTION with no contract behind it and is then forced to hand-type a source contract — exactly the situation that must not exist. This already contradicts the `pm-order-creation-options` note: *"portfolio-only → SUBSCRIPTION; portfolio+contract → INCREASE, DECREASE, REDEMPTION"*.

## What Changes

- **Remove** the `sourceContractNumber` form field (and its form control) from the Order details step. The step collects only `amount`, `valueDate`, and optional `minimumRate`.
- **Gate lifecycle operations behind contract context.** In the fresh flow (no `contractNumber`), the OnCall operation step SHALL offer **only SUBSCRIPTION**. INCREASE / DECREASE / REDEMPTION are reachable **only** via the contract-number shortcut.
- **`sourceContractNumber` is set exclusively by the contract shortcut** (`applyContractShortcut`) and flows read-only into the emitted payload — never collected from user input.
- **Fix downstream-clear bug:** changing the operation in contract-shortcut mode MUST preserve the resolved `sourceContractNumber` (today `clearDownstream` wipes it, like it incorrectly would for `noticePeriod` if not guarded).

This is a frontend behaviour change only. No HTTP contract, OpenAPI, or backend changes. The widget library is not yet released, so no consumer migration is required.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- **`pm-order-creation-widget`**: tighten operation gating and order-details fields so the source contract number is derived from contract context, never user-entered.

> Baseline note: the `pm-order-creation-widget` capability currently lives in the completed-but-unarchived `openspec/changes/pm-order-creation-widget/` change. These deltas assume that change's requirements as the baseline; archive it to `openspec/specs/` before/with this delivery so the delta applies cleanly.

## Impact

| Area | Notes |
|------|-------|
| `frontend/projects/order-creation-widget/.../steps/step-order-details.component.ts` | Remove `sourceContractNumber` control + template field; drop param from `setOrderDetails`. |
| `frontend/projects/order-creation-widget/.../steps/step-operation.component.ts` | Fresh flow → filter to SUBSCRIPTION only; shortcut → lifecycle ops (unchanged). |
| `frontend/projects/order-creation-widget/.../services/wizard-state.service.ts` | `setOrderDetails` signature loses `sourceContractNumber`; guard `clearDownstream` so `sourceContractNumber` is preserved in shortcut mode. |
| `frontend/projects/order-creation-widget/.../models/*` | `OrderCreationPayload.sourceContractNumber` stays (still emitted); only the input path is removed. |
| Vitest specs | `step-order-details`, `step-operation`, `wizard-state.service`, `order-creation-wizard` updated. |

## Out of Scope

- The playground's host-side `contractNumber` configuration field (host config, not order-details). Replacing it with a contract picker is covered by the separate `order-creation-live-contracts-picker` change.
- Any backend / OpenAPI changes.
