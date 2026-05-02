#!/usr/bin/env bash
# Posts demo Money Market orders to the intake API for local UI/API testing.
#
# Prerequisites: backend running (e.g. mvn spring-boot:run -pl mmx-bootstrap -Dspring-boot.run.profiles=local)
# Default URL: http://localhost:8080  (override with BASE_URL)
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
RUN_ID="$(date +%s)"
VALUE_DATE="$(python3 -c "from datetime import date, timedelta; print((date.today() + timedelta(days=3)).isoformat())")"

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
  echo "OK   ${label} (HTTP ${code})"
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
echo "Done. Try:"
echo "  curl -sS -H 'X-Trader-Id: demo-trader' '${BASE_URL}/api/v1/orders/term/received?page=0&size=20' | jq ."
echo "  curl -sS -H 'X-Trader-Id: demo-trader' '${BASE_URL}/api/v1/orders/oncall/received?page=0&size=20' | jq ."
echo "Or open the Angular app at http://localhost:4200 (ensure trader id matches if you changed it in the UI)."
