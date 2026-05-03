#!/bin/bash
# Start the web UI on port 8080
set -a
source "$(dirname "$0")/.env"
set +a

export AGENT_DEV_MODE=true
export SERVER_PORT=8080
echo "Starting AgentMesh web UI on port 8080"
mvn spring-boot:run -pl app
