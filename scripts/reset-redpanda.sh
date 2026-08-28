#!/usr/bin/env bash
# Reset Redpanda to a clean state (dev only — wipes all topics and schemas).
# Use when schema registry is stuck unhealthy (e.g. after disk-full log eviction).
set -euo pipefail
cd "$(dirname "$0")/.."

docker compose down
docker volume rm -f mmx_redpanda-data
docker compose up -d

echo "Waiting for redpanda to become healthy..."
for i in $(seq 1 30); do
  status=$(docker inspect mmx-redpanda --format '{{.State.Health.Status}}' 2>/dev/null || echo "missing")
  [ "$status" = "healthy" ] && echo "Redpanda healthy." && exit 0
  sleep 2
done
echo "ERROR: redpanda did not become healthy in 60s" >&2
exit 1
