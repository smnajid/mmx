# Data Model: Money Market Order Processing

## Aggregate Root

### MoneyMarketOrder

The single aggregate root of the Order Processing bounded context. Encapsulates the complete order lifecycle from reception through execution.

**Invariants enforced in the aggregate:**
- OrderOperation MUST be valid for the given OrderType
- Subscription orders MUST have all required fields populated (no sourceContractNumber)
- Increase/Decrease/Redemption orders MUST have sourceContractNumber
- Term orders MUST have a Tenor and MUST NOT have a NoticePeriod
- OnCall orders MUST have a NoticePeriod and MUST NOT have a Tenor
- ValueDate MUST be at least 2 calendar days in the future (validated at creation time)
- Amount MUST be greater than zero
- MinimumRate MUST be greater than or equal to zero
- Status transitions MUST follow the allowed state machine
- Only the assigned Trader may update, execute, or unassign the order

#### Fields

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `id` | `UUID` | No | Internal unique identifier, generated at creation |
| `externalOrderReference` | `ExternalOrderReference` | No | Business key from Portfolio Management system; idempotency key |
| `orderType` | `OrderType` | No | TERM or ON_CALL |
| `orderOperation` | `OrderOperation` | No | SUBSCRIPTION, INCREASE, DECREASE, or REDEMPTION |
| `portfolioNumber` | `PortfolioNumber` | No | Identifier of the source portfolio |
| `currency` | `String` | No | ISO 4217 currency code (e.g., EUR, USD, CHF) |
| `amount` | `BigDecimal` | No | Monetary amount; must be > 0; scale=2 |
| `valueDate` | `LocalDate` | No | Settlement date; must be ≥ today + 2 days |
| `minimumRate` | `BigDecimal` | No | Minimum acceptable rate; must be ≥ 0; scale=8 |
| `tenor` | `Tenor` | Yes | Required for TERM orders; null for ON_CALL |
| `noticePeriod` | `NoticePeriod` | Yes | Required for ON_CALL orders; null for TERM |
| `sourceContractNumber` | `ContractNumber` | Yes | Existing contract referenced by Increase/Decrease/Redemption; null for Subscription |
| `desiredCounterpartyComment` | `String` | Yes | Optional free-text counterparty preference from Portfolio Management |
| `status` | `OrderStatus` | No | Current lifecycle state |
| `assignment` | `Assignment` | Yes | Current Trader assignment; null when not assigned |
| `executionDetails` | `ExecutionDetails` | Yes | Populated only upon execution; null otherwise |
| `rejectionReason` | `String` | Yes | Reason for rejection; null unless status is REJECTED |
| `createdAt` | `Instant` | No | Timestamp of order creation in the system |
| `updatedAt` | `Instant` | No | Timestamp of last modification |

#### Key Methods

| Method | Signature | Effect |
|--------|-----------|--------|
| Create (factory) | `MoneyMarketOrder.create(command, today)` | Validates all creation invariants; sets status to RECEIVED |
| Assign | `assign(traderId, now)` | RECEIVED → ASSIGNED; sets Assignment |
| Unassign | `unassign(traderId, now)` | ASSIGNED → RECEIVED; clears Assignment; only assigned Trader |
| Update | `update(command, traderId, today)` | Validates ASSIGNED status and Trader identity; updates mutable fields |
| Execute | `execute(command, traderId, dealingRef, contractNum, now)` | ASSIGNED → EXECUTED; creates ExecutionDetails; only assigned Trader |
| Cancel | `cancel(now)` | RECEIVED → CANCELLED |
| Reject | `reject(reason, now)` | RECEIVED → REJECTED; stores rejectionReason |

## Value Objects

### ExternalOrderReference

Typed wrapper around `String`. Uniqueness key for idempotent order intake.

| Field | Type | Constraints |
|-------|------|-------------|
| `value` | `String` | Not blank; max 100 characters |

Equality based on `value`. Immutable.

### ContractNumber

Typed wrapper around `String`. Used for both `sourceContractNumber` (existing contract reference at intake) and `generatedContractNumber` (created at execution for Deposits integration).

| Field | Type | Constraints |
|-------|------|-------------|
| `value` | `String` | Not blank; max 50 characters; generated values prefixed with `CN-` |

### DealingReference

Typed wrapper around `String`. System-generated at execution time.

| Field | Type | Constraints |
|-------|------|-------------|
| `value` | `String` | Not blank; max 50 characters; prefixed with `DL-` |

### PortfolioNumber

Typed wrapper around `String`. Identifier from the external Portfolio Management system.

| Field | Type | Constraints |
|-------|------|-------------|
| `value` | `String` | Not blank; max 50 characters |

### TraderId

