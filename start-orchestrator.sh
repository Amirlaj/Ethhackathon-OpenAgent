#!/bin/bash
# Start the orchestrator agent on port 8081
cd "$(dirname "$0")"

if [ -f .env ]; then
    set -a; source .env; set +a
else
    echo "Warning: .env not found — using defaults"
fi

export ENS_NAME="${ENS_NAME:-orchestrator.agentmesh-dev.eth}"
export SERVER_PORT="${SERVER_PORT:-8081}"
export AGENT_CAPABILITIES="${AGENT_CAPABILITIES:-orchestrate,delegate}"

echo "Starting orchestrator: $ENS_NAME on port $SERVER_PORT"
mvn spring-boot:run -pl agent-runtime
