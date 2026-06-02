#!/usr/bin/env bash
# Simulate back-office inbound confirmations against a running mmx instance.
#
#   Orders (EXECUTED → ACCOUNTED):
#     POST /api/v1/back-office/orders/{orderId}/accounted
#
#   OnCall rate segments (PENDING_CONFIRMATION → VALID):
#     POST /api/v1/back-office/oncall-rates/{segmentId}/confirmed
#
# Prerequisites: backend running (e.g. ./mmx-start.sh)
# Default URL: http://localhost:8080  (override with BASE_URL)
#
# Discovery lists use X-Trader-Id (trader API). Confirmation callbacks do not.
#
# Usage:
#   ./scripts/bo-confirm.sh                 # confirm all discoverable pending items
#   ./scripts/bo-confirm.sh --orders-only
#   ./scripts/bo-confirm.sh --rates-only
#   ./scripts/bo-confirm.sh --dry-run
#   ./scripts/bo-confirm.sh <order-uuid> ...           # explicit order IDs
#   ./scripts/bo-confirm.sh --rate <segment-uuid> ...  # explicit segment IDs

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
TRADER_ID="${TRADER_ID:-demo-trader}"
PAGE_SIZE="${PAGE_SIZE:-100}"

CONFIRM_ORDERS=1
CONFIRM_RATES=1
DRY_RUN=0
EXPLICIT_ORDER_IDS=()
EXPLICIT_SEGMENT_IDS=()

