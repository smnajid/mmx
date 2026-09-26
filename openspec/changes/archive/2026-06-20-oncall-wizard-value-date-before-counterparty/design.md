## Context

**Current state**

- Full-flow step order: Order type → Currency → Operation → Tenor/notice → **Counterparty** → **Order details** → Review.
- OnCall counterparties API requires `valueDate`; `StepCounterpartyComponent` shows an inline date picker when `valueDate` is missing.
- `StepOrderDetailsComponent` always collects `valueDate` (Term and OnCall).
- `WizardStateService.setCounterparty()` clears downstream ORDER_DETAILS fields including `valueDate`, so a date set on the counterparty step is wiped when the user selects an institution.
- Spec (`pm-order-creation-widget`) allows prompting for valueDate on the counterparty step if not already provided.

**Constraints**

- Frontend-only; no REST contract changes.
- Reuse existing settlement validation (`minSettlementDate`, `isOnOrAfterMinSettlementDate`).
- TDD: Vitest specs in `order-creation-widget` library.
- Term flow must remain unchanged in UX (value date still on order details).

## Goals / Non-Goals

**Goals:**

- OnCall users set **value date once**, before counterparty selection, so displayed rates match the chosen settlement date.
- Dedicated wizard step with clear progress indicator entry ("Value date").
- Counterparty step loads rates immediately using stored `valueDate` (no inline date picker).
- OnCall order-details step collects amount (+ optional minimumRate) only; `valueDate` already in state from the new step.
- Changing notice period or currency upstream clears value date and downstream counterparty (existing `clearDownstream` pattern).

**Non-Goals:**

- Backend or order-creation API changes.
- Term order step reordering.
- Merging value date into the notice-period step UI (separate step keeps progress indicator honest).
- Playground-specific code (inherits widget behaviour).

## Decisions

### D1: New `WizardStepId.VALUE_DATE` visible only for OnCall

Insert `VALUE_DATE` after `TENOR_OR_NOTICE_PERIOD` and before `COUNTERPARTY` in the canonical step list. `computeVisibleSteps()` filters it out when `orderType === 'TERM'` or when not yet known.

**Rationale:** Rates depend on value date; step must precede counterparty. Dedicated step avoids cramming date into notice-period UI.

**Alternative:** Inline on notice-period step — rejected; mixes two concerns and hides progress.

### D2: New `StepValueDateComponent`

Reactive form with single `valueDate` field, same validation as order details (`>= today + 2 calendar days`). On submit calls `wizardState.setValueDate(value)` and `completeAndAdvance()`.

Default display: `minSettlementDate()` pre-filled in the input.

**Rationale:** Mirrors `StepOrderDetailsComponent` date validation; one place for OnCall settlement date rules.

### D3: Counterparty step — remove inline value date UI

`StepCounterpartyComponent` always calls `listOnCallCounterparties(currency, noticePeriod, state.valueDate!)` when `orderType === ON_CALL`. If `valueDate` is missing (should not happen when step order is enforced), show a blocking message directing the user back.

Remove `valueDateInput` signal, `onValueDateChange`, and `setValueDate` from counterparty step.

### D4: Order details — conditional fields by order type

| Order type | Fields on order details step |
|------------|------------------------------|
| TERM | amount, valueDate, minimumRate |
| ON_CALL | amount, minimumRate (valueDate read-only summary line optional) |

`setOrderDetails(amount, valueDate?, minimumRate?)` for OnCall: pass through existing `state.valueDate` when completing the step (signature may keep `valueDate` param optional when OnCall).

`isStepComplete(ORDER_DETAILS)`: OnCall requires `amount` only; Term requires `amount` and `valueDate`.

### D5: `clearDownstream` adjustments

When clearing from `TENOR_OR_NOTICE_PERIOD` or earlier: clear `valueDate` if `VALUE_DATE` is downstream (OnCall path).

When clearing from `COUNTERPARTY`: do **not** clear `valueDate` (it is upstream now).

When clearing from `VALUE_DATE`: clear counterparty selections and order details fields as today.

### D6: Contract shortcut flow

`CONTRACT_SHORTCUT_STEPS` becomes: OPERATION → VALUE_DATE → COUNTERPARTY → ORDER_DETAILS → REVIEW.

After operation selection, user picks value date, then counterparties with rates for that date.

### D7: Step labels and progress

`WIZARD_STEP_LABELS[VALUE_DATE] = 'Value date'`.

Progress indicator uses `visibleSteps().length` — OnCall full flow shows 8 steps, Term stays 7.

## Risks / Trade-offs

- **[Risk] Users change value date after seeing rates** → Mitigated: back navigation to VALUE_DATE step clears counterparty via `clearDownstream`; must re-select counterparty.
- **[Risk] Test churn across wizard specs** → Mitigated: update `wizard-state.service.spec`, new `step-value-date.component.spec`, adjust counterparty/order-details/order-creation-wizard specs in one delivery.
- **[Trade-off] Extra step for OnCall** → Acceptable; matches domain (date drives rates) and removes duplicate entry.

## Migration Plan

Frontend library + playground deploy together. No data migration. Existing in-flight wizard sessions (if any) reset on reload.

## Open Questions

(none — ready to implement)
