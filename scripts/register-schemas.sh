#!/usr/bin/env bash
# scripts/register-schemas.sh — Idempotently register AsyncAPI JSON schemas
# against the Confluent-compatible Redpanda Schema Registry.
#
# Usage: ./scripts/register-schemas.sh [registry_url]
# Default registry_url: http://localhost:18081

set -euo pipefail

REGISTRY_URL="${1:-${SCHEMA_REGISTRY_URL:-http://localhost:18081}}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMAS_DIR="$SCRIPT_DIR/../specs/002-trader-orders-views/contracts/schemas"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[schema-registry]${NC} $*"; }
warn() { echo -e "${YELLOW}[schema-registry]${NC} $*"; }
die()  { echo -e "${RED}[schema-registry] ERROR:${NC} $*" >&2; exit 1; }

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

# Ensure registry is reachable
log "Checking connection to Redpanda Schema Registry at $REGISTRY_URL..."
MAX_ATTEMPTS=15
ATTEMPT=1
until curl -sf "$REGISTRY_URL/subjects" > /dev/null 2>&1; do
  if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
    die "Schema Registry at $REGISTRY_URL is unreachable after $MAX_ATTEMPTS attempts."
  fi
  warn "Schema Registry is not ready yet. Retrying in 2s... ($ATTEMPT/$MAX_ATTEMPTS)"
  sleep 2
  ATTEMPT=$((ATTEMPT + 1))
done
log "Schema Registry is reachable."

register_schema "mmx.order.executed-value" "$SCHEMAS_DIR/OrderExecutedV1.json"
register_schema "mmx.oncall.rate.handoff-value" "$SCHEMAS_DIR/OnCallRateUpdatedV1.json"
register_schema "mmx.oncall.rate.canceled-value" "$SCHEMAS_DIR/OnCallRateCanceledV1.json"

log "Schema governance setup complete!"
