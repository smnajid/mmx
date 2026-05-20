#!/usr/bin/env bash
# scripts/register-schemas.sh — Idempotently register OrderExecutedV1 schema
# against the Confluent-compatible Redpanda Schema Registry.
#
# Usage: ./scripts/register-schemas.sh [registry_url]
# Default registry_url: http://localhost:18081

set -euo pipefail

REGISTRY_URL="${1:-${SCHEMA_REGISTRY_URL:-http://localhost:18081}}"
SUBJECT="mmx.order.executed-value"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCHEMA_FILE="$SCRIPT_DIR/../specs/002-trader-orders-views/contracts/schemas/OrderExecutedV1.json"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[schema-registry]${NC} $*"; }
warn() { echo -e "${YELLOW}[schema-registry]${NC} $*"; }
die()  { echo -e "${RED}[schema-registry] ERROR:${NC} $*" >&2; exit 1; }

if [ ! -f "$SCHEMA_FILE" ]; then
  die "Schema file not found at: $SCHEMA_FILE"
fi

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

# 1. Register the schema first (which guarantees the subject exists)
log "Registering JSON schema for subject: $SUBJECT..."
SCHEMA_CONTENT=$(jq -c '.' "$SCHEMA_FILE")
PAYLOAD=$(jq -n --arg schema "$SCHEMA_CONTENT" '{schemaType: "JSON", schema: $schema}')

RESPONSE=$(curl -sS -X POST "$REGISTRY_URL/subjects/$SUBJECT/versions" \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d "$PAYLOAD")

VERSION_ID=$(echo "$RESPONSE" | jq -r '.id // empty')

if [ -z "$VERSION_ID" ] || [ "$VERSION_ID" = "null" ]; then
  die "Schema registration failed. Response: $RESPONSE"
fi

log "Schema registered successfully with Version ID: $VERSION_ID"

# 2. Set compatibility level to BACKWARD on the subject
log "Configuring compatibility mode to BACKWARD for subject: $SUBJECT..."
CONF_RESPONSE=$(curl -sS -X PUT "$REGISTRY_URL/config/$SUBJECT" \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  -d '{"compatibility": "BACKWARD"}')

if echo "$CONF_RESPONSE" | jq -e '.compatibility' > /dev/null 2>&1; then
  log "Compatibility set successfully to: $(echo "$CONF_RESPONSE" | jq -r '.compatibility')"
else
  # Confluent standard says PUT /config/subject returns {"compatibility": "BACKWARD"}
  # If a registry version returns empty on success, let's verify via GET
  CHECK_COMPAT=$(curl -sS "$REGISTRY_URL/config/$SUBJECT" || true)
  log "Subject compatibility configured. Current config: $CHECK_COMPAT"
fi

log "Schema governance setup complete for $SUBJECT!"
