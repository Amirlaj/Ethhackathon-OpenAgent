#!/bin/bash
# Start the orchestrator agent on port 8081
set -a
source "$(dirname "$0")/.env"
set +a

export ENS_NAME="${ENS_NAME:-orchestrator.agentmesh-dev.eth}"
export SERVER_PORT="${SERVER_PORT:-8081}"
export AGENT_CAPABILITIES="${AGENT_CAPABILITIES:-orchestrate,delegate}"

echo "Starting orchestrator: $ENS_NAME on port $SERVER_PORT"
mvn spring-boot:run -pl agent-runtime