Typed wrapper around `String`. Identifies a Trader.

| Field | Type | Constraints |
|-------|------|-------------|
| `value` | `String` | Not blank; max 100 characters |

### Assignment

Composite value object representing an active Trader assignment.

| Field | Type | Constraints |
|-------|------|-------------|
| `traderId` | `TraderId` | Not null |
| `assignedAt` | `Instant` | Not null |

Immutable. Created when order is assigned; cleared (set to null on aggregate) when unassigned.

### ExecutionDetails

Composite value object capturing all data recorded at execution time. Immutable once created.

| Field | Type | Constraints |
|-------|------|-------------|
| `executedRate` | `BigDecimal` | Not null; ≥ 0; scale=8 |
| `counterparty` | `String` | Not blank; max 200 characters |
| `executionTime` | `Instant` | Not null; system-generated |
| `dealingReference` | `DealingReference` | Not null; system-generated |
| `generatedContractNumber` | `ContractNumber` | Not null; system-generated |

## Enumerations

### OrderType

| Value | Description | Allowed OrderOperations |
|-------|-------------|----------------------|
| `TERM` | Fixed-duration deposit | SUBSCRIPTION only |
| `ON_CALL` | Demand deposit with notice period | SUBSCRIPTION, INCREASE, DECREASE, REDEMPTION |

The aggregate validates OrderOperation against OrderType at creation time.

### OrderOperation

| Value | Description | Requires sourceContractNumber | Requires Tenor/NoticePeriod |
|-------|-------------|-------------------------------|----------------------------|
| `SUBSCRIPTION` | New order | No | Yes (Tenor for TERM, NoticePeriod for ON_CALL) |
| `INCREASE` | Increase existing contract amount | Yes | No |
| `DECREASE` | Decrease existing contract amount | Yes | No |
| `REDEMPTION` | Close/redeem existing contract | Yes | No |

### OrderStatus

| Value | Description | Transitions From | Transitions To |
|-------|-------------|------------------|----------------|
| `RECEIVED` | Order received from Portfolio Management | (initial) | ASSIGNED, CANCELLED, REJECTED |
| `ASSIGNED` | Order assigned to a Trader | RECEIVED | RECEIVED (unassign), EXECUTED |
| `EXECUTED` | Execution confirmed by Trader | ASSIGNED | (terminal) |
| `CANCELLED` | Order cancelled | RECEIVED | (terminal) |
| `REJECTED` | Order rejected | RECEIVED | (terminal) |

The `OrderStatus` enum contains a `transitionTo(OrderStatus target)` method that validates the transition and throws `InvalidStatusTransitionException` for illegal transitions.

### Tenor

Allowed values for Term orders.

| Value | Code | Description |
|-------|------|-------------|
| `_1W` | `1W` | 1 week |
| `_2W` | `2W` | 2 weeks |
| `_1M` | `1M` | 1 month |
| `_3M` | `3M` | 3 months |
| `_6M` | `6M` | 6 months |
| `_1Y` | `1Y` | 1 year |

Java enum constants prefixed with underscore because identifiers cannot start with a digit. Each enum value has a `code` field containing the business representation (e.g., `_1W.getCode()` returns `"1W"`).

### NoticePeriod

Allowed values for OnCall orders.

| Value | Code | Description |
|-------|------|-------------|
| `_24H` | `24H` | 24-hour notice |
| `_48H` | `48H` | 48-hour notice |

Same underscore prefix convention as Tenor.

## Domain Exceptions

| Exception | Thrown When |
|-----------|------------|
| `InvalidOrderException` | Order creation or update violates a domain invariant (invalid OrderType/OrderOperation combination, missing required fields, Amount ≤ 0, MinimumRate < 0, ValueDate too soon, invalid Tenor/NoticePeriod) |
| `InvalidStatusTransitionException` | Attempted status transition is not allowed (e.g., ASSIGNED → CANCELLED) |
| `OrderNotFoundException` | Order with given ID does not exist |

## Relationship Diagram

```text
MoneyMarketOrder (aggregate root)
├── externalOrderReference: ExternalOrderReference
├── orderType: OrderType (enum)
├── orderOperation: OrderOperation (enum)
├── portfolioNumber: PortfolioNumber
├── status: OrderStatus (enum)
├── tenor?: Tenor (enum)
├── noticePeriod?: NoticePeriod (enum)
├── sourceContractNumber?: ContractNumber
├── assignment?: Assignment
│   ├── traderId: TraderId
│   └── assignedAt: Instant
└── executionDetails?: ExecutionDetails
    ├── executedRate: BigDecimal
    ├── counterparty: String
    ├── executionTime: Instant
    ├── dealingReference: DealingReference
    └── generatedContractNumber: ContractNumber
```
