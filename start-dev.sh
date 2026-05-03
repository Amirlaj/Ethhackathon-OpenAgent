#!/usr/bin/env bash
# ─── AgentMesh Dev Startup ──────────────────────────────────────────────────
# Starts both agents + the web app in dev mode (no ENS registration needed).
#
# Usage:  ./start-dev.sh
# Stop:   Ctrl+C (kills all three processes)
# ─────────────────────────────────────────────────────────────────────────────

set -e

# Load .env
if [ -f .env ]; then
    export $(grep -v '^#' .env | grep -v '^\s*$' | xargs)
fi

# Force dev mode
export AGENT_DEV_MODE=true

echo "╔══════════════════════════════════════════════════╗"
echo "║         AgentMesh — Dev Mode Startup             ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# Build everything first
echo "→ Building all modules..."
mvn -q clean install -DskipTests
echo "✓ Build complete"
echo ""

# Trap Ctrl+C to kill all background processes
trap 'echo ""; echo "Shutting down..."; kill $(jobs -p) 2>/dev/null; exit 0' INT TERM

# Start orchestrator (port 8081)
echo "→ Starting orchestrator on :8081..."
ENS_NAME=orchestrator.${AGENT_PARENT_DOMAIN} \
SERVER_PORT=8081 \
AGENT_CAPABILITIES=orchestrate,delegate \
AGENT_DESCRIPTION="Orchestrator agent — coordinates tasks across the mesh" \
mvn -q spring-boot:run -pl agent-runtime &
ORCH_PID=$!

# Start researcher (port 8082)
echo "→ Starting researcher on :8082..."
ENS_NAME=researcher.${AGENT_PARENT_DOMAIN} \
SERVER_PORT=8082 \
AGENT_CAPABILITIES=web-search,summarise \
AGENT_DESCRIPTION="Researcher agent — web search and summarisation" \
mvn -q spring-boot:run -pl agent-runtime &
RES_PID=$!

# Wait for agents to be ready
echo "→ Waiting for agents to start..."
for i in $(seq 1 30); do
    sleep 2
    if curl -s http://localhost:8081/health > /dev/null 2>&1 && \
       curl -s http://localhost:8082/health > /dev/null 2>&1; then
        echo "✓ Both agents are ready"
        break
    fi
    if [ $i -eq 30 ]; then
        echo "✗ Agents did not start in time — check logs above"
        kill $ORCH_PID $RES_PID 2>/dev/null
        exit 1
    fi
done

# Start web app (port 8080)
echo "→ Starting web UI on :8080..."
APP_PORT=8080 \
mvn -q spring-boot:run -pl app &
APP_PID=$!

echo ""
echo "╔══════════════════════════════════════════════════╗"
echo "║  ✓ AgentMesh running in dev mode                 ║"
echo "║                                                  ║"
echo "║  Web UI:       http://localhost:8080              ║"
echo "║  Orchestrator: http://localhost:8081/health       ║"
echo "║  Researcher:   http://localhost:8082/health       ║"
echo "║                                                  ║"
echo "║  Press Ctrl+C to stop all services                ║"
echo "╚══════════════════════════════════════════════════╝"
echo ""

# Wait for all background processes
wait
