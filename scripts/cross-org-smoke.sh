#!/usr/bin/env bash
# cross-org-smoke.sh — End-to-end order-flow check over the local cross-org topology:
#
#   CGD@CGEG (http://localhost:8082, role=client)  →  LOC@LODH (http://localhost:8080, role=hub)
#
# Exercises, in order:
#   1. hub reference data (currency, institution, delegated grant for CGD) on LODH
#   2. thin-client live reference read: CGEG ClientRepresentative sees the LODH grant remotely
#   3. PM intake on CGEG  → Leg A accept → client-side order ROUTED, hub-side order on LODH desk
#   4. hub trader assigns + executes the hub-side order
#   5. Leg B: CGEG client-side order converges to EXECUTED (RoutingOutcomeV1 via Kafka outbox)
#
# Prerequisites: ./mmx-cross-org-start.sh (or equivalent) running — both deployments healthy,
# identity stub on :8090, Redpanda up. Safe to re-run (idempotent reference data, unique refs).
#
# Environment:
#   LODH_URL   (default http://localhost:8080)
#   CGEG_URL   (default http://localhost:8082)
#   USER_ID    (default demo-trader)

set -euo pipefail

LODH_URL="${LODH_URL:-http://localhost:8080}"
CGEG_URL="${CGEG_URL:-http://localhost:8082}"
USER_ID="${USER_ID:-demo-trader}"
RUN_ID="$(date +%s)"

CURRENCY="EUR"
INSTITUTION_CODE="${INSTITUTION_CODE:-}"   # resolved from the hub's active institutions when unset
TENOR="3M"
CLIENT_PORTFOLIO="CGD-PM-${RUN_ID}"
EXTERNAL_REF="CGEG-SMOKE-${RUN_ID}"
EXECUTED_RATE="3.55"

GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'
ok()   { echo -e "${GREEN}[smoke]${NC} $*"; }
fail() { echo -e "${RED}[smoke] FAIL:${NC} $*" >&2; exit 1; }

