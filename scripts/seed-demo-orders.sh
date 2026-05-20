#!/usr/bin/env bash
# Post a handful of Money Market orders for local UI smoke testing.
#
# Prerequisites: backend running (e.g. ./mmx-start.sh or mvn spring-boot:run -pl mmx-bootstrap with Postgres up)
# Default URL: http://localhost:8080  (override with BASE_URL)
#
# Intake: valueDate must be ≥ today + 2 calendar days.
#
# Some rows use valueDate = today+2 so they appear under the default Received near-term window
# (today … today+2, Europe/Paris). Others use today+30 — valid for intake but hidden until you enable
# “Show all value dates” on the Received screen.
#
# No verification step — curl exit status only. Use any X-Trader-Id in the SPA (e.g. demo-trader).

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
RUN_ID="$(date +%s)"
VALUE_DATE_NEAR="$(python3 -c "from datetime import date, timedelta; print((date.today() + timedelta(days=2)).isoformat())")"
VALUE_DATE_FAR="$(python3 -c "from datetime import date, timedelta; print((date.today() + timedelta(days=30)).isoformat())")"

post_order() {
  local label="$1"
  local json="$2"
  local code
  code="$(curl -sS -o /dev/null -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/orders" \
    -H "Content-Type: application/json" \
    -d "${json}")"
  if [[ "${code}" != "201" && "${code}" != "200" ]]; then
    echo "FAIL ${label} -> HTTP ${code}" >&2
    exit 1
  fi
  echo "OK   ${label} (${code})"
}

echo "Seeding demo orders -> ${BASE_URL}"
echo "  near-term valueDate=${VALUE_DATE_NEAR}"
echo "  far valueDate=${VALUE_DATE_FAR} (Received: enable \"Show all value dates\")  run=${RUN_ID}"
echo

# Near-term band — visible on default Received
post_order "TERM EUR 3M" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-TERM-EUR",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-DEMO",
  "currency": "EUR",
  "amount": 1000000.00,
  "valueDate": "${VALUE_DATE_NEAR}",
  "minimumRate": 3.45,
  "tenor": "3M",
  "desiredCounterpartyComment": "Demo placement"
}
EOF
)"

post_order "TERM USD 6M" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-TERM-USD",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-DEMO",
  "currency": "USD",
  "amount": 500000.00,
  "valueDate": "${VALUE_DATE_NEAR}",
  "minimumRate": 4.00,
  "tenor": "6M"
}
EOF
)"

# On-call — near-term
post_order "ON_CALL EUR 24H" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-OC-EUR",
  "orderType": "ON_CALL",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-DEMO",
  "currency": "EUR",
  "amount": 750000.00,
  "valueDate": "${VALUE_DATE_NEAR}",
  "minimumRate": 2.85,
  "noticePeriod": "24H"
}
EOF
)"

post_order "ON_CALL USD 48H" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-OC-USD",
  "orderType": "ON_CALL",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-DEMO",
  "currency": "USD",
  "amount": 300000.00,
  "valueDate": "${VALUE_DATE_NEAR}",
  "minimumRate": 3.10,
  "noticePeriod": "48H"
}
EOF
)"

# Outside near-term window — enable "Show all value dates" on Received
post_order "TERM EUR 3M (far)" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-TERM-EUR-FAR",
  "orderType": "TERM",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-DEMO-FAR",
  "currency": "EUR",
  "amount": 2000000.00,
  "valueDate": "${VALUE_DATE_FAR}",
  "minimumRate": 3.50,
  "tenor": "3M",
  "desiredCounterpartyComment": "Far value date — use Show all"
}
EOF
)"

post_order "ON_CALL EUR 24H (far)" "$(cat <<EOF
{
  "externalOrderReference": "DEMO-${RUN_ID}-OC-EUR-FAR",
  "orderType": "ON_CALL",
  "orderOperation": "SUBSCRIPTION",
  "portfolioNumber": "PF-DEMO-FAR",
  "currency": "EUR",
  "amount": 400000.00,
  "valueDate": "${VALUE_DATE_FAR}",
  "minimumRate": 2.90,
  "noticePeriod": "24H",
  "desiredCounterpartyComment": "Far value date — use Show all"
}
EOF
)"

echo
echo "Done. Open http://localhost:4200 → Term / OnCall → Received."
echo "Toggle \"Show all value dates\" to see orders with external ref ...-FAR (value ${VALUE_DATE_FAR})."
