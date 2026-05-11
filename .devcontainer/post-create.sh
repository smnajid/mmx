#!/usr/bin/env bash
# One-time setup after the dev container is created (see devcontainer.json).
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo "Starting PostgreSQL (docker compose)..."
docker compose up -d postgres

echo "Waiting for PostgreSQL to accept connections..."
i=0
until docker compose exec -T postgres pg_isready -U mmx -d mmx >/dev/null 2>&1; do
  i=$((i + 1))
  if [ "$i" -ge 60 ]; then
    echo "Timed out waiting for PostgreSQL." >&2
    exit 1
  fi
  sleep 2
done
echo "PostgreSQL is ready."

echo "Installing frontend dependencies (npm ci)..."
(cd "$ROOT/frontend" && npm ci)

echo "Building backend modules (mvn install, tests skipped)..."
(cd "$ROOT/backend" && mvn -B -DskipTests install)

cat <<'EOF'

Post-create finished.

  Database:  jdbc:postgresql://localhost:5432/mmx (user/password: mmx/mmx)
  Backend:   cd backend && mvn spring-boot:run -pl mmx-bootstrap
  Frontend:  cd frontend && npm run start -- --proxy-config proxy.conf.json --host 0.0.0.0 --port 4200

Use --host 0.0.0.0 so GitHub Codespaces port forwarding can reach ng serve.

EOF
