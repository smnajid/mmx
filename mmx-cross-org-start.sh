#!/usr/bin/env bash
# mmx-cross-org-start.sh — Start TWO MMX deployments side by side for cross-org order-flow testing:
#
#   LODH  (TradingHub,    role=hub)    → http://localhost:8080  (DB mmx)
#   CGEG  (TradingClient, role=client) → http://localhost:8082  (DB mmx_cgeg)
#   Identity stub (external-identity stand-in) → http://localhost:8090
#
# Topology (contracts/007-cross-org-routing, openspec/specs/order-routing):
#   CGD@CGEG —Leg A (REST)→ LOC@LODH        CGD@CGEG ←Leg B (Kafka mmx.routed-order-outcome.LODH)—
#
# Usage:
#   ./mmx-cross-org-start.sh               # two backends + stub
#   ./mmx-cross-org-start.sh --frontend    # + two Angular serves (LODH :4200, CGEG :4201)
#   ./mmx-cross-org-start.sh --skip-build  # reuse previously built artifacts
#
# Then run the end-to-end check: ./scripts/cross-org-smoke.sh
# Stop: Ctrl-C kills backends, stub and frontends (Docker services stay up).
#
# The single-deployment LODH-only stack remains available via ./mmx-start.sh.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
FRONTEND_DIR="$SCRIPT_DIR/frontend"

WITH_FRONTEND=0
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --frontend)   WITH_FRONTEND=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    *) echo "Unknown option: $arg" >&2; exit 1 ;;
  esac
done

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[cross-org]${NC} $*"; }
warn() { echo -e "${YELLOW}[cross-org]${NC} $*"; }
die()  { echo -e "${RED}[cross-org] ERROR:${NC} $*" >&2; exit 1; }

LODH_URL="http://localhost:8080"
CGEG_URL="http://localhost:8082"

PIDS=()

cleanup() {
  warn "Shutting down cross-org stack..."
  for pid in "${PIDS[@]:-}"; do kill "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
  log "Deployments stopped. Docker services left running — stop with: docker compose down"
}
trap cleanup INT TERM

wait_healthy() {
  local url="$1" name="$2" pid="$3"
  until curl -sf "${url}/actuator/health" > /dev/null 2>&1; do
    if ! kill -0 "$pid" 2>/dev/null; then
      die "${name} process exited unexpectedly. Check output above."
    fi
    sleep 3
  done
}

# ── 1. Docker Compose (shared Postgres + Redpanda) ────────────────────────────
log "Starting Docker services (Postgres, Redpanda)..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

until docker inspect --format='{{.State.Health.Status}}' mmx-postgres 2>/dev/null | grep -q healthy; do sleep 2; done
log "Postgres is healthy."
until docker inspect --format='{{.State.Health.Status}}' mmx-redpanda 2>/dev/null | grep -q healthy; do sleep 3; done
log "Redpanda and Schema Registry are healthy."

# ── 1.5 Schemas, topics, CGEG database ────────────────────────────────────────
log "Registering schemas in Redpanda Schema Registry..."
"$SCRIPT_DIR/scripts/register-schemas.sh"

log "Provisioning cross-org Kafka topics + ACLs..."
if ! "$SCRIPT_DIR/scripts/provision-topics.sh"; then
  warn "provision-topics.sh failed (host rpk missing?) — creating topics via the broker container"
fi
# The CGEG deployment's org-suffixed back-office topic + the Leg-B topic: idempotent creates via the
# broker container's own rpk (works without a host rpk install; dev broker has no enforced ACLs).
docker exec mmx-redpanda rpk topic create mmx.routed-order-outcome.LODH --replicas 1 --partitions 1 > /dev/null 2>&1 \
  || log "Topic mmx.routed-order-outcome.LODH already exists."
docker exec mmx-redpanda rpk topic create mmx.order.executed.CGEG --replicas 1 --partitions 1 > /dev/null 2>&1 \
  || log "Topic mmx.order.executed.CGEG already exists."

