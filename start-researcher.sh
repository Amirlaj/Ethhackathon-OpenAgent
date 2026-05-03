#!/bin/bash
# Start the researcher agent on port 8082
set -a
source "$(dirname "$0")/.env.researcher"
set +a

export ENS_NAME="${ENS_NAME:-researcher.agentmesh-dev.eth}"
export SERVER_PORT="${SERVER_PORT:-8082}"
export AGENT_CAPABILITIES="${AGENT_CAPABILITIES:-web-search,summarise}"

echo "Starting researcher: $ENS_NAME on port $SERVER_PORT"
mvn spring-boot:run -pl agent-runtime
