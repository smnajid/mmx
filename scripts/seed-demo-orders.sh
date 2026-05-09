#!/usr/bin/env bash
# Posts demo Money Market orders to the intake API for local UI/API testing.
#
# Prerequisites: backend running (e.g. mvn spring-boot:run -pl mmx-bootstrap -Dspring-boot.run.profiles=local)
# Default URL: http://localhost:8080  (override with BASE_URL)
#
# User Story 2 (desk-wide workspace Assigned): after seeding, assigns two probe orders as TRADER_A
# and asserts TRADER_B sees the same rows on GET .../term/assigned and .../oncall/assigned, while
# legacy GET .../assigned stays assignee-scoped for TRADER_B. Requires jq. Skip with VERIFY_US2=0.
#
# Domain rules enforced by the server:
# - valueDate must be at least today + 2 calendar days
# - TERM: SUBSCRIPTION only; tenor required; no noticePeriod
# - ON_CALL: SUBSCRIPTION / INCREASE / DECREASE / REDEMPTION; noticePeriod required; no tenor
# - INCREASE, DECREASE, REDEMPTION require sourceContractNumber
#
# Trader header is not required for intake. List endpoints use any X-Trader-Id (e.g. demo-trader from the SPA).

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
VERIFY_US2="${VERIFY_US2:-1}"
TRADER_A="${TRADER_A:-demo-trader-alice}"
TRADER_B="${TRADER_B:-demo-trader-bob}"
RUN_ID="$(date +%s)"
VALUE_DATE="$(python3 -c "from datetime import date, timedelta; print((date.today() + timedelta(days=3)).isoformat())")"

LAST_ORDER_ID=""

post_order() {
  local label="$1"
  local json="$2"
  local code
  code="$(curl -sS -o /tmp/mmx-seed-body.json -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/orders" \
    -H "Content-Type: application/json" \
    -d "${json}")"
  if [[ "${code}" != "201" && "${code}" != "200" ]]; then
    echo "FAIL ${label} -> HTTP ${code}" >&2
    cat /tmp/mmx-seed-body.json >&2
    exit 1
  fi
  LAST_ORDER_ID="$(jq -r '.orderId // empty' /tmp/mmx-seed-body.json)"
  if [[ -z "${LAST_ORDER_ID}" ]]; then
    echo "WARN ${label}: missing orderId in response" >&2
  fi
  echo "OK   ${label} (HTTP ${code})"
}

assign_order() {
  local label="$1"
  local order_id="$2"
  local trader="$3"
  local code
  code="$(curl -sS -o /tmp/mmx-assign-body.json -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/orders/${order_id}/assign" \
    -H "X-Trader-Id: ${trader}")"
  if [[ "${code}" != "200" ]]; then
    echo "FAIL assign ${label} -> HTTP ${code}" >&2
    cat /tmp/mmx-assign-body.json >&2
    exit 1
  fi
  echo "OK   assign ${label} as ${trader} (HTTP ${code})"
}

# Workspace Assigned lists are desk-wide (US2): any X-Trader-Id should see the same assigned rows for that type.
workspace_assigned_contains() {
  local path="$1"
  local order_id="$2"
  local trader="$3"
  local body
  body="$(curl -sS -H "X-Trader-Id: ${trader}" "${BASE_URL}/api/v1/orders/${path}?page=0&size=500")"
  if echo "${body}" | jq -e --arg id "${order_id}" 'any(.content[]?; .orderId == $id)' >/dev/null; then
    return 0
  fi
  echo "FAIL US2: expected order ${order_id} on GET /api/v1/orders/${path} for X-Trader-Id=${trader}" >&2
  echo "${body}" | jq . >&2 || echo "${body}" >&2
  return 1
}

# Deprecated flat list: still filtered to orders assigned to the requesting trader.
flat_assigned_excludes() {
  local order_id="$1"
  local trader="$2"
  local body
  body="$(curl -sS -H "X-Trader-Id: ${trader}" "${BASE_URL}/api/v1/orders/assigned?page=0&size=500")"
  if echo "${body}" | jq -e --arg id "${order_id}" 'any(.content[]?; .orderId == $id)' >/dev/null; then
    echo "FAIL US2: legacy GET /assigned must not show another trader's order (${order_id}) for X-Trader-Id=${trader}" >&2
    return 1
  fi
  return 0
}

echo "Seeding demo orders -> ${BASE_URL} (valueDate=${VALUE_DATE}, run=${RUN_ID})"
echo

# --- Term (subscription only): varied portfolios, currencies, tenors ---
post_order "TERM EUR 1M 3M" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-TERM-EUR-3M",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-LIQUIDITY",
  "currency": "EUR",
  "amount": 1000000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 3.45,
  "tenor": "3M",
  "desiredCounterpartyComment": "Benchmark placement — quarterly roll"
}
EOF
)"

post_order "TERM USD 2.5M 6M" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-TERM-USD-6M",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-TREASURY",
  "currency": "USD",
  "amount": 2500000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 4.10,
  "tenor": "6M",
  "desiredCounterpartyComment": "Split across two counterparts if needed"
}
EOF
)"

post_order "TERM GBP 500k 1W" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-TERM-GBP-1W",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-MM-UK",
  "currency": "GBP",
  "amount": 500000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 4.875,
  "tenor": "1W",
  "desiredCounterpartyComment": "Short bridge ahead of coupon receipt"
}
EOF
)"

