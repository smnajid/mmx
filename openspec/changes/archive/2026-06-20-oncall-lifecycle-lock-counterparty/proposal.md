## Why

OnCall lifecycle operations (INCREASE / DECREASE / REDEMPTION) act on an **existing executed Subscription contract**. That contract was opened with a specific counterparty (`institutionCode` on the original subscription). Today the widget's contract shortcut resolves only `currency` and `noticePeriod` via `contract-info`, then the counterparty step lists **every** institution with a rate for the chosen value date — so PM can pick the wrong bank. The backend accepts any active `institutionCode` at intake with no check that it matches the contract's original institution. Both gaps violate domain reality and demo seed expectations (lifecycle uses the same institution as the subscription).

## What Changes

- **Extend `contract-info`** (`GET /api/v1/order-creation/oncall/contract-info`) to return `institutionCode` and `counterparty` (display name) from the executed Subscription order, alongside existing `currency` and `noticePeriod`.
- **Widget contract shortcut** stores the resolved institution; counterparty step in shortcut mode shows **only** that institution, not the full market list.
- **Operation split (inflow vs outflow):** **INCREASE** prices new funds — the locked institution MUST have a rate for the chosen value date or the wizard blocks advance. **DECREASE** and **REDEMPTION** withdraw or close existing funds — the client MUST always be able to proceed with the locked contract counterparty even when no rate segment exists (the bank is not pricing new business).
- **Backend intake enforcement**: for OnCall lifecycle operations with `sourceContractNumber`, reject receive when `institutionCode` does not match the original executed Subscription's institution.
- **Contract-first**: update `openapi.yaml` and `api-v1.md`, regenerate server stubs, then implement.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- **`pm-order-creation-options`**: `contract-info` response includes `institutionCode` and `counterparty` from the executed Subscription.
- **`pm-order-creation-widget`**: contract shortcut locks counterparty to the contract's institution; INCREASE requires a rate; DECREASE/REDEMPTION proceed without one.
- **`order-institution-constraints`**: lifecycle intake MUST use the same `institutionCode` as the source contract's executed Subscription.

## Impact

| Area | Notes |
|------|-------|
| `specs/002-trader-orders-views/contracts/openapi.yaml` | `ContractInfoResponse` + required fields; version bump |
| `specs/002-trader-orders-views/contracts/api-v1.md` | Mirror contract-info shape |
| `backend/mmx-application` | `ContractInfoResult`, `ReceiveOrderService` lifecycle institution check |
| `backend/mmx-adapter-out-persistence` | `ExecutedSubscriptionContractInfo` + JPA mapping |
| `backend/mmx-adapter-in-rest` | Mapper for extended `ContractInfoResponse` |
| `backend/mmx-bootstrap` | Integration tests for contract-info + receive rejection |
| `frontend/projects/order-creation-widget` | Models, `applyContractShortcut`, `step-counterparty`, wizard state guards |
| Vitest / JUnit | Red-first tests per layer |

## Out of Scope

- Term lifecycle (Term contract picker is not yet available).
- Auto-skipping the counterparty step for DECREASE/REDEMPTION (step remains but shows locked counterparty without rate and always allows continue).
- Changing the counterparties list endpoint shape (filtering is client-side using contract-info data).