usage() {
  sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
  echo
  echo "Environment: BASE_URL TRADER_ID PAGE_SIZE"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --orders-only)
      CONFIRM_RATES=0
      shift
      ;;
    --rates-only)
      CONFIRM_ORDERS=0
      shift
      ;;
    --dry-run)
      DRY_RUN=1
      shift
      ;;
    --rate)
      shift
      [[ $# -gt 0 ]] || { echo "ERROR: --rate requires a segment UUID" >&2; exit 1; }
      EXPLICIT_SEGMENT_IDS+=("$1")
      shift
      ;;
    *)
      if [[ "$1" =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]]; then
        EXPLICIT_ORDER_IDS+=("$1")
        shift
      else
        echo "ERROR: unknown argument or invalid UUID: $1" >&2
        usage >&2
        exit 1
      fi
      ;;
  esac
done

# Explicit IDs narrow scope: do not auto-discover the other resource type.
if [[ ${#EXPLICIT_ORDER_IDS[@]} -gt 0 ]]; then
  CONFIRM_ORDERS=1
  if [[ ${#EXPLICIT_SEGMENT_IDS[@]} -eq 0 ]]; then
    CONFIRM_RATES=0
  fi
fi
if [[ ${#EXPLICIT_SEGMENT_IDS[@]} -gt 0 ]]; then
  CONFIRM_RATES=1
  if [[ ${#EXPLICIT_ORDER_IDS[@]} -eq 0 ]]; then
    CONFIRM_ORDERS=0
  fi
fi

trader_get() {
  local path="$1"
  curl -sS -f \
    -H "X-Trader-Id: ${TRADER_ID}" \
    "${BASE_URL}${path}"
}

bo_post() {
  local path="$1"
  local label="$2"
  if [[ "${DRY_RUN}" -eq 1 ]]; then
    echo "DRY  ${label} -> POST ${path}"
    return 0
  fi
  local code body
  body="$(mktemp)"
  code="$(curl -sS -o "${body}" -w "%{http_code}" \
    -X POST "${BASE_URL}${path}" \
    -H "Content-Type: application/json" \
    -d '{}')"
  if [[ "${code}" == "200" ]]; then
    echo "OK   ${label} (${code})"
    rm -f "${body}"
    return 0
  fi
  echo "FAIL ${label} -> HTTP ${code}" >&2
  if [[ -s "${body}" ]]; then
    cat "${body}" >&2
    echo >&2
  fi
  rm -f "${body}"
  return 1
}

confirm_order() {
  local order_id="$1"
  local ref="${2:-}"
  local label="order ${order_id}"
  if [[ -n "${ref}" ]]; then
    label="order ${ref} (${order_id})"
  fi
  bo_post "/api/v1/back-office/orders/${order_id}/accounted" "${label}"
}

confirm_rate_segment() {
  local segment_id="$1"
  local ctx="${2:-}"
  local label="segment ${segment_id}"
  if [[ -n "${ctx}" ]]; then
    label="segment ${segment_id} (${ctx})"
  fi
  bo_post "/api/v1/back-office/oncall-rates/${segment_id}/confirmed" "${label}"
}

discover_executed_order_ids() {
  local list_path page json count
  for list_path in "/api/v1/orders/term/executed" "/api/v1/orders/oncall/executed"; do
    page=0
    while true; do
      json="$(trader_get "${list_path}?page=${page}&size=${PAGE_SIZE}")" || return 1
      python3 - "${json}" <<'PY'
import json, sys
data = json.loads(sys.argv[1])
for row in data.get("content") or []:
    oid = row.get("orderId")
    ref = row.get("externalOrderReference") or ""
    if oid:
        print(f"{oid}\t{ref}")
PY
      count="$(python3 -c "import json,sys; d=json.loads(sys.argv[1]); print(len(d.get('content') or []))" "${json}")"
      if [[ "${count}" -lt "${PAGE_SIZE}" ]]; then
        break
      fi
      page=$((page + 1))
    done
  done
}

discover_pending_segment_ids() {
  local institutions_json
  institutions_json="$(trader_get "/api/v1/settings/institutions")" || return 1
  python3 - "${institutions_json}" "${BASE_URL}" "${TRADER_ID}" <<'PY'
import json, sys, urllib.request

institutions = json.loads(sys.argv[1])
base = sys.argv[2].rstrip("/")
trader = sys.argv[3]

for inst in institutions:
    code = inst.get("institutionCode")
    if not code:
        continue
    req = urllib.request.Request(
        f"{base}/api/v1/settings/institutions/{code}/oncall-rates",
        headers={"X-Trader-Id": trader},
    )
    try:
        with urllib.request.urlopen(req) as resp:
            segments = json.loads(resp.read().decode())
    except urllib.error.HTTPError as e:
        if e.code == 404:
            continue
        raise
    for seg in segments:
        if seg.get("status") == "PENDING_CONFIRMATION":
            sid = seg.get("segmentId")
            if sid:
                ctx = f"{code} {seg.get('currency','?')} {seg.get('noticePeriod','?')}"
                print(f"{sid}\t{ctx}")
PY
}

failures=0

echo "Back-office confirmations -> ${BASE_URL}"
[[ "${DRY_RUN}" -eq 1 ]] && echo "(dry run — no POSTs)"
echo

if [[ "${CONFIRM_ORDERS}" -eq 1 ]]; then
  if [[ ${#EXPLICIT_ORDER_IDS[@]} -gt 0 ]]; then
    echo "Orders (explicit IDs):"
    for id in "${EXPLICIT_ORDER_IDS[@]}"; do
      confirm_order "${id}" || failures=$((failures + 1))
    done
  else
    echo "Orders (discover EXECUTED term + oncall lists):"
    mapfile -t order_rows < <(discover_executed_order_ids || true)
    if [[ ${#order_rows[@]} -eq 0 ]]; then
      echo "  (none)"
    else
      for row in "${order_rows[@]}"; do
        IFS=$'\t' read -r oid ref <<<"${row}"
        confirm_order "${oid}" "${ref}" || failures=$((failures + 1))
      done
    fi
  fi
  echo
fi

if [[ "${CONFIRM_RATES}" -eq 1 ]]; then
  if [[ ${#EXPLICIT_SEGMENT_IDS[@]} -gt 0 ]]; then
    echo "OnCall rates (explicit segment IDs):"
    for id in "${EXPLICIT_SEGMENT_IDS[@]}"; do
      confirm_rate_segment "${id}" || failures=$((failures + 1))
    done
  else
    echo "OnCall rates (discover PENDING_CONFIRMATION per institution):"
    mapfile -t segment_rows < <(discover_pending_segment_ids || true)
    if [[ ${#segment_rows[@]} -eq 0 ]]; then
      echo "  (none)"
    else
      for row in "${segment_rows[@]}"; do
        IFS=$'\t' read -r sid ctx <<<"${row}"
        confirm_rate_segment "${sid}" "${ctx}" || failures=$((failures + 1))
      done
    fi
  fi
  echo
fi

if [[ "${failures}" -gt 0 ]]; then
  echo "Finished with ${failures} failure(s)." >&2
  exit 1
fi

echo "Done."