post_order "TERM CHF 750k 1Y" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-TERM-CHF-1Y",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-RESERVES",
  "currency": "CHF",
  "amount": 750000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 1.05,
  "tenor": "1Y"
}
EOF
)"

# --- On-call subscriptions ---
post_order "ON_CALL EUR 500k 24H sub" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-OC-EUR-SUB-24H",
  "orderType": "ON_CALL",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-CASH",
  "currency": "EUR",
  "amount": 500000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 2.85,
  "noticePeriod": "24H"
}
EOF
)"

post_order "ON_CALL USD 1.2M 48H sub" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-OC-USD-SUB-48H",
  "orderType": "ON_CALL",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-LIQUIDITY",
  "currency": "USD",
  "amount": 1200000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 3.05,
  "noticePeriod": "48H",
  "desiredCounterpartyComment": "Prefer same-day confirmation"
}
EOF
)"

# --- On-call lifecycle (require sourceContractNumber) ---
post_order "ON_CALL EUR increase" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-OC-EUR-INC",
  "orderType": "ON_CALL",
  "orderOperation": "INCREASE",
  "portfolioNumber": "PF-CASH",
  "currency": "EUR",
  "amount": 250000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 2.90,
  "noticePeriod": "24H",
  "sourceContractNumber": "CN-DEMO-BASE-001"
}
EOF
)"

post_order "ON_CALL USD decrease" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-OC-USD-DEC",
  "orderType": "ON_CALL",
  "orderOperation": "DECREASE",
  "portfolioNumber": "PF-TREASURY",
  "currency": "USD",
  "amount": 400000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 3.00,
  "noticePeriod": "48H",
  "sourceContractNumber": "CN-DEMO-BASE-002"
}
EOF
)"

post_order "ON_CALL EUR redemption" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-OC-EUR-RED",
  "orderType": "ON_CALL",
  "orderOperation": "REDEMPTION",
  "portfolioNumber": "PF-MM-UK",
  "currency": "EUR",
  "amount": 300000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 0.0,
  "noticePeriod": "24H",
  "sourceContractNumber": "CN-DEMO-BASE-003",
  "desiredCounterpartyComment": "Full exit — align with fund cutoff"
}
EOF
)"

echo
if [[ "${VERIFY_US2}" == "1" ]]; then
  command -v jq >/dev/null 2>&1 || {
    echo "FAIL: jq is required for US2 checks (install jq or set VERIFY_US2=0)" >&2
    exit 1
  }

  echo "--- US2 probe orders (desk-wide workspace Assigned) ---"
  post_order "US2 TERM (probe)" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-US2-TERM",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-US2",
  "currency": "EUR",
  "amount": 100000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 3.40,
  "tenor": "3M"
}
EOF
)"
  US2_TERM_ID="${LAST_ORDER_ID}"

  post_order "US2 ON_CALL (probe)" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-US2-OC",
  "orderType": "ON_CALL",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-US2",
  "currency": "EUR",
  "amount": 50000.00,
  "valueDate": "${VALUE_DATE}",
  "minimumRate": 2.80,
  "noticePeriod": "24H"
}
EOF
)"
  US2_ONCALL_ID="${LAST_ORDER_ID}"

  [[ -n "${US2_TERM_ID}" && -n "${US2_ONCALL_ID}" ]] || {
    echo "FAIL US2: could not read probe orderIds from intake responses" >&2
    exit 1
  }

  assign_order "US2 TERM" "${US2_TERM_ID}" "${TRADER_A}"
  assign_order "US2 ON_CALL" "${US2_ONCALL_ID}" "${TRADER_A}"

  echo "--- US2 assertions (workspace lists visible to ${TRADER_B}; flat /assigned not) ---"
  workspace_assigned_contains "term/assigned" "${US2_TERM_ID}" "${TRADER_B}"
  workspace_assigned_contains "oncall/assigned" "${US2_ONCALL_ID}" "${TRADER_B}"
  echo "OK   US2 workspace Assigned lists include probe orders for ${TRADER_B}"

  flat_assigned_excludes "${US2_TERM_ID}" "${TRADER_B}"
  flat_assigned_excludes "${US2_ONCALL_ID}" "${TRADER_B}"
  echo "OK   US2 legacy GET /assigned excludes ${TRADER_A}'s orders for ${TRADER_B}"

  workspace_assigned_contains "term/assigned" "${US2_TERM_ID}" "${TRADER_A}"
  workspace_assigned_contains "oncall/assigned" "${US2_ONCALL_ID}" "${TRADER_A}"
  echo "OK   US2 assignee (${TRADER_A}) still sees probes on workspace Assigned"
  echo
fi

echo "Done. Try:"
echo "  curl -sS -H 'X-Trader-Id: demo-trader' '${BASE_URL}/api/v1/orders/term/received?page=0&size=20' | jq ."
echo "  curl -sS -H 'X-Trader-Id: demo-trader' '${BASE_URL}/api/v1/orders/oncall/received?page=0&size=20' | jq ."
echo "  US2 (desk-wide Assigned): curl -sS -H 'X-Trader-Id: ${TRADER_B}' '${BASE_URL}/api/v1/orders/term/assigned?page=0&size=20' | jq ."
echo "Or open the Angular app at http://localhost:4200 (ensure trader id matches if you changed it in the UI)."
echo "Env: VERIFY_US2=0 skips probes; TRADER_A / TRADER_B override demo traders."
