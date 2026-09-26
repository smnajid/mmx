## Context

mmx persists every order in one `MoneyMarketOrder` table. A contract is materialised when a Subscription is **executed** (`generatedContractNumber` set; see `JpaOrderRepository.findExecutedSubscriptionByContractNumber`, which already matches OnCall executed Subscriptions). Lifecycle orders carry `sourceContractNumber` equal to the contract they act on (see `execution-contract-number`). So both the set of contracts and the actions against them are derivable from the order table.

The order-creation API is **contract-first** (`OrderCreationOptionsController implements OrderCreationApi`, generated from `specs/002-trader-orders-views/contracts/openapi.yaml`). New endpoints are added to the OpenAPI first, regenerated, then implemented.

## Goals / Non-Goals

**Goals**
- List live contracts per portfolio so a host can offer a contract picker that feeds the widget shortcut.
- Define liveness precisely and testably for OnCall (no redemption) and Term (end date in future).

**Non-Goals**
- Term lifecycle operations; running contract balances; a widget-internal picker.

## Decisions

### D1: One endpoint, orderType-filtered

`GET /api/v1/order-creation/contracts?portfolioNumber={pf}&orderType={TERM|ON_CALL}`.

`orderType` is required (the picker is opened from a Term or OnCall context). Returns `LiveContractsResponse { contracts: LiveContract[] }` where `LiveContract` carries enough to drive the shortcut and render a useful row:

```
LiveContract:
  contractNumber: string        # generatedContractNumber of the executed subscription
  orderType: TERM | ON_CALL
  currency: string
  noticePeriod?: 24H | 48H      # OnCall only
  tenor?: 1W|2W|1M|3M|6M|1Y     # Term only
  valueDate: string (date)      # subscription value date
  endDate?: string (date)       # Term only — valueDate + tenor
  originalAmount: number        # subscription amount (reference, not a running balance)
```

No `X-Trader-Id` required (consistent with the rest of `/api/v1/order-creation/`, consumed by PM).

### D2: Liveness rules (domain)

Base set = executed Subscription orders for the portfolio and orderType.

- **OnCall live** ⇔ no order exists with `sourceContractNumber == contract.contractNumber` AND `orderOperation == REDEMPTION` AND status ≠ CANCELLED (i.e. received or executed redemptions both disqualify). Prevents offering a contract with a redemption already in flight.
- **Term live** ⇔ `endDate > today`, where `endDate = subscription.valueDate + tenorDuration(tenor)`. Tenor→duration mapping lives in `mmx-domain` (1W/2W/1M/3M/6M/1Y).

These predicates belong in `mmx-domain` (pure) and are orchestrated by the application service; the repository supplies the candidate subscriptions and the redemption-existence signal.

### D3: Repository query placement

Extend `OrderRepository` (`port/out`) with a query returning executed Subscriptions for a portfolio + orderType, plus a way to know which contract numbers have a non-cancelled redemption. Two shapes are acceptable:

- a single projection that the persistence adapter assembles (preferred — one round trip via a join/derived query in `SpringDataOrderRepository`), or
- two queries (candidates + redeemed contract numbers) combined in the service.

Decide during implementation; either keeps SQL in `mmx-adapter-out-persistence` and rules in domain/application.

### D4: Host-side picker

The playground (dev stand-in for the PM host) gets a picker: pick orderType → call `listLiveContracts(portfolioNumber, orderType)` → show contracts → on select, set the widget's `contractNumber` input (OnCall only for now). Term entries may be listed but are non-actionable until Term lifecycle exists. The widget itself is unchanged except for an optional `WizardApiService.listLiveContracts` helper the host can reuse.

## Risks / Trade-offs

- **[Risk] "redemption exists" semantics.** We disqualify on any non-cancelled redemption (received or executed). If product later wants "executed only", it's a one-line predicate change with a new scenario.
- **[Trade-off] Term listed but not actionable.** Slightly surprising UX, but it is deliberate groundwork; the picker should visually indicate Term selection is not yet available for lifecycle.
- **[Risk] Performance.** Scoped by portfolio; order volumes per portfolio are small. Index on `(portfolioNumber, orderType, status, orderOperation)` and `sourceContractNumber` if needed.

## Test plan

- **domain** — liveness rules: OnCall with/without redemption; Term end-date before/after today; tenor→duration mapping.
- **application** — `ListLiveContractsService`: filters to executed Subscriptions for the portfolio/type and applies liveness; excludes redeemed OnCall and matured Term.
- **adapter-out-persistence** — query test: executed subscriptions returned; redemption (received and executed) disqualifies; cancelled redemption does not.
- **adapter-in-rest** — controller test against generated API: 200 with `LiveContractsResponse`; required params validated.
- **contract** — OpenAPI/`api-v1.md` describe the tenth operation and schemas.
- **frontend** — playground picker spec (Vitest): lists contracts, selecting one mounts the widget with `contractNumber` set.
