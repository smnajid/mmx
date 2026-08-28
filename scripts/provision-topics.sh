#!/usr/bin/env bash
# scripts/provision-topics.sh — Idempotently provision Kafka topics + ACLs used by MMX.
#
# Focus: the cross-org routed-order-outcome leg-B channel (task 10.5 of the
# cross-org-routing-transport change). The topic is LODH-owned and org-suffixed
# (mmx.routed-order-outcome.LODH); the client deployment (CGED) holds a consume-only ACL.
#
# The back-office channels (mmx.order.executed, mmx.oncall.rate.handoff) auto-create on the
# dev Redpanda container; this script does not re-create them. The cross-org channel is
# provisioned explicitly because it is a named, org-scoped, inter-deployment surface with an
# ACL contract.
#
# Usage: ./scripts/provision-topics.sh [bootstrap_servers]
# Default bootstrap_servers: localhost:19092 (docker-compose external port)
#
# NOTE on ACLs: the dev Redpanda container runs in dev-container mode without SASL/SSL, so
# ACLs are not enforced locally — this script still issues the rpk acl commands so the same
# script is the source of truth for production broker provisioning (where SASL/SSL + ACL
# authorizer are enabled). Adjust CGED_PRINCIPAL / HUB_PRINCIPAL to your deployment principals.

set -euo pipefail

BOOTSTRAP="${1:-${KAFKA_BOOTSTRAP:-localhost:19092}}"
BROKER_ARGS="--brokers ${BOOTSTRAP}"

# Hub (LODH) is the sole publisher; client (CGED) is consume-only.
HUB_TOPIC="mmx.routed-order-outcome.LODH"
HUB_PRINCIPAL="${HUB_PRINCIPAL:-User:LODH}"
CGED_PRINCIPAL="${CGED_PRINCIPAL:-User:CGED}"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'
log()  { echo -e "${GREEN}[topics]${NC} $*"; }
warn() { echo -e "${YELLOW}[topics]${NC} $*"; }

# Probe broker reachability (rpk cluster info). Auto-create would mask a dead broker; fail fast.
log "Checking Kafka broker reachability at ${BOOTSTRAP}..."
if ! rpk ${BROKER_ARGS} cluster info >/dev/null 2>&1; then
  warn "Broker not reachable at ${BOOTSTRAP} (rpk cluster info failed)."
  warn "Start the stack first: docker compose up -d"
  exit 1
fi

# rpk topic create is idempotent: it succeeds if the topic exists (prints a notice) or creates it.
log "Provisioning topic ${HUB_TOPIC} (LODH-owned, org-suffixed leg-B channel)..."
rpk ${BROKER_ARGS} topic create "${HUB_TOPIC}" --replicas 1 --partitions 1 \
  -c cleanup.policy=compact,delete \
  -c retention.ms=-1 || warn "Topic ${HUB_TOPIC} already exists or could not be created (continuing for ACL step)."

log "Granting hub publisher ACL on ${HUB_TOPIC} (${HUB_PRINCIPAL}: produce+describe)..."
rpk ${BROKER_ARGS} acl create \
  --allow-principal "${HUB_PRINCIPAL}" \
  --operation write \
  --operation describe \
  --topic "${HUB_TOPIC}" \
  2>/dev/null || warn "ACL grant for ${HUB_PRINCIPAL} skipped (authorizer not enabled in dev-container mode)."

log "Granting CGED consume-only ACL on ${HUB_TOPIC} (${CGED_PRINCIPAL}: read+describe)..."
rpk ${BROKER_ARGS} acl create \
  --allow-principal "${CGED_PRINCIPAL}" \
  --operation read \
  --operation describe \
  --topic "${HUB_TOPIC}" \
  --group "${CGED_PRINCIPAL}-consumer-group" \
  2>/dev/null || warn "ACL grant for ${CGED_PRINCIPAL} skipped (authorizer not enabled in dev-container mode)."

log "Cross-org topic + ACL provisioning complete."
log "Reminder: register the RoutingOutcomeV1 schema subject with ./scripts/register-schemas.sh"
