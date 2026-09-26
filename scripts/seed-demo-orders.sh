#!/usr/bin/env bash
# Seed mmx demo orders for the trader desk (test env).
#
# Does NOT touch Settings (currencies, institutions, rates). Configure those separately.
# By default wipes all order rows via Postgres before seeding (WIPE_ORDERS=1).
#
# Populates desk queues — Received (near + far), Assigned (two traders), Executed, OnCall INCREASE
#
# Prerequisites:
#   - Backend running (e.g. ./mmx-start.sh)
#   - Postgres reachable (default: docker container mmx-postgres)
#   - ≥1 active institution and onboarded currencies in Settings
#
# Use X-Trader-Id demo-trader in the SPA (or TRADER_ID below).
#
# Intake: valueDate must be ≥ today + 2 calendar days.
#
# Environment:
#   BASE_URL                   API base (default http://localhost:8080)
#   TRADER_ID                  Primary trader (default demo-trader)
#   TRADER_ID_OTHER            Second trader for Assigned visibility (default demo-trader-2)
#   ADVANCE_WORKFLOW           1 = assign/execute subset (default); 0 = intake only
#   WIPE_ORDERS                1 = DELETE all orders + audit/outbox first (default); 0 = append
#   POSTGRES_CONTAINER         Docker container for wipe (default mmx-postgres)
#   INSTITUTION_PRIMARY_CODE   Override; else first active institution from Settings API
#   INSTITUTION_SECONDARY_CODE Override; else second active institution (or primary again)
#
# After seeding:
#   ./scripts/bo-confirm.sh              # move Executed → Accounted (optional)
#   Open http://localhost:4200 → ON-CALL / Term → Received / Assigned / Executed

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TRADER_ID="${TRADER_ID:-demo-trader}"
TRADER_ID_OTHER="${TRADER_ID_OTHER:-demo-trader-2}"
ADVANCE_WORKFLOW="${ADVANCE_WORKFLOW:-1}"
WIPE_ORDERS="${WIPE_ORDERS:-1}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-mmx-postgres}"
RUN_ID="$(date +%s)"

VALUE_DATE_NEAR="$(python3 -c "from datetime import date, timedelta; print((date.today() + timedelta(days=2)).isoformat())")"
VALUE_DATE_FAR="$(python3 -c "from datetime import date, timedelta; print((date.today() + timedelta(days=30)).isoformat())")"

INSTITUTION_PRIMARY=""
INSTITUTION_SECONDARY=""
LAST_ORDER_ID=""
LAST_CONTRACT_NUMBER=""

json_field() {
  local json="$1"
  local field="$2"
  python3 -c "import json,sys; d=json.loads(sys.argv[1]); print(d.get(sys.argv[2]) or '')" "${json}" "${field}"
}

trader_curl() {
  local method="$1"
  local path="$2"
  local trader="${3:-${TRADER_ID}}"
  shift 3
  curl -sS -X "${method}" "${BASE_URL}${path}" \
    -H "X-Trader-Id: ${trader}" \
    "$@"
}

wipe_order_data() {
  if ! docker inspect "${POSTGRES_CONTAINER}" >/dev/null 2>&1; then
    echo "FAIL wipe: container ${POSTGRES_CONTAINER} not running" >&2
    exit 1
  fi
  docker exec "${POSTGRES_CONTAINER}" psql -U mmx -d mmx -v ON_ERROR_STOP=1 -c "
    DELETE FROM back_office_outbox;
    DELETE FROM order_audit_log;
    DELETE FROM money_market_order;
  " >/dev/null
  echo "OK   wiped orders (money_market_order, order_audit_log, back_office_outbox)"
}

resolve_institution_codes() {
  if [[ -n "${INSTITUTION_PRIMARY_CODE:-}" ]]; then
    INSTITUTION_PRIMARY="${INSTITUTION_PRIMARY_CODE}"
    INSTITUTION_SECONDARY="${INSTITUTION_SECONDARY_CODE:-${INSTITUTION_PRIMARY_CODE}}"
    return 0
  fi

  local json
  json="$(trader_curl GET "/api/v1/settings/institutions?active=true" "${TRADER_ID}")"
  read -r INSTITUTION_PRIMARY INSTITUTION_SECONDARY <<<"$(python3 -c "
import json, sys
rows = json.load(sys.stdin)
codes = sorted(r['institutionCode'] for r in rows if r.get('active'))
if not codes:
    sys.exit(1)
primary = codes[0]
secondary = codes[1] if len(codes) > 1 else codes[0]
print(primary, secondary)
" <<<"${json}")" || {
    echo "FAIL no active institutions in Settings — onboard at least one before seeding" >&2
    exit 1
  }
}

post_order() {
  local label="$1"
  local json="$2"
  local body_file
  body_file="$(mktemp)"
  local code
  code="$(curl -sS -o "${body_file}" -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/orders" \
    -H "Content-Type: application/json" \
    -d "${json}")"
  if [[ "${code}" != "201" && "${code}" != "200" ]]; then
    echo "FAIL ${label} -> HTTP ${code}" >&2
    if [[ -s "${body_file}" ]]; then
      cat "${body_file}" >&2
    fi
    rm -f "${body_file}"
    exit 1
  fi
  LAST_ORDER_ID="$(json_field "$(cat "${body_file}")" "orderId")"
  rm -f "${body_file}"
  echo "OK   ${label} (${code}) id=${LAST_ORDER_ID}"
}

