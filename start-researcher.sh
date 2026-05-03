#!/bin/bash
# Start the researcher agent on port 8082
cd "$(dirname "$0")"

if [ -f .env.researcher ]; then
    set -a; source .env.researcher; set +a
else
    echo "Warning: .env.researcher not found — using defaults"
fi

export ENS_NAME="${ENS_NAME:-researcher.agentmesh-dev.eth}"
export SERVER_PORT="${SERVER_PORT:-8082}"
export AGENT_CAPABILITIES="${AGENT_CAPABILITIES:-web-search,summarise}"

echo "Starting researcher: $ENS_NAME on port $SERVER_PORT"
mvn spring-boot:run -pl agent-runtime
