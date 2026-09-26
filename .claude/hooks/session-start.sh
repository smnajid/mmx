#!/bin/bash
# SessionStart hook for Claude Code on the web: provisions JDK 25, Docker
# (for Testcontainers), and pre-fetches Maven/npm dependencies.
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

ROOT="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
JAVA25_HOME=/usr/lib/jvm/java-25-openjdk-amd64

# --- JDK 25 (backend/pom.xml requires <java.version>25) ---
if [ ! -x "$JAVA25_HOME/bin/javac" ]; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get install -y -qq openjdk-25-jdk-headless >/dev/null 2>&1 \
    || { apt-get update -qq >/dev/null && apt-get install -y -qq openjdk-25-jdk-headless >/dev/null; }
fi
export JAVA_HOME="$JAVA25_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  {
    echo "export JAVA_HOME=$JAVA25_HOME"
    echo "export PATH=$JAVA25_HOME/bin:\$PATH"
  } >> "$CLAUDE_ENV_FILE"
fi

# --- Docker daemon (Testcontainers: postgres, kafka) ---
if command -v dockerd >/dev/null && ! docker info >/dev/null 2>&1; then
  (nohup dockerd >/tmp/dockerd.log 2>&1 &)
  for _ in $(seq 1 30); do docker info >/dev/null 2>&1 && break; sleep 1; done
fi
if docker info >/dev/null 2>&1; then
  docker pull -q postgres:16-alpine >/dev/null 2>&1 || true
  docker pull -q apache/kafka-native:3.8.1 >/dev/null 2>&1 || true
fi

# --- Frontend deps ---
# The Cypress binary comes from download.cypress.io, which the environment's
# network policy may block; install npm deps without it, then try it best-effort.
(cd "$ROOT/frontend" && CYPRESS_INSTALL_BINARY=0 npm install --no-audit --no-fund --loglevel=error)
(cd "$ROOT/frontend" && timeout 120 npx --no-install cypress install >/dev/null 2>&1) \
  || echo "session-start: Cypress binary unavailable (allow download.cypress.io to enable e2e)" >&2

# --- Backend deps: compile + resolve test/plugin deps without running tests ---
(cd "$ROOT/backend" && mvn -q -B -DskipTests install)
