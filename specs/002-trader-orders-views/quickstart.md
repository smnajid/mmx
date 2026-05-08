# Quickstart: User Story 1 — Term vs OnCall workspace

**Goal**: Verify workspace-scoped lists and default **OnCall** entry without cross-type leakage.

## Preconditions

- Backend and DB running per [001 quickstart](../001-mm-order-processing/quickstart.md) (same stack).
- OpenAPI codegen in `mmx-adapter-in-rest` points at `specs/002-trader-orders-views/contracts/openapi.yaml` after implementation tasks (see [plan.md](plan.md)).

## 1. Seed two orders (Term + OnCall)

Use Portfolio intake (`POST /api/v1/orders`) twice with the same baseline payload shape, varying only `orderType` (`TERM` vs `ON_CALL`) and required tenor/notice fields.

## 2. API checks (Trader header)

Replace `BASE` and supply a valid `X-Trader-Id`.

```bash
export BASE=http://localhost:8080
export TID=alice

curl -s "$BASE/api/v1/orders/term/received?page=0&size=20"  -H "X-Trader-Id: $TID" | jq '.content[].orderType' | uniq
# Expect only TERM

curl -s "$BASE/api/v1/orders/oncall/received?page=0&size=20" -H "X-Trader-Id: $TID" | jq '.content[].orderType' | uniq
# Expect only ON_CALL

# After implementation of 002 list endpoints:
curl -s "$BASE/api/v1/orders/term/assigned?page=0&size=20"   -H "X-Trader-Id: $TID" | jq '.content[].orderType' | uniq
curl -s "$BASE/api/v1/orders/oncall/assigned?page=0&size=20" -H "X-Trader-Id: $TID" | jq '.content[].orderType' | uniq
```

**Accept**: No response body mixes `TERM` and `ON_CALL` in `content[]` for a single workspace-scoped URL.

## 3. Angular checks

1. Open the app root — **without** manually choosing Term, the first screen MUST land in the **OnCall** workspace (default route).
2. Switch to **Term** — Received (and Assigned / Executed when wired) MUST show **only** Term rows.
3. Switch back to **OnCall** — lists MUST show **only** OnCall rows; Term rows MUST NOT linger from the prior view (same session).
4. Full page reload — default entry MUST be **OnCall** again (no persisted last workspace).

## 4. Regression

- Legacy `GET /api/v1/orders/assigned` is **deprecated** in OpenAPI v1.1.0; once clients migrate, remove usage from the SPA.