assign_order() {
  local order_id="$1"
  local trader="${2:-${TRADER_ID}}"
  local label="${3:-assign ${order_id}}"
  local code
  code="$(trader_curl POST "/api/v1/orders/${order_id}/assign" "${trader}" -o /dev/null -w "%{http_code}")"
  if [[ "${code}" != "200" ]]; then
    echo "FAIL ${label} -> HTTP ${code}" >&2
    exit 1
  fi
  echo "OK   ${label} (${code})"
}

execute_order() {
  local order_id="$1"
  local executed_rate="$2"
  local trader="${3:-${TRADER_ID}}"
  local label="${4:-execute ${order_id}}"
  local body_file
  body_file="$(mktemp)"
  local code
  code="$(trader_curl POST "/api/v1/orders/${order_id}/execute" "${trader}" \
    -H "Content-Type: application/json" \
    -d "{\"executedRate\":${executed_rate}}" \
    -o "${body_file}" -w "%{http_code}")"
  if [[ "${code}" != "200" ]]; then
    echo "FAIL ${label} -> HTTP ${code}" >&2
    if [[ -s "${body_file}" ]]; then
      cat "${body_file}" >&2
    fi
    rm -f "${body_file}"
    exit 1
  fi
  LAST_CONTRACT_NUMBER="$(json_field "$(cat "${body_file}")" "generatedContractNumber")"
  rm -f "${body_file}"
  echo "OK   ${label} (${code}) contract=${LAST_CONTRACT_NUMBER:-—}"
}

order_json() {
  local external_ref="$1"
  local order_type="$2"
  local operation="$3"
  local portfolio="$4"
  local currency="$5"
  local amount="$6"
  local value_date="$7"
  local institution_code="$8"
  local minimum_rate="${9:-}"
  local tenor="${10:-}"
  local notice="${11:-}"
  local source_contract="${12:-}"

  python3 - "${external_ref}" "${order_type}" "${operation}" "${portfolio}" "${currency}" \
    "${amount}" "${value_date}" "${institution_code}" "${minimum_rate}" "${tenor}" "${notice}" \
    "${source_contract}" <<'PY'
import json, sys
(
    external_ref, order_type, operation, portfolio, currency,
    amount, value_date, institution_code, minimum_rate, tenor, notice,
    source_contract,
) = sys.argv[1:]

payload = {
    "externalOrderReference": external_ref,
    "orderType": order_type,
    "orderOperation": operation,
    "portfolioNumber": portfolio,
    "currency": currency,
    "amount": float(amount),
    "valueDate": value_date,
    "institutionCode": institution_code,
}
if minimum_rate:
    payload["minimumRate"] = float(minimum_rate)
if tenor:
    payload["tenor"] = tenor
if notice:
    payload["noticePeriod"] = notice
if source_contract:
    payload["sourceContractNumber"] = source_contract
print(json.dumps(payload))
PY
}

echo "=== mmx demo seed -> ${BASE_URL} (run=${RUN_ID}) ==="
echo "  trader=${TRADER_ID}  other=${TRADER_ID_OTHER}  advance=${ADVANCE_WORKFLOW}  wipe=${WIPE_ORDERS}"
echo "  near valueDate=${VALUE_DATE_NEAR}  far=${VALUE_DATE_FAR}"
echo

if [[ "${WIPE_ORDERS}" == "1" ]]; then
  echo "--- Wipe existing orders (test env) ---"
  wipe_order_data
  echo
fi

echo "--- Institutions (read from Settings; not modified) ---"
resolve_institution_codes
echo "  primary=${INSTITUTION_PRIMARY}  secondary=${INSTITUTION_SECONDARY}"
echo

echo "--- Orders: Received (near-term — default Received view) ---"
post_order "TERM EUR 3M received" "$(order_json \
  "DEMO-${RUN_ID}-TERM-EUR-RCV" "TERM" "SUBSCRIPTION" "PF-DEMO" "EUR" "1000000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_PRIMARY}" "3.45" "3M")"

post_order "ON_CALL EUR 24H received" "$(order_json \
  "DEMO-${RUN_ID}-OC-EUR-RCV" "ON_CALL" "SUBSCRIPTION" "PF-DEMO" "EUR" "750000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_PRIMARY}" "2.85" "" "24H")"

post_order "TERM USD 6M received" "$(order_json \
  "DEMO-${RUN_ID}-TERM-USD-RCV" "TERM" "SUBSCRIPTION" "PF-DEMO" "USD" "1000000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_SECONDARY}" "4.00" "6M")"

post_order "ON_CALL USD 48H received" "$(order_json \
  "DEMO-${RUN_ID}-OC-USD-RCV" "ON_CALL" "SUBSCRIPTION" "PF-DEMO" "USD" "1000000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_SECONDARY}" "3.10" "" "48H")"

