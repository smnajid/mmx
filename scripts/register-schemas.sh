#!/usr/bin/env bash
# scripts/register-schemas.sh — Idempotently register AsyncAPI JSON schemas
# against the Confluent-compatible Redpanda Schema Registry.
#
# Usage: ./scripts/register-schemas.sh [registry_url]
# Default registry_url: http://localhost:18081

set -euo pipefail

REGISTRY_URL="${1:-${SCHEMA_REGISTRY_URL:-http://localhost:18081}}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMAS_DIR="$SCRIPT_DIR/../contracts/002-trader-orders-views/schemas"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[schema-registry]${NC} $*"; }
warn() { echo -e "${YELLOW}[schema-registry]${NC} $*"; }
die()  { echo -e "${RED}[schema-registry] ERROR:${NC} $*" >&2; exit 1; }

LAST_SR_HTTP_CODE=""
LAST_SR_BODY=""

schema_registry_probe() {
  local response http_code
  if ! response=$(curl -sS -w '\n%{http_code}' "$REGISTRY_URL/subjects" 2>&1); then
    LAST_SR_HTTP_CODE=""
    LAST_SR_BODY="$response"
    return 1
  fi

  http_code=$(echo "$response" | tail -n1)
  LAST_SR_HTTP_CODE="$http_code"
  LAST_SR_BODY=$(echo "$response" | sed '$d')

  [ "$http_code" -ge 200 ] && [ "$http_code" -lt 300 ]
}

describe_schema_registry_failure() {
  local error_code message
  error_code=$(echo "$LAST_SR_BODY" | jq -r '.error_code // empty' 2>/dev/null || true)
  message=$(echo "$LAST_SR_BODY" | jq -r '.message // empty' 2>/dev/null || true)

  if [ -n "$LAST_SR_HTTP_CODE" ]; then
    warn "Last response: HTTP $LAST_SR_HTTP_CODE ${LAST_SR_BODY:-"(empty body)"}"
  elif [ -n "$LAST_SR_BODY" ]; then
    warn "Last response: $LAST_SR_BODY"
  fi

  if [ "$error_code" = "40002" ] || [[ "$message" == *"Fetch returned with error"* ]]; then
    warn "Schema Registry cannot read its internal _schemas topic (often offset_out_of_range)."
    warn "The _schemas topic must use cleanup.policy=compact; delete retention can trim required records."
    warn ""
    warn "Local dev recovery:"
    warn "  docker compose down"
    warn "  docker volume rm mmx_redpanda-data"
    warn "  docker compose up -d"
    warn ""
    warn "Production: verify _schemas has cleanup.policy=compact at cluster provision time,"
    warn "monitor GET /subjects in readiness checks, and escalate to Redpanda support — do not wipe volumes."
  fi
}

register_schema() {
  local subject="$1"
  local schema_file="$2"

  if [ ! -f "$schema_file" ]; then
    die "Schema file not found at: $schema_file"
  fi

  log "Registering JSON schema for subject: $subject..."
  local schema_content payload response version_id
  schema_content=$(jq -c '.' "$schema_file")
  payload=$(jq -n --arg schema "$schema_content" '{schemaType: "JSON", schema: $schema}')

  response=$(curl -sS -X POST "$REGISTRY_URL/subjects/$subject/versions" \
    -H "Content-Type: application/vnd.schemaregistry.v1+json" \
    -d "$payload")

  version_id=$(echo "$response" | jq -r '.id // empty')

  if [ -z "$version_id" ] || [ "$version_id" = "null" ]; then
    die "Schema registration failed for $subject. Response: $response"
  fi

  log "Schema registered successfully with Version ID: $version_id"

  log "Configuring compatibility mode to BACKWARD for subject: $subject..."
  local conf_response
  conf_response=$(curl -sS -X PUT "$REGISTRY_URL/config/$subject" \
    -H "Content-Type: application/vnd.schemaregistry.v1+json" \
    -d '{"compatibility": "BACKWARD"}')

  if echo "$conf_response" | jq -e '.compatibility' > /dev/null 2>&1; then
    log "Compatibility set successfully to: $(echo "$conf_response" | jq -r '.compatibility')"
  else
    local check_compat
    check_compat=$(curl -sS "$REGISTRY_URL/config/$subject" || true)
    log "Subject compatibility configured. Current config: $check_compat"
  fi
}

# Ensure registry is reachable and serving 2xx on /subjects
log "Checking connection to Redpanda Schema Registry at $REGISTRY_URL..."
MAX_ATTEMPTS=15
ATTEMPT=1
until schema_registry_probe; do
  if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
    describe_schema_registry_failure >&2
    die "Schema Registry at $REGISTRY_URL is not ready after $MAX_ATTEMPTS attempts."
  fi
  if [ -n "$LAST_SR_HTTP_CODE" ] && [ "$LAST_SR_HTTP_CODE" != "000" ]; then
    warn "Schema Registry returned HTTP $LAST_SR_HTTP_CODE (expected 2xx). Retrying in 2s... ($ATTEMPT/$MAX_ATTEMPTS)"
  else
    warn "Schema Registry is not ready yet. Retrying in 2s... ($ATTEMPT/$MAX_ATTEMPTS)"
  fi
  sleep 2
  ATTEMPT=$((ATTEMPT + 1))
done
log "Schema Registry is reachable."

register_schema "mmx.order.executed-value" "$SCHEMAS_DIR/OrderExecutedV1.json"
register_schema "mmx.oncall.rate.handoff-value" "$SCHEMAS_DIR/OnCallRateUpdatedV1.json"
register_schema "mmx.oncall.rate.canceled-value" "$SCHEMAS_DIR/OnCallRateCanceledV1.json"

log "Schema governance setup complete!"
