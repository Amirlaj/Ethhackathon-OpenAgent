#!/bin/bash
# Start the web UI on port 8080
cd "$(dirname "$0")"

if [ -f .env ]; then
    set -a; source .env; set +a
fi

export AGENT_DEV_MODE=true
export SERVER_PORT=8080
echo "Starting AgentMesh web UI on port 8080"
mvn spring-boot:run -pl app
