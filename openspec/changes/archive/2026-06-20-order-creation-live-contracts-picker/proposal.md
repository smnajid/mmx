## Why

To run a lifecycle operation (Increase / Decrease / Redemption) the widget needs a `contractNumber`. Today the host must already know it — the playground exposes a free-text "Contract number" field and the PM app would have to source it elsewhere. There is no way for mmx to tell a user **which contracts are still live** and therefore eligible for a lifecycle action.

This change adds an mmx endpoint that lists **live contracts** for a portfolio so the host/playground can present a picker. Selecting a contract feeds its number into the widget's existing contract-number shortcut. It complements `order-details-no-source-contract-entry`: that change guarantees the source contract is never typed; this change gives the user a first-class way to **choose** the contract instead.

**Liveness** (a contract is still operable):
- **OnCall** contract → live while **no redemption order exists** against it (received or executed). Increases/decreases do not end a contract; a redemption does. Counting redemptions in any non-cancelled state prevents offering a contract that already has a redemption in flight.
- **Term** contract → live while its **end date is in the future** (contract `valueDate` + tenor).

Contracts come into being when a Subscription order is **executed** (its `generatedContractNumber` is allocated), so "live contracts" are executed Subscriptions, scoped to the requested portfolio, minus those that are no longer live by the rules above.

## What Changes

- **New contract-first endpoint** `GET /api/v1/order-creation/contracts?portfolioNumber={pf}&orderType={TERM|ON_CALL}` returning live contracts for the portfolio. Defined in canonical `specs/002-trader-orders-views/contracts/openapi.yaml` (tag `OrderCreation`) + `api-v1.md` mirror, then codegen + controller. This makes the order-creation surface **ten** GET operations (was nine).
- **Domain liveness rule** for OnCall (no redemption) and Term (end date in the future), expressed in `mmx-domain` / `mmx-application` and backed by an `OrderRepository` query.
- **Host/playground picker:** the dev playground replaces its free-text contract field with a contract picker that calls the new endpoint and passes the chosen contract number into the widget's `contractNumber` input.
- **Term groundwork only:** the endpoint lists live Term contracts, but **Term lifecycle operations are out of scope**. The widget's contract shortcut remains OnCall-only; Term entries are listed for the planned future Term-lifecycle (rollover/renewal) work and are not yet actionable in the widget.

## Capabilities

### New Capabilities

(none — extends existing capabilities)

### Modified Capabilities

- **`pm-order-creation-options`**: add the live-contracts listing endpoint and its liveness rules; update the contract-first endpoint count (nine → ten).
- **`pm-order-creation-widget`**: the dev playground offers a live-contract picker that drives the `contractNumber` shortcut.

> Baseline note: `pm-order-creation-widget` currently lives in the unarchived `openspec/changes/pm-order-creation-widget/` change; archive it to `openspec/specs/` before/with this delivery so the playground delta applies cleanly.

## Impact

| Area | Notes |
|------|-------|
| `specs/002-trader-orders-views/contracts/openapi.yaml` + `api-v1.md` | New `GET /api/v1/order-creation/contracts` operation + `LiveContract` / `LiveContractsResponse` schemas. |
| `mmx-adapter-in-rest` | Regenerate `OrderCreationApi`; implement new method in `OrderCreationOptionsController`; mapper additions. |
| `mmx-application` | New `ListLiveContractsUseCase` (`port/in`) + service; result type under `application/ordercreation`. |
| `mmx-application` `port/out` | Extend `OrderRepository`: query executed Subscriptions by portfolio + orderType and determine liveness (redemption existence for OnCall; end date for Term). |
| `mmx-domain` | Liveness policy/helpers (Term end-date from tenor; OnCall "has redemption"). No framework deps. |
| `mmx-adapter-out-persistence` | `JpaOrderRepository` + `SpringDataOrderRepository` query methods (e.g. find executed subscriptions by portfolio/type; detect redemption by `sourceContractNumber`). |
| `frontend/src/app/features/widget-playground/` | Contract picker calling the new endpoint; replaces free-text contract input. |
| `frontend/projects/order-creation-widget/.../services/wizard-api.service.ts` (+ models) | Optional `listLiveContracts(...)` client used by the playground/host. |

## Out of Scope

- **Term lifecycle operations** (rollover/renewal) and wiring Term contract selection into a widget shortcut — future change. Term contracts are listed only.
- Building a contract picker inside the widget itself (kept host-side, consistent with the widget's "host provides `contractNumber`" boundary).
- Contract balance/position maths (net amount after increases/decreases). The listing returns identity + reference data, not running balances.
