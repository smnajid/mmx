## Why

OnCall counterparty rates from `GET /api/v1/order-creation/oncall/counterparties` depend on **value date** (along with currency and notice period). The PM wizard currently asks for value date on the **counterparty** step and again on **order details**, and selecting a counterparty wipes the first entry via `clearDownstream` — forcing traders to pick the same date twice. Value date must be collected **before** counterparty selection so rates reflect the intended settlement date.

## What Changes

- **New OnCall-only wizard step** (`VALUE_DATE`) inserted after notice period and **before** counterparty in full flow and contract-shortcut flow.
- **Remove** the inline value-date picker from `StepCounterpartyComponent`; counterparties load using `valueDate` already in wizard state.
- **Order details step (OnCall)**: collect **amount** and optional **minimumRate** only — value date is no longer re-entered here (show read-only summary or omit field; user can navigate back to change value date).
- **Order details step (Term)**: unchanged — still collects amount, valueDate, minimumRate.
- **Wizard state / navigation**: `visibleSteps` includes `VALUE_DATE` only when `orderType === ON_CALL`; `clearDownstream` preserves value date when invalidating counterparty from upstream notice/currency changes.
- **Spec alignment**: update `pm-order-creation-widget` step numbering and counterparty/order-details requirements.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- **`pm-order-creation-widget`**: add OnCall value-date step before counterparty; remove counterparty-step value-date prompt; narrow order-details fields for OnCall.

## Impact

| Area | Notes |
|------|-------|
| `frontend/projects/order-creation-widget/` | `WizardStepId`, `WizardStateService`, new `StepValueDateComponent`, `wizard-shell`, step labels, counterparty + order-details steps |
| `frontend/src/app/features/widget-playground/` | No API changes; playground inherits widget behaviour |
| `openspec/specs/pm-order-creation-widget/spec.md` | Delta merged on archive |
| Backend / OpenAPI | **No changes** |

## Out of Scope

- Changing oncall counterparties API shape or rate logic.
- Term order wizard step order (value date remains on order details).
- Trader execute-form value date behaviour (separate change).