echo
echo "--- Orders: Received (far — enable \"Show all value dates\") ---"
post_order "TERM EUR 3M far" "$(order_json \
  "DEMO-${RUN_ID}-TERM-EUR-FAR" "TERM" "SUBSCRIPTION" "PF-DEMO-FAR" "EUR" "2000000.00" \
  "${VALUE_DATE_FAR}" "${INSTITUTION_PRIMARY}" "3.50" "3M")"

post_order "ON_CALL EUR 24H far" "$(order_json \
  "DEMO-${RUN_ID}-OC-EUR-FAR" "ON_CALL" "SUBSCRIPTION" "PF-DEMO-FAR" "EUR" "400000.00" \
  "${VALUE_DATE_FAR}" "${INSTITUTION_PRIMARY}" "2.90" "" "24H")"

if [[ "${ADVANCE_WORKFLOW}" != "1" ]]; then
  echo
  echo "Done (intake only). Set ADVANCE_WORKFLOW=1 to populate Assigned and Executed queues."
  echo "Open http://localhost:4200 → ON-CALL (default) or Term → Received."
  exit 0
fi

echo
echo "--- Workflow: Assigned (ready to execute in UI) ---"
post_order "TERM EUR 1M assign-me" "$(order_json \
  "DEMO-${RUN_ID}-TERM-EUR-ASG" "TERM" "SUBSCRIPTION" "PF-DEMO" "EUR" "2500000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_PRIMARY}" "3.20" "1M")"
assign_order "${LAST_ORDER_ID}" "${TRADER_ID}" "assign TERM EUR 1M → ${TRADER_ID}"

post_order "ON_CALL EUR 48H assign-me" "$(order_json \
  "DEMO-${RUN_ID}-OC-EUR-ASG" "ON_CALL" "SUBSCRIPTION" "PF-DEMO" "EUR" "600000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_PRIMARY}" "2.80" "" "48H")"
assign_order "${LAST_ORDER_ID}" "${TRADER_ID}" "assign ON_CALL EUR 48H → ${TRADER_ID}"

echo
echo "--- Workflow: Assigned (desk-wide — other trader) ---"
post_order "TERM USD 3M assign-other" "$(order_json \
  "DEMO-${RUN_ID}-TERM-USD-OTH" "TERM" "SUBSCRIPTION" "PF-DEMO" "USD" "1000000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_SECONDARY}" "3.75" "3M")"
assign_order "${LAST_ORDER_ID}" "${TRADER_ID_OTHER}" "assign TERM USD 3M → ${TRADER_ID_OTHER}"

echo
echo "--- Workflow: Executed (not yet accounted) ---"
post_order "TERM EUR 3M executed" "$(order_json \
  "DEMO-${RUN_ID}-TERM-EUR-EXE" "TERM" "SUBSCRIPTION" "PF-DEMO" "EUR" "3200000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_PRIMARY}" "3.30" "3M")"
assign_order "${LAST_ORDER_ID}" "${TRADER_ID}" "assign TERM EUR 3M"
execute_order "${LAST_ORDER_ID}" "3.55" "${TRADER_ID}" "execute TERM EUR 3M"

post_order "ON_CALL EUR 24H executed" "$(order_json \
  "DEMO-${RUN_ID}-OC-EUR-EXE" "ON_CALL" "SUBSCRIPTION" "PF-DEMO" "EUR" "900000.00" \
  "${VALUE_DATE_NEAR}" "${INSTITUTION_PRIMARY}" "2.75" "" "24H")"
assign_order "${LAST_ORDER_ID}" "${TRADER_ID}" "assign ON_CALL EUR 24H"
execute_order "${LAST_ORDER_ID}" "2.90" "${TRADER_ID}" "execute ON_CALL EUR 24H"
LIVE_CONTRACT="${LAST_CONTRACT_NUMBER}"

if [[ -n "${LIVE_CONTRACT}" ]]; then
  echo
  echo "--- Workflow: OnCall INCREASE (lifecycle on contract ${LIVE_CONTRACT}) ---"
  post_order "ON_CALL EUR INCREASE" "$(order_json \
    "DEMO-${RUN_ID}-OC-EUR-INC" "ON_CALL" "INCREASE" "PF-DEMO" "EUR" "250000.00" \
    "${VALUE_DATE_NEAR}" "${INSTITUTION_PRIMARY}" "" "" "24H" "${LIVE_CONTRACT}")"
fi

echo
echo "=== Done ==="
cat <<EOF

Desk tour (http://localhost:4200, trader ${TRADER_ID}):

  ON-CALL → Received     near-term subscriptions (+ toggle Show all for FAR rows)
  ON-CALL → Assigned     EUR 48H ready to execute; USD row visible but owned by ${TRADER_ID_OTHER}
  ON-CALL → Executed     EUR 24H subscription awaiting accounting (counterparty visible)
  Term → Received / Assigned / Executed   mirror Term product rows

Institutions used (from Settings): ${INSTITUTION_PRIMARY}, ${INSTITUTION_SECONDARY}

Optional: ./scripts/bo-confirm.sh --orders-only   # EXECUTED → ACCOUNTED

EOF
