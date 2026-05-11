## Why

Today execution always asks `ReferenceGenerator` for a **new** generated contract number (`ExecuteOrderService`). For **INCREASE**, **DECREASE**, and **REDEMPTION**, the business contract already exists: intake requires `sourceContractNumber`, and execution should **not** mint a new MM contract id for those operations. Only **SUBSCRIPTION** creates a new Money Market placement that needs a newly generated contract number on execute.

## What Changes

- On **receive**, when `orderOperation = SUBSCRIPTION`: **do not** persist `sourceContractNumber`; if PM sends it, **discard at reception** (`ReceiveOrderService` passes null into domain create).
- On **execute**, when `orderOperation = SUBSCRIPTION`: keep current behaviour — generate a new contract number via `ReferenceGenerator` (or equivalent) and persist it on execution details as today.
- On **execute**, when `orderOperation ∈ { INCREASE, DECREASE, REDEMPTION }`: **do not** call `ReferenceGenerator.generateContractNumber()` for this order; persist execution `generatedContractNumber` (wire/API field unchanged) **from** the existing `sourceContractNumber` supplied at intake — i.e. the executed order continues to identify the **same** underlying contract the trader was operating on.
- Domain/application rules and tests SHALL reflect this split (unit + integration as appropriate).
- Align **Speckit** artifacts under `specs/001-mm-order-processing/` and/or **`specs/002-trader-orders-views/`** where execution behaviour or field semantics are documented (no **BREAKING** HTTP shape assumed: response field `generatedContractNumber` remains populated; semantics for lifecycle ops tighten to equality with sourced contract).

## Capabilities

### New Capabilities

- **`execution-contract-number`**: Defines when MM must allocate a **new** generated contract number at execution versus reusing the **existing** contract reference from intake (`sourceContractNumber`) for lifecycle operations (increase / decrease / redemption).

### Modified Capabilities

- *(none in `openspec/specs/` today — deltas for this behaviour live only under this change until archive sync.)*

## Impact

| Area | Notes |
|------|--------|
| **`mmx-application`** | `ReceiveOrderService`: Subscription → null `sourceContractNumber` at create. `ExecuteOrderService`: branch on `OrderOperation`; only `SUBSCRIPTION` invokes `generateContractNumber()`. |
| **`mmx-domain`** | Possibly `MoneyMarketOrder.execute(...)` validation: ensure lifecycle ops have non-null source before binding `generatedContractNumber` from source (domain rule clarity). |
| **`mmx-application` tests** | `ReceiveOrderServiceTest`: subscription + PM `sourceContractNumber` → persisted null. `ExecuteOrderServiceTest`: generator called only for subscription; lifecycle paths use source contract without second generator call. |
| **Integration / e2e** | Update expectations where `generatedContractNumber` was assumed to always be freshly generated UUID-style. |
| **Contract / OpenAPI** | Update prose in `specs/002-trader-orders-views/contracts/api-v1.md` (and OpenAPI **`description`** on `generatedContractNumber` if present) only if materially misleading today — field name stays for SDD parity. |
| **`ReferenceGenerator` port** | Unchanged signature; fewer call sites for non-subscription executes. |

## Out of Scope

- Changing **Portfolio Management’s** validation rules or published contract for what they send (lifecycle still requires source on their side; MM additionally discards `sourceContractNumber` on Subscription at reception).
- Renaming REST fields (`generatedContractNumber` vs `contractNumber`).
- Persisting historical “prior contract” lineage beyond current order fields.