log "Ensuring mmx_cgeg database exists (CGEG deployment)..."
if ! docker exec mmx-postgres psql -U mmx -d mmx -tAc "SELECT 1 FROM pg_database WHERE datname='mmx_cgeg'" | grep -q 1; then
  docker exec mmx-postgres createdb -U mmx -O mmx mmx_cgeg
  log "Created database mmx_cgeg."
else
  log "Database mmx_cgeg already exists."
fi

# ── 2. External identity stub (CGEG-side account resolution) ──────────────────
log "Starting external identity stub on :8090..."
python3 "$SCRIPT_DIR/scripts/identity-stub.py" 2>&1 | sed 's/^/[identity] /' &
PIDS+=($!)

# ── 3. Build once, then start both deployments ────────────────────────────────
if [[ "$SKIP_BUILD" -eq 0 ]]; then
  log "Installing backend modules (skip tests)..."
  # clean: wipe stale IDE (ECJ) auto-compiled .class files so javac recompiles consistently
  ( cd "$BACKEND_DIR" && mvn clean install -DskipTests -q ) || die "mvn install failed — check output above"
fi

log "Starting LODH deployment (role=hub) on :8080..."
(
  cd "$BACKEND_DIR"
  mvn spring-boot:run -pl mmx-bootstrap -Dspring-boot.run.profiles=lodh 2>&1 | sed 's/^/[lodh] /'
) &
PIDS+=($!)
wait_healthy "$LODH_URL" "LODH" "${PIDS[-1]}"
log "LODH deployment is ready."

log "Starting CGEG deployment (role=client) on :8082..."
(
  cd "$BACKEND_DIR"
  mvn spring-boot:run -pl mmx-bootstrap -Dspring-boot.run.profiles=cgeg 2>&1 | sed 's/^/[cgeg] /'
) &
PIDS+=($!)
wait_healthy "$CGEG_URL" "CGEG" "${PIDS[-1]}"
log "CGEG deployment is ready."

# ── 4. Optional frontends ─────────────────────────────────────────────────────
if [[ "$WITH_FRONTEND" -eq 1 ]]; then
  export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
  [[ -s "$NVM_DIR/nvm.sh" ]] || die "nvm not found — install Node $(cat "$SCRIPT_DIR/.nvmrc") via https://github.com/nvm-sh/nvm"
  # shellcheck source=/dev/null
  source "$NVM_DIR/nvm.sh"
  ( cd "$SCRIPT_DIR" && nvm install && nvm use ) || die "Failed to activate Node version from .nvmrc"
  export PATH="$(dirname "$(nvm which current)"):$PATH"

  log "Starting LODH frontend on :4200..."
  (
    cd "$FRONTEND_DIR"
    npm start -- --host 0.0.0.0 --allowed-hosts=all 2>&1 | sed 's/^/[lodh-ui] /'
  ) &
  PIDS+=($!)

  log "Starting CGEG frontend on :4201 (proxying to :8082)..."
  (
    cd "$FRONTEND_DIR"
    npm start -- --port 4201 --proxy-config proxy-cgeg.conf.json --host 0.0.0.0 --allowed-hosts=all 2>&1 | sed 's/^/[cgeg-ui] /'
  ) &
  PIDS+=($!)
fi

log ""
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "  MMX cross-org stack is up (CGD@CGEG → LOC@LODH)"
log "  LODH (hub)        → http://localhost:8080"
log "  CGEG (client)     → http://localhost:8082"
log "  Identity stub     → http://localhost:8090"
if [[ "$WITH_FRONTEND" -eq 1 ]]; then
  log "  LODH frontend     → http://localhost:4200"
  log "  CGEG frontend     → http://localhost:4201"
fi
log "  Redpanda console  → http://localhost:8081"
log ""
log "  End-to-end order-flow check:  ./scripts/cross-org-smoke.sh"
log "  Manual CGEG order intake:"
log "    POST http://localhost:8082/api/v1/orders  (legalEntityCode=CGD → routes to LODH)"
log "    Trader desk for the hub-side order:       http://localhost:8080 (LODH)"
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "  Press Ctrl-C to stop deployments + stub"
log ""

wait