json() { python3 -c "import json,sys; d=json.load(sys.stdin); print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

value_date="$(python3 -c "from datetime import date, timedelta; print((date.today() + timedelta(days=2)).isoformat())")"

# ── 0. Health ─────────────────────────────────────────────────────────────────
curl -sf "${LODH_URL}/actuator/health" > /dev/null || fail "LODH not reachable at ${LODH_URL}"
curl -sf "${CGEG_URL}/actuator/health" > /dev/null || fail "CGEG not reachable at ${CGEG_URL}"
ok "both deployments healthy"

# Pin the hub session scope explicitly (demo-trader holds several scopes; the implicit default
# is resolved-order dependent — e.g. CGD sorts before LOC once V26 seeds the client scope).
curl -sf -X POST "${LODH_URL}/api/v1/session/scope" \
  -H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" \
  -d '{"legalEntityCode":"LOC","role":"TRADER"}' > /dev/null \
  || fail "could not re-scope ${USER_ID} to LOC/TRADER on LODH"

# ── 1. Hub reference data on LODH (idempotent) ────────────────────────────────
if curl -sf -H "X-User-Id: ${USER_ID}" "${LODH_URL}/api/v1/settings/currencies" | grep -q "\"${CURRENCY}\""; then
  ok "currency ${CURRENCY} already managed on LODH"
else
  curl -sf -X POST "${LODH_URL}/api/v1/settings/currencies" \
    -H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" \
    -d "{\"code\":\"${CURRENCY}\",\"minSubscriptionAmount\":100000,\"minIncreaseDecreaseAmount\":10000,\"enabledTenors\":[\"1W\",\"1M\",\"3M\",\"6M\"],\"enabledNoticePeriods\":[\"24H\",\"48H\"]}" \
    > /dev/null || fail "could not onboard currency ${CURRENCY} on LODH"
  ok "onboarded currency ${CURRENCY} on LODH"
fi

# Ensure the tenor we route with is enabled on the hub's managed currency.
CUR_TENORS="$(curl -sf -H "X-User-Id: ${USER_ID}" "${LODH_URL}/api/v1/settings/currencies/${CURRENCY}" \
  | python3 -c "import json,sys; print(' '.join(json.load(sys.stdin).get('enabledTenors') or []))")"
if [[ " ${CUR_TENORS} " != *" ${TENOR} "* ]]; then
  PATCH_FILE="$(mktemp)"
  curl -sf -X PATCH "${LODH_URL}/api/v1/settings/currencies/${CURRENCY}" \
    -H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" \
    -d "{\"enabledTenors\":[\"1W\",\"1M\",\"3M\",\"6M\"]}" \
    > "${PATCH_FILE}" || fail "could not enable ${TENOR} on managed currency ${CURRENCY}: $(cat "${PATCH_FILE}")"
  ok "enabled tenor ${TENOR} on managed currency ${CURRENCY}"
fi

# Resolve an active hub institution (the flow does not depend on onboarding one).
if [[ -z "${INSTITUTION_CODE}" ]]; then
  INST_JSON="$(curl -sf -H "X-User-Id: ${USER_ID}" "${LODH_URL}/api/v1/settings/institutions?active=true")"
  INSTITUTION_CODE="$(python3 -c "
import json, sys
rows = json.loads(sys.argv[1])
codes = sorted(r['institutionCode'] for r in rows if r.get('active'))
print(codes[0] if codes else '')
" "${INST_JSON}")"
  if [[ -z "${INSTITUTION_CODE}" ]]; then
    NEW_INST="$(mktemp)"
    curl -sf -X POST "${LODH_URL}/api/v1/settings/institutions" \
      -H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" \
      -d '{"displayName":"BNP Paribas"}' > "${NEW_INST}" \
      || fail "no active institution on LODH and native onboarding failed: $(cat "${NEW_INST}")"
    INSTITUTION_CODE="$(python3 -c "import json,sys; print(json.load(sys.stdin).get('institutionCode',''))" < "${NEW_INST}")"
  fi
fi
[[ -n "${INSTITUTION_CODE}" ]] || fail "could not resolve an active institution on LODH"
ok "hub institution for the flow: ${INSTITUTION_CODE}"

if curl -sf -H "X-User-Id: ${USER_ID}" "${LODH_URL}/api/v1/settings/delegated-grants" | grep -q "\"clientLegalEntityCode\":\"CGD\""; then
  ok "delegated grant ${INSTITUTION_CODE}→CGD/${CURRENCY} already active on LODH"
else
  GRANT_FILE="$(mktemp)"
  curl -sf -X POST "${LODH_URL}/api/v1/settings/delegated-grants" \
    -H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" \
    -d "{\"hubInstitutionCode\":\"${INSTITUTION_CODE}\",\"clientLegalEntityCode\":\"CGD\",\"currency\":\"${CURRENCY}\",\"enabledTenors\":[\"${TENOR}\"],\"enabledNoticePeriods\":[]}" \
    > "${GRANT_FILE}" || fail "could not create delegated grant on LODH: $(cat "${GRANT_FILE}")"
  ok "created delegated grant ${INSTITUTION_CODE}→CGD/${CURRENCY} (tenors: ${TENOR}) on LODH"
fi

# ── 2. Thin-client remote reference read on CGEG ──────────────────────────────
curl -sf -X POST "${CGEG_URL}/api/v1/session/scope" \
  -H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" \
  -d '{"legalEntityCode":"CGD","role":"CLIENT_REPRESENTATIVE"}' > /dev/null \
  || fail "could not re-scope ${USER_ID} to CGD/CLIENT_REPRESENTATIVE on CGEG"
GRANTS="$(curl -sf -H "X-User-Id: ${USER_ID}" "${CGEG_URL}/api/v1/settings/delegated-grants/client")"
echo "${GRANTS}" | grep -q "\"${INSTITUTION_CODE}\"" \
  || fail "CGEG remote grant read returned no ${INSTITUTION_CODE} grant: ${GRANTS}"
ok "CGEG (thin client) reads the LODH grant live — BNP via LOC visible"

# ── 3. PM intake on CGEG → Leg A ─────────────────────────────────────────────
INTAKE_BODY_FILE="$(mktemp)"
HTTP_CODE="$(curl -sS -o "${INTAKE_BODY_FILE}" -w "%{http_code}" -X POST "${CGEG_URL}/api/v1/orders" \
  -H "Content-Type: application/json" \
  -d "{
        \"externalOrderReference\":\"${EXTERNAL_REF}\",
        \"legalEntityCode\":\"CGD\",
        \"orderType\":\"TERM\",
        \"orderOperation\":\"SUBSCRIPTION\",
        \"portfolioNumber\":\"${CLIENT_PORTFOLIO}\",
        \"currency\":\"${CURRENCY}\",
        \"amount\":1000000,
        \"valueDate\":\"${value_date}\",
        \"institutionCode\":\"${INSTITUTION_CODE}\",
        \"tenor\":\"${TENOR}\"
      }")"
[[ "${HTTP_CODE}" == "201" || "${HTTP_CODE}" == "200" ]] || fail "CGEG intake → HTTP ${HTTP_CODE}: $(cat "${INTAKE_BODY_FILE}")"
CLIENT_ORDER_ID="$(json "d['orderId']" < "${INTAKE_BODY_FILE}")"
CLIENT_STATUS="$(json "d['status']" < "${INTAKE_BODY_FILE}")"
[[ "${CLIENT_STATUS}" == "ROUTED" ]] || fail "expected client-side ROUTED after Leg A accept, got ${CLIENT_STATUS}: $(cat "${INTAKE_BODY_FILE}")"
ok "CGEG intake ${EXTERNAL_REF} → client-side order ${CLIENT_ORDER_ID} is ROUTED (Leg A accepted by LODH)"

# ── 4. Hub-side order on the LODH desk ───────────────────────────────────────
DESK_FILE="$(mktemp)"
curl -sf -H "X-User-Id: ${USER_ID}" "${LODH_URL}/api/v1/orders/term/received" > "${DESK_FILE}"
HUB_ORDER_ID="$(python3 - "${DESK_FILE}" "${EXTERNAL_REF}" <<'PY'
import json, sys
rows = json.load(open(sys.argv[1]))
items = rows.get("items") or rows.get("content") or rows
match = [r for r in items if r.get("originatingExternalOrderReference") == sys.argv[1]]
match = match or [r for r in items if r.get("portfolioNumber", "").startswith("CGD-LOC")]
print(match[0]["orderId"] if match else "")
PY
)"
[[ -n "${HUB_ORDER_ID}" ]] || fail "no hub-side order found on LODH desk for ${EXTERNAL_REF}: $(head -c 400 "${DESK_FILE}")"
ok "hub-side order ${HUB_ORDER_ID} on LODH desk (Received)"

curl -sf -X POST "${LODH_URL}/api/v1/orders/${HUB_ORDER_ID}/assign" -H "X-User-Id: ${USER_ID}" > /dev/null \
  || fail "assign failed on LODH for ${HUB_ORDER_ID}"
EXEC_FILE="$(mktemp)"
HTTP_CODE="$(curl -sS -o "${EXEC_FILE}" -w "%{http_code}" -X POST "${LODH_URL}/api/v1/orders/${HUB_ORDER_ID}/execute" \
  -H "Content-Type: application/json" -H "X-User-Id: ${USER_ID}" \
  -d "{\"executedRate\":${EXECUTED_RATE}}")"
[[ "${HTTP_CODE}" == "200" ]] || fail "execute failed on LODH (HTTP ${HTTP_CODE}): $(cat "${EXEC_FILE}")"
ok "LODH trader assigned + executed hub-side order ${HUB_ORDER_ID} (rate ${EXECUTED_RATE}%)"

# ── 5. Leg B: CGEG client-side converges to EXECUTED ─────────────────────────
echo -n "[smoke] polling CGEG for Leg-B EXECUTED outcome"
for _ in $(seq 1 20); do
  DETAIL_FILE="$(mktemp)"
  curl -sf -H "X-User-Id: ${USER_ID}" "${CGEG_URL}/api/v1/orders/${CLIENT_ORDER_ID}" > "${DETAIL_FILE}" || { sleep 2; continue; }
  STATUS="$(json "d['status']" < "${DETAIL_FILE}")"
  if [[ "${STATUS}" == "EXECUTED" ]]; then
    echo ""
    ok "client-side order ${CLIENT_ORDER_ID} is EXECUTED on CGEG (Leg B outcome applied)"
    ok "order flow CGD@CGEG → LOC@LODH verified end-to-end"
    exit 0
  fi
  echo -n "."
  sleep 2
done
echo ""
fail "client-side order ${CLIENT_ORDER_ID} did not reach EXECUTED within 40s — check Leg-B topic mmx.routed-order-outcome.LODH"
