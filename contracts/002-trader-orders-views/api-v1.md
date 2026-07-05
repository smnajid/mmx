# REST API Contract: Money Market Order Processing (baseline + trader workspaces)

**Canonical OpenAPI 3 spec (contract-first, codegen)**: [openapi.yaml](./openapi.yaml) — **v2.0.0** adds **`ROUTED`** order status, requires **`legalEntityCode`** on order-creation counterparty endpoints (grant-scoped thin proxies for TradingClient), and scopes counterparty listing by active legal entity. **v1.9.0** extends **`GET /api/v1/order-creation/oncall/contract-info`** with required **`institutionCode`** and **`counterparty`** from the executed Subscription. **v1.8.0** adds **`GET /api/v1/order-creation/contracts`** (live contracts listing for portfolio pickers). **v1.7.0** adds **Order Creation** wizard options (`/api/v1/order-creation/*`), requires **`institutionCode`** at intake, and makes execute **rate-only** (no `institutionCode` on execute). **v1.5.0** added **`handoffStatus`** on executed-list summaries (`PENDING` / `PUBLISHED` / `FAILED`) for back-office Kafka delivery visibility ([FR-013](spec.md)); Async companion for outbound Kafka remains [**asyncapi.yaml**](./asyncapi.yaml).

**Base URL**: `/api/v1`
**Content-Type**: `application/json`
**MMXUser Identity**: `X-User-Id` request header (required for Trader-facing endpoints)

## Common Response Models

