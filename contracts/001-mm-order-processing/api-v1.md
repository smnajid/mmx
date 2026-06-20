# REST API Contract: Money Market Order Processing V1

**Canonical OpenAPI 3 spec (contract-first, codegen)**: [openapi.yaml](./openapi.yaml)

**Base URL**: `/api/v1`
**Content-Type**: `application/json`
**Trader Identity**: `X-Trader-Id` request header (required for Trader-facing endpoints)

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
  "status": "RECEIVED | ASSIGNED | EXECUTED | CANCELLED | REJECTED",
  "assignedTraderId": "string | null",
  "createdAt": "2026-04-28T21:30:00Z"
}
```

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
  "desiredCounterpartyComment": "string | null",
  "status": "RECEIVED | ASSIGNED | EXECUTED | CANCELLED | REJECTED",
  "assignedTraderId": "string | null",
  "assignedAt": "2026-04-28T21:30:00Z | null",
  "executedRate": 3.50000000,
  "counterparty": "string | null",
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
  "desiredCounterpartyComment": "Prefer BankCo if available"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| externalOrderReference | string | Yes | Idempotency key; max 100 chars |
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
| desiredCounterpartyComment | string | No | Free text; max 500 chars |

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `201 Created` | New order created | `{ "orderId": "uuid", "status": "RECEIVED" }` |
| `200 OK` | Duplicate externalOrderReference (idempotent) | `{ "orderId": "uuid", "status": "RECEIVED" }` |
| `400 Bad Request` | Validation errors | ErrorResponse with details |

---

### 2. List Received Term Orders

**GET** `/api/v1/orders/term/received`

**Headers**: `X-Trader-Id` required.

#### Query Parameters

| Param | Type | Default | Description |
|-------|------|---------|-------------|
| page | int | 0 | Zero-based page number |
| size | int | 20 | Page size (max 100) |

#### Responses

| Status | Body |
|--------|------|
| `200 OK` | `{ "content": [OrderSummaryResponse, ...], "totalElements": 42, "page": 0, "size": 20 }` |

---

### 3. List Received OnCall Orders

**GET** `/api/v1/orders/oncall/received`

**Headers**: `X-Trader-Id` required.

#### Query Parameters

Same as List Received Term Orders.

#### Responses

| Status | Body |
|--------|------|
| `200 OK` | `{ "content": [OrderSummaryResponse, ...], "totalElements": 15, "page": 0, "size": 20 }` |

---

### 4. Get Order Details

**GET** `/api/v1/orders/{orderId}`

**Headers**: `X-Trader-Id` required.

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

**Headers**: `X-Trader-Id` required (used as the assignee).

#### Request

No request body. The Trader identity comes from the `X-Trader-Id` header.

#### Responses

| Status | Condition | Body |
|--------|-----------|------|
| `200 OK` | Successfully assigned | OrderDetailsResponse |
| `404 Not Found` | Order does not exist | ErrorResponse |
| `409 Conflict` | Order not in RECEIVED status | ErrorResponse with `INVALID_STATUS_TRANSITION` |

---

### 6. Unassign Order

**POST** `/api/v1/orders/{orderId}/unassign`

**Headers**: `X-Trader-Id` required (must match assigned Trader).

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

**Headers**: `X-Trader-Id` required (filters to this Trader's assignments).

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

**Headers**: `X-Trader-Id` required (must match assigned Trader).

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

**Headers**: `X-Trader-Id` required (must match assigned Trader).

#### Request

```json
{
  "executedRate": 3.50000000,
  "counterparty": "BankCo International"
}
```

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| executedRate | decimal | Yes | Must be ≥ 0; when the order has a `minimumRate` from intake, must be ≥ `minimumRate` |
| counterparty | string | Yes | Non-blank; max 200 chars |

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

**Headers**: `X-Trader-Id` required.

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

**Headers**: `X-Trader-Id` required.

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

### Decimal Serialization

- `amount`: serialized with 2 decimal places (e.g., `5000000.00`)
- `minimumRate` (when present) and `executedRate`: serialized with 8 decimal places (e.g., `3.25000000`); `minimumRate` may be omitted or null when Portfolio Management did not set a floor at intake
- JSON numbers, not strings (Jackson handles BigDecimal precision)

### Date/Time Formats

- `valueDate`: ISO 8601 date (`YYYY-MM-DD`)
- All timestamps: ISO 8601 with timezone (`YYYY-MM-DDTHH:mm:ssZ`)
