## Why

Portfolio Management currently submits orders to mmx via `POST /api/v1/orders` without guidance on which counterparties are available or what rates they offer. The PM operator manually determines counterparty and rate information outside the system, leading to uninformed order placement. mmx already holds the reference data needed to guide order creation — managed currencies, institution catalog, term rates, and on-call rate segments — but does not expose it for this purpose.

A new **Order Creation Options API** lets the Portfolio Management application build a step-by-step wizard that progressively narrows available choices (currency → operation → tenor/notice period → counterparty) based on live reference data, with counterparty selection moving from trader execution to PM intake.

## What Changes

- **New read-only REST endpoints** (`/api/v1/order-creation/term/*` and `/api/v1/order-creation/oncall/*`) that expose available currencies, operations (with minimum amounts), tenors/notice periods, and counterparties with their rates — filtered at each step so only viable combinations are shown.
- **New contract-info endpoint** (`/api/v1/order-creation/oncall/contract-info`) that resolves a contract number to its currency and notice period by looking up the original executed Subscription order, enabling the wizard for OnCall lifecycle operations (Increase/Decrease/Redemption).
- **Counterparty set at intake** — `ReceiveOrderRequest` gains a required `institutionCode` field; the system derives `counterparty` (display name) at order reception. **BREAKING**: `desiredCounterpartyComment` is removed from intake.
- **Execution simplified** — `ExecuteOrderRequest` no longer accepts `institutionCode`; the counterparty is locked from intake. Trader provides only `executedRate`. If the trader cannot deal with the PM-chosen counterparty, they must reject the order. **BREAKING**: `institutionCode` removed from execute request.
- **Rate fallback with transparency** — counterparty endpoints return the latest available rate per institution (not restricted to today's upload). Each row includes `rateDate` and an `indicative` flag when the rate is stale (not from today's trading date). Counterparties sorted by best rate first.

## Capabilities

### New Capabilities

- `pm-order-creation-options`: Read-only API exposing available order creation options (currencies, operations, tenors/notice periods, counterparties with rates) for the PM wizard, with cascading availability filters based on reference data (managed currencies, term rates, on-call rate segments, active institutions). Includes contract-info lookup for OnCall lifecycle operations.

### Modified Capabilities

- `order-institution-constraints`: Institution is now set at **intake** (not execution). Execute no longer accepts `institutionCode` — the counterparty is locked from PM's choice at order creation. Trader must reject if they cannot deal with the chosen counterparty.

## Impact

- **Backend — `mmx-application`**: new use cases / query services for each options endpoint; new `port/out` queries on `TermRateRepository`, `OnCallRateRepository`, `InstitutionRepository`, `ManagedCurrencyRepository`, and `OrderRepository` (contract-info lookup).
- **Backend — `mmx-adapter-in-rest`**: new `OrderCreationOptionsController` implementing generated API interfaces from the new OpenAPI paths. No business rules in the controller.
- **Backend — `mmx-adapter-out-persistence`**: new repository queries (latest term rate per institution, open on-call segments by currency/notice period, executed subscription by contract number).
- **OpenAPI contract**: new paths under `/api/v1/order-creation/`, plus breaking changes to `ReceiveOrderRequest` (+`institutionCode`, -`desiredCounterpartyComment`) and `ExecuteOrderRequest` (-`institutionCode`). Contract-first: OpenAPI and `api-v1.md` updated before implementation.
- **Existing intake flow**: `ReceiveOrderService` must resolve `institutionCode` → `Institution.displayName` and set `counterparty` on the order at reception. Validation: institution must be active.
- **Existing execution flow**: `ExecuteOrderService` no longer reads `institutionCode` from the request — uses the value already on the order. `OrderAgainstInstitutionPolicy` enforcement moves to intake.
- **TDD**: all new behaviour and breaking changes delivered with failing tests first (JUnit 5).
- **No frontend changes** in mmx — the wizard UI lives in the Portfolio Management application.
- **No persistence schema changes** — all data already exists in current tables; `institutionCode` column already on `money_market_order`.
