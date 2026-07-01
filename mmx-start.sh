#!/usr/bin/env bash
# mmx-start.sh — Start the full MMX development stack:
#   1. Docker Compose (Postgres + Redpanda + Redpanda Console)
#   2. Spring Boot backend  (mmx-bootstrap → PostgreSQL + Redpanda via application.yml)
#   3. Angular frontend     (ng serve)
#
# Usage: ./mmx-start.sh
# Stop:  Ctrl-C kills all three processes cleanly

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
FRONTEND_DIR="$SCRIPT_DIR/frontend"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log()  { echo -e "${GREEN}[mmx]${NC} $*"; }
warn() { echo -e "${YELLOW}[mmx]${NC} $*"; }
die()  { echo -e "${RED}[mmx] ERROR:${NC} $*" >&2; exit 1; }

# Cursor/IDE shells may prepend a bundled Node (< v20.19) ahead of nvm on PATH.
ensure_node() {
  export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
  [[ -s "$NVM_DIR/nvm.sh" ]] || die "nvm not found — install Node $(cat "$SCRIPT_DIR/.nvmrc") via https://github.com/nvm-sh/nvm"
  # shellcheck source=/dev/null
  source "$NVM_DIR/nvm.sh"
  (cd "$SCRIPT_DIR" && nvm install && nvm use) || die "Failed to activate Node version from .nvmrc"
  export PATH="$(dirname "$(nvm which current)"):$PATH"
  log "Using Node $(node -v)"
}

cleanup() {
  warn "Shutting down..."
  kill "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  wait "$BACKEND_PID" "$FRONTEND_PID" 2>/dev/null || true
  log "Backend and frontend stopped."
  log "Docker services left running — stop them with: docker compose down"
}

# ── 1. Docker Compose ──────────────────────────────────────────────────────────
log "Starting Docker services (Postgres, Redpanda)..."
docker compose -f "$SCRIPT_DIR/docker-compose.yml" up -d

log "Waiting for Postgres to be healthy..."
until docker inspect --format='{{.State.Health.Status}}' mmx-postgres 2>/dev/null | grep -q healthy; do
  sleep 2
done
log "Postgres is healthy."

log "Waiting for Redpanda and Schema Registry to be healthy..."
until docker inspect --format='{{.State.Health.Status}}' mmx-redpanda 2>/dev/null | grep -q healthy; do
  sleep 3
done
log "Redpanda and Schema Registry are healthy."

# ── 1.5. Schema Registry Registration ──────────────────────────────────────────
log "Registering schemas in Redpanda Schema Registry..."
"$SCRIPT_DIR/scripts/register-schemas.sh"

# ── 2. Spring Boot backend ─────────────────────────────────────────────────────
log "Starting Spring Boot backend..."
(
  cd "$BACKEND_DIR"
  mvn spring-boot:run -pl mmx-bootstrap 2>&1 \
    | sed 's/^/[backend] /'
) &
BACKEND_PID=$!

log "Waiting for backend to be ready on port 8080..."
until curl -sf http://localhost:8080/actuator/health > /dev/null 2>&1; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    die "Backend process exited unexpectedly. Check output above."
  fi
  sleep 3
done
log "Backend is ready."

# ── 3. Angular frontend ────────────────────────────────────────────────────────
ensure_node
log "Starting Angular frontend..."
(
  cd "$FRONTEND_DIR"
  npm start 2>&1 | sed 's/^/[frontend] /'
) &
FRONTEND_PID=$!

trap cleanup INT TERM

log ""
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "  MMX stack is up"
log "  Frontend   → http://localhost:4200"
log "  Backend    → http://localhost:8080"
log "  Redpanda   → http://localhost:8081  (console)"
log "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
log "  Press Ctrl-C to stop backend + frontend"
log ""

wait "$BACKEND_PID" "$FRONTEND_PID"