### ErrorResponse

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable description",
  "details": [
    { "field": "fieldName", "message": "Specific validation message" }
  ]
}
```

Error codes: `VALIDATION_ERROR`, `ORDER_NOT_FOUND`, `INVALID_STATUS_TRANSITION`, `UNAUTHORIZED_TRADER`, `DUPLICATE_ORDER`.

### OrderSummaryResponse

```json
{
  "orderId": "uuid",
  "externalOrderReference": "string",
  "orderType": "TERM | ON_CALL",
  "orderOperation": "SUBSCRIPTION | INCREASE | DECREASE | REDEMPTION",
  "portfolioNumber": "string",
  "currency": "string",
  "amount": 1000000.00,
  "valueDate": "2026-05-02",
  "minimumRate": 3.25000000,
  "tenor": "1W | 2W | 1M | 3M | 6M | 1Y | null",
  "noticePeriod": "24H | 48H | null",
  "status": "RECEIVED | ROUTED | ASSIGNED | EXECUTED | ACCOUNTED | CANCELLED | REJECTED",
  "counterparty": "string | null",
  "institutionCode": "string | null",
  "handoffStatus": "PENDING | PUBLISHED | FAILED | omitted",
  "assignedTraderId": "string | null",
  "createdAt": "2026-04-28T21:30:00Z"
}
```

`counterparty` and `institutionCode` are set at intake from Portfolio Management’s institution selection. When absent (e.g. legacy rows), fields are **omitted** from JSON responses (omit-null), not serialized as `null`.

`handoffStatus` applies to **EXECUTED** rows on workspace executed-list endpoints (`GET .../term/executed`, `GET .../oncall/executed`). It reflects durable outbound **Kafka handoff** state (transactional outbox + relay), **not** `OrderStatus`: **`PENDING`** — outbox written, producer not yet acked; **`PUBLISHED`** — handoff message acked toward back-office, awaiting accounting; **`FAILED`** — retries exhausted (integration problem). The field is **omitted** when null or for non-EXECUTED summaries (omit-null).

`minimumRate` is `null` (or omitted in responses that omit-null) when Portfolio Management did not supply an execution-floor indication at intake. It is not mutable after reception.

`tenor` is `null` for on-call orders or when not applicable; populated for term instruments.

`noticePeriod` is `null` for term orders or when not applicable; populated for on-call instruments (`24H`, `48H`).

### OrderDetailsResponse

```json
{
  "orderId": "uuid",
  "externalOrderReference": "string",
  "orderType": "TERM | ON_CALL",
  "orderOperation": "SUBSCRIPTION | INCREASE | DECREASE | REDEMPTION",
  "portfolioNumber": "string",
  "currency": "string",
  "amount": 1000000.00,
  "valueDate": "2026-05-02",
  "minimumRate": 3.25000000,
  "tenor": "1W | 2W | 1M | 3M | 6M | 1Y | null",
  "noticePeriod": "24H | 48H | null",
  "sourceContractNumber": "string | null",
  "institutionCode": "BNKCO",
  "status": "RECEIVED | ROUTED | ASSIGNED | EXECUTED | ACCOUNTED | CANCELLED | REJECTED",
  "assignedTraderId": "string | null",
  "assignedAt": "2026-04-28T21:30:00Z | null",
  "executedRate": 3.50000000,
  "counterparty": "BankCo",
  "executionTime": "2026-04-28T22:00:00Z | null",
  "dealingReference": "string | null",
  "generatedContractNumber": "string | null",
  "rejectionReason": "string | null",
  "createdAt": "2026-04-28T21:30:00Z",
  "updatedAt": "2026-04-28T21:35:00Z"
}
```

Execution identifiers: `dealingReference` is always newly generated when an order is executed. `generatedContractNumber` is newly allocated for **SUBSCRIPTION** executions; for **INCREASE**, **DECREASE**, and **REDEMPTION** it matches the persisted intake `sourceContractNumber`.

---

## Endpoints

### 1. Receive Order

**POST** `/api/v1/orders`

Intake endpoint called by the external Portfolio Management system.

#### Request

```json
{
  "externalOrderReference": "PM-2026-00123",
  "legalEntityCode": "LOC",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-001",
  "currency": "EUR",
  "amount": 5000000.00,
  "valueDate": "2026-05-02",
  "minimumRate": 3.25000000,
  "tenor": "3M",
  "noticePeriod": null,
  "sourceContractNumber": null,
  "institutionCode": "BNKCO"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| externalOrderReference | string | Yes | Idempotency key; max 100 chars |
| legalEntityCode | string | Yes | Owning LegalEntity code; exactly 3 characters (e.g. LOC, PAR) |
| orderType | string | Yes | `TERM` or `ON_CALL` |
| orderOperation | string | Yes | `SUBSCRIPTION`, `INCREASE`, `DECREASE`, `REDEMPTION` |
| portfolioNumber | string | Yes | Max 50 chars |
| currency | string | Yes | ISO 4217; 3 chars |
| amount | decimal | Yes | Must be > 0 |
| valueDate | date | Yes | ISO 8601; must be ≥ today + 2 days |
| minimumRate | decimal | No | Portfolio Manager execution floor when provided; must be ≥ 0 if present; when absent, Trader executes subject to best market conditions |
| tenor | string | Conditional | Required if orderType=TERM; one of: 1W, 2W, 1M, 3M, 6M, 1Y |
| noticePeriod | string | Conditional | Required if orderType=ON_CALL; one of: 24H, 48H |
| sourceContractNumber | string | Conditional | Required if orderOperation ∈ {INCREASE, DECREASE, REDEMPTION}. For SUBSCRIPTION the system does not store this field; if PM sends it, it is discarded at reception. |
| institutionCode | string | Yes | Active onboarded institution from the settings catalog; max 32 chars. Counterparty display name is derived server-side at intake. |

**Breaking change (PM order creation wizard):** `desiredCounterpartyComment` is removed from intake. Portfolio Management selects the counterparty via `institutionCode` using the Order Creation options API.

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `201 Created` | New order created | `{ "orderId": "uuid", "status": "RECEIVED", "legalEntityCode": "LOC" }` |
| `200 OK` | Duplicate `(legalEntityCode, externalOrderReference)` (idempotent) | `{ "orderId": "uuid", "status": "RECEIVED", "legalEntityCode": "LOC" }` |
| `400 Bad Request` | Validation errors | ErrorResponse with details |

---

### 1b. Re-scope session

**POST** `/api/v1/session/scope`

Re-binds the caller's session to a held `(legalEntityCode, role)` scope. Requires header `X-User-Id`. Role is session-carried (not a separate header).

#### Request

```json
{
  "legalEntityCode": "PAR",
  "role": "CLIENT_REPRESENTATIVE"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| legalEntityCode | string | Yes | Exactly 3 characters |
| role | string | Yes | `TRADER` or `CLIENT_REPRESENTATIVE` |

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Scope rebound | `{ "legalEntityCode": "PAR", "role": "CLIENT_REPRESENTATIVE" }` |
| `400 Bad Request` | Validation errors | ErrorResponse |
| `403 Forbidden` | User does not hold the requested scope | ErrorResponse |

---

### 2. List Received Term Orders

**GET** `/api/v1/orders/term/received`

**Headers**: `X-User-Id` required.

#### Query Parameters

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| page | int | 0 | Zero-based page number |
| size | int | 20 | Page size (max 100) |
| receivedView | enum | `NEAR_TERM` | `NEAR_TERM`: only orders whose `valueDate` falls within **today and the next two calendar days** inclusive in the **Europe/Paris** business calendar. `ALL`: full `RECEIVED` cohort for Term (no value-date window). |

#### Responses

| Status | Body |
|--------|------|
| `200 OK` | `{ "content": [OrderSummaryResponse, ...], "totalElements": 42, "page": 0, "size": 20 }` |

---

### 3. List Received OnCall Orders

**GET** `/api/v1/orders/oncall/received`

**Headers**: `X-User-Id` required.

#### Query Parameters

Same as List Received Term Orders (including `receivedView`).

#### Responses

| Status | Body |
|--------|------|
| `200 OK` | `{ "content": [OrderSummaryResponse, ...], "totalElements": 15, "page": 0, "size": 20 }` |

---

### Workspace-scoped lists (002-trader-orders-views)

These endpoints ensure **Term** and **OnCall** orders are not mixed on the same list. **US1** uses them for Received / Assigned / Executed sub-views inside each workspace.

| Method | Path | Summary |
|--------|------|---------|
| GET | `/api/v1/orders/term/assigned` | **Desk-wide** assigned **Term** orders (all assignees; `X-User-Id` is actor only, not an assignee filter) |
| GET | `/api/v1/orders/oncall/assigned` | **Desk-wide** assigned **OnCall** orders (all assignees) |
| GET | `/api/v1/orders/term/executed` | **Term** orders in `EXECUTED` status ( **`ACCOUNTED`** excluded; workspace executed-not-accounted view) |
| GET | `/api/v1/orders/oncall/executed` | **OnCall** orders in `EXECUTED` status ( **`ACCOUNTED`** orders excluded ) |

**Headers**: `X-User-Id` required. **Query**: `page`, `size` — same as received list endpoints.

**Deprecated**: `GET /api/v1/orders/assigned` (mixes order types) — clients MUST migrate to the workspace-scoped paths above.

List rows include **counterparty** when execution exists (omit-null).

---

### Back-office accounted callback

This path is intentionally **outside** Trader header rules: callers MUST NOT send `X-User-Id` for authentication baseline (endpoint remains reachable with no Trader identity header).

#### Request

**POST** `/api/v1/back-office/orders/{orderId}/accounted`

Optional JSON body:

```json
{}
```

or

```json
{
  "accountedAt": "2026-05-02T09:30:00Z"
}
```

`accountedAt` is optional metadata; implementations may ignore it and use server time for the lifecycle transition.

#### Responses

| Status | Condition |
|--------|-----------|
| `200 OK` | Order transitioned `EXECUTED` → `ACCOUNTED`, **or** was already `ACCOUNTED` (idempotent replay) |
| `404 Not Found` | Unknown `orderId` — `ORDER_NOT_FOUND` |
| `409 Conflict` | Order not in `EXECUTED` status — `INVALID_STATUS_TRANSITION` |

---

### Back-office OnCall rate confirmation callback

Unauthenticated baseline (no `X-User-Id`).

#### Request

**POST** `/api/v1/back-office/oncall-rates/{segmentId}/confirmed`

Optional empty JSON body; mmx stamps `validatedAt` server-side.

#### Responses

| Status | Condition |
|--------|-----------|
| `200 OK` | Segment transitioned `PENDING_CONFIRMATION` → `VALID`, **or** was already `VALID` (idempotent) |
| `404 Not Found` | Unknown `segmentId` — `ONCALL_SEGMENT_NOT_FOUND` |
| `409 Conflict` | Segment is `CANCELED` — `ONCALL_SEGMENT_CANCELED` |

---

### OnCall rate settings (institution-scoped)

Trader operations require `X-User-Id`.

#### List segments

**GET** `/api/v1/settings/institutions/{institutionCode}/oncall-rates`

Returns all rate segments for the institution (all curve points).

#### Add rate

**POST** `/api/v1/settings/institutions/{institutionCode}/oncall-rates`

```json
{
  "currency": "EUR",
  "noticePeriod": "24H",
  "rate": 3.25,
  "valueDate": "2026-05-31"
}
```

| Status | Condition |
|--------|-----------|
| `201 Created` | New `PENDING_CONFIRMATION` segment; prior open segment end-dated to `valueDate − 1` |
| `400 Bad Request` | Backdated `valueDate` — `ONCALL_BACKDATED_VALUE_DATE` |
| `404 Not Found` | Unknown institution — `INSTITUTION_NOT_FOUND` |
| `409 Conflict` | Curve point already has pending segment — `ONCALL_PENDING_EXISTS` |

#### Cancel pending segment

**POST** `/api/v1/settings/institutions/{institutionCode}/oncall-rates/{segmentId}/cancel`

| Status | Condition |
|--------|-----------|
| `200 OK` | Segment `CANCELED`; prior segment end restored to `2999-12-31` when applicable |
| `404 Not Found` | Unknown segment or institution |
| `409 Conflict` | Segment not `PENDING_CONFIRMATION` — `ONCALL_INVALID_SEGMENT_STATUS` |

#### OnCallRateSegmentResponse

```json
{
  "segmentId": "uuid",
  "institutionCode": "HSBC-01",
  "currency": "EUR",
  "noticePeriod": "24H",
  "rate": 3.25,
  "valueDate": "2026-05-31",
  "endDate": "2999-12-31",
  "status": "PENDING_CONFIRMATION | VALID | CANCELED",
  "validatedAt": "2026-05-31T10:00:00Z"
}
```

`validatedAt` is present only when `status` is `VALID`.

---

### 4. Get Order Details

**GET** `/api/v1/orders/{orderId}`

**Headers**: `X-User-Id` required.

#### Path Parameters

| Param | Type | Description |
|-------|------|-------------|
| orderId | UUID | Internal order identifier |

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Order found | OrderDetailsResponse |
| `404 Not Found` | Order does not exist | ErrorResponse |

---

### 5. Assign Order

**POST** `/api/v1/orders/{orderId}/assign`

**Headers**: `X-User-Id` required (used as the assignee).

#### Request

No request body. The Trader identity comes from the `X-User-Id` header.

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Successfully assigned | OrderDetailsResponse |
| `404 Not Found` | Order does not exist | ErrorResponse |
| `409 Conflict` | Order not in RECEIVED status | ErrorResponse with `INVALID_STATUS_TRANSITION` |

---

### 6. Unassign Order

**POST** `/api/v1/orders/{orderId}/unassign`

**Headers**: `X-User-Id` required (must match assigned Trader).

#### Request

No request body.

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Successfully unassigned | OrderDetailsResponse |
| `403 Forbidden` | Caller is not the assigned Trader | ErrorResponse with `UNAUTHORIZED_TRADER` |
| `404 Not Found` | Order does not exist | ErrorResponse |
| `409 Conflict` | Order not in ASSIGNED status | ErrorResponse with `INVALID_STATUS_TRANSITION` |

---

### 7. List Assigned Orders

**GET** `/api/v1/orders/assigned`

**Headers**: `X-User-Id` required (filters to this Trader's assignments).

#### Query Parameters

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| page | int | 0 | Zero-based page number |
| size | int | 20 | Page size (max 100) |

#### Responses

| Status | Body |
|--------|------|
| `200 OK` | `{ "content": [OrderSummaryResponse, ...], "totalElements": 5, "page": 0, "size": 20 }` |

---

### 8. Update Assigned Order

**PUT** `/api/v1/orders/{orderId}`

**Headers**: `X-User-Id` required (must match assigned Trader).

#### Request

All fields are optional; only provided fields are updated.

```json
{
  "amount": 6000000.00,
  "valueDate": "2026-05-05"
}
```

| Field | Type | Notes |
|-------|------|-------|
| amount | decimal | Must be > 0 if provided |
| valueDate | date | Must be ≥ today + 2 days if provided |

`MinimumRate` is not part of this request; it is set only at intake (optional) and cannot be changed by the Trader. `DesiredCounterpartyComment` is likewise intake-only.

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Successfully updated | OrderDetailsResponse |
| `400 Bad Request` | Validation errors | ErrorResponse |
| `403 Forbidden` | Caller is not the assigned Trader | ErrorResponse with `UNAUTHORIZED_TRADER` |
| `404 Not Found` | Order does not exist | ErrorResponse |
| `409 Conflict` | Order not in ASSIGNED status | ErrorResponse with `INVALID_STATUS_TRANSITION` |

---

### 9. Execute Order

**POST** `/api/v1/orders/{orderId}/execute`

**Headers**: `X-User-Id` required (must match assigned Trader).

#### Request

```json
{
  "executedRate": 3.50000000
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| executedRate | decimal | Yes | Must be ≥ 0; when the order has a `minimumRate` from intake, must be ≥ `minimumRate` |

**Breaking change (PM order creation wizard):** `institutionCode` is no longer accepted on execute — it is set at intake by Portfolio Management. The trader provides only `executedRate`. If the trader cannot deal with the PM-chosen counterparty, they must reject the order. The server still validates the intake institution remains active at execute time.

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Successfully executed | OrderDetailsResponse (includes generated dealingReference, generatedContractNumber, executionTime) |
| `400 Bad Request` | Missing or invalid execution data, or `executedRate` below PM `minimumRate` when set | ErrorResponse |
| `403 Forbidden` | Caller is not the assigned Trader | ErrorResponse with `UNAUTHORIZED_TRADER` |
| `404 Not Found` | Order does not exist | ErrorResponse |
| `409 Conflict` | Order not in ASSIGNED status | ErrorResponse with `INVALID_STATUS_TRANSITION` |

---

### 10. Cancel Order

**POST** `/api/v1/orders/{orderId}/cancel`

**Headers**: `X-User-Id` required.

#### Request

No request body.

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Successfully cancelled | OrderDetailsResponse |
| `404 Not Found` | Order does not exist | ErrorResponse |
| `409 Conflict` | Order not in RECEIVED status | ErrorResponse with `INVALID_STATUS_TRANSITION` |

---

### 11. Reject Order

**POST** `/api/v1/orders/{orderId}/reject`

**Headers**: `X-User-Id` required.

#### Request

```json
{
  "reason": "Insufficient portfolio allocation"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| reason | string | Yes | Trader must justify rejection; max 500 chars |

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Successfully rejected | OrderDetailsResponse |
| `403 Forbidden` | Order is ASSIGNED to another Trader | ErrorResponse with `UNAUTHORIZED_TRADER` |
| `404 Not Found` | Order does not exist | ErrorResponse |
| `409 Conflict` | Order not in RECEIVED or ASSIGNED status | ErrorResponse with `INVALID_STATUS_TRANSITION` |

---

## Cross-Cutting Concerns

### Pagination

All list endpoints use Spring's page-based pagination:

```json
{
  "content": [],
  "totalElements": 0,
  "page": 0,
  "size": 20
}
```

### Order Creation Options (PM wizard)

Read-only endpoints consumed by the external Portfolio Management application to build a step-by-step order creation wizard. **No `X-User-Id` header** required. The surface comprises **ten** GET operations (currencies, operations, tenors, notice-periods, counterparties, contract-info, and live contracts).

#### Term currencies

**GET** `/api/v1/order-creation/term/currencies`

Returns currencies where the managed currency is active, has enabled tenors, and at least one active institution has a term rate.

```json
{
  "tradingDate": "2026-06-06",
  "currencies": ["EUR", "USD"]
}
```

#### OnCall currencies

**GET** `/api/v1/order-creation/oncall/currencies`

Returns currencies with at least one open on-call segment from an active institution.

```json
{
  "currencies": ["EUR"]
}
```

#### Term operations

**GET** `/api/v1/order-creation/term/operations?currency=EUR`

Returns allowed Term operations with minimum amounts (Term allows only `SUBSCRIPTION`).

```json
{
  "operations": [
    { "operation": "SUBSCRIPTION", "minAmount": 500000.00 }
  ]
}
```

#### OnCall operations

**GET** `/api/v1/order-creation/oncall/operations?currency=EUR`

Returns all four OnCall operations with their minimum amounts.

#### Term tenors

**GET** `/api/v1/order-creation/term/tenors?currency=EUR`

Returns enabled tenors with at least one counterparty rate.

```json
{
  "tenors": ["1M", "3M"]
}
```

#### OnCall notice periods

**GET** `/api/v1/order-creation/oncall/notice-periods?currency=EUR`

Returns enabled notice periods with at least one open counterparty segment.

```json
{
  "noticePeriods": ["24H"]
}
```

#### Term counterparties

**GET** `/api/v1/order-creation/term/counterparties?legalEntityCode=LOC&currency=EUR&tenor=3M`

| Parameter | Required | Description |
|-----------|----------|-------------|
| `legalEntityCode` | Yes | Owning LegalEntity (3 chars). TradingHub → hub-native institutions; TradingClient → granted thin-proxies only |
| `currency` | Yes | ISO currency code |
| `tenor` | Yes | Term tenor (e.g. `3M`) |

Returns active institutions with the latest term rate per institution, sorted by best rate first. For a **TradingClient** `legalEntityCode`, only thin-proxy institutions with an active delegated grant for the currency and tenor are returned (using hub rates). For a **TradingHub**, all hub institutions with rates are returned.

```json
{
  "counterparties": [
    {
      "institutionCode": "BNKCO",
      "displayName": "BankCo",
      "rate": 3.45,
      "rateDate": "2026-06-06",
      "indicative": false
    }
  ]
}
```

#### OnCall counterparties

**GET** `/api/v1/order-creation/oncall/counterparties?legalEntityCode=LOC&currency=EUR&noticePeriod=24H&valueDate=2026-06-09`

| Parameter | Required | Description |
|-----------|----------|-------------|
| `legalEntityCode` | Yes | Owning LegalEntity (3 chars). TradingHub → hub-native institutions; TradingClient → granted thin-proxies only |
| `currency` | Yes | ISO currency code |
| `noticePeriod` | Yes | OnCall notice period (e.g. `24H`) |
| `valueDate` | Yes | Settlement date (`YYYY-MM-DD`) |

Returns active institutions with a segment covering `valueDate`, sorted by best rate first. For a **TradingClient** `legalEntityCode`, only thin-proxy institutions with an active delegated grant for the currency and notice period are returned. Same `CounterpartyOption` shape as Term counterparties.

#### OnCall contract info

**GET** `/api/v1/order-creation/oncall/contract-info?contractNumber=CT-00042`

Looks up an executed OnCall **Subscription** by `generatedContractNumber`. Returns the contract's `currency`, `noticePeriod`, and the original subscription's `institutionCode` and `counterparty` (display name).

| Status | Body |
|--------|------|
| `200 OK` | `{ "currency": "EUR", "noticePeriod": "24H", "institutionCode": "BNKCO", "counterparty": "BankCo" }` |
| `404 Not Found` | ErrorResponse when no matching executed Subscription exists |

#### Live contracts

**GET** `/api/v1/order-creation/contracts?portfolioNumber=PF-001&orderType=ON_CALL`

Returns **live** contracts for the given portfolio and order type. A contract is materialised when a **Subscription** order is **executed** (`generatedContractNumber` allocated). Both query parameters are required.

**Liveness rules** (applied server-side; only live contracts are returned):

| Order type | Live while… |
|------------|-------------|
| **OnCall** | No **redemption** order exists against the contract (`sourceContractNumber` equals the contract number, `orderOperation` = `REDEMPTION`, status ≠ `CANCELLED`). Received and executed redemptions both disqualify the contract. Increases and decreases do not end a contract. |
| **Term** | Contract **end date** is in the future. End date = subscription `valueDate` + tenor duration (`1W`, `2W`, `1M`, `3M`, `6M`, `1Y`). |

Non-executed Subscriptions (no `generatedContractNumber`) are not contracts and are excluded.

```json
{
  "contracts": [
    {
      "contractNumber": "CT-00042",
      "orderType": "ON_CALL",
      "currency": "EUR",
      "noticePeriod": "24H",
      "valueDate": "2026-06-01",
      "originalAmount": 5000000.00
    },
    {
      "contractNumber": "CT-00100",
      "orderType": "TERM",
      "currency": "EUR",
      "tenor": "3M",
      "valueDate": "2026-06-01",
      "endDate": "2026-09-01",
      "originalAmount": 10000000.00
    }
  ]
}
```

| Field | Type | Notes |
|-------|------|-------|
| contractNumber | string | `generatedContractNumber` of the executed Subscription |
| orderType | string | `TERM` or `ON_CALL` (matches the request filter) |
| currency | string | ISO 4217 (3 chars) |
| noticePeriod | string | OnCall only (`24H`, `48H`); omitted for Term |
| tenor | string | Term only (`1W` … `1Y`); omitted for OnCall |
| valueDate | date | Subscription value date |
| endDate | date | Term only — `valueDate` + tenor; omitted for OnCall |
| originalAmount | number | Subscription amount at creation (reference, not a running balance) |

| Status | Body |
|--------|------|
| `200 OK` | `LiveContractsResponse` (may be an empty `contracts` array) |

---

### Decimal Serialization

- `amount`: serialized with 2 decimal places (e.g., `5000000.00`)
- `minimumRate` (when present) and `executedRate`: serialized with 8 decimal places (e.g., `3.25000000`); `minimumRate` may be omitted or null when Portfolio Management did not set a floor at intake
- JSON numbers, not strings (Jackson handles BigDecimal precision)

### Date/Time Formats

- `valueDate`: ISO 8601 date (`YYYY-MM-DD`)
- All timestamps: ISO 8601 with timezone (`YYYY-MM-DDTHH:mm:ssZ`)
