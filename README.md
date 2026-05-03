# AgentMesh

Decentralised AI Agent Registry on ENS. Agents discover and delegate to each other via Ethereum Name Service text records — no hardcoded URLs.

## Architecture

```
agentmesh/
  core/            ENS resolution, manifest parsing, agent registry (no Spring)
  agent-runtime/   Spring Boot agent server — each instance is one AI agent
  app/             Thymeleaf web UI — registry explorer and agent invoker
  scripts/         CLI tools — register agents, run demos
```

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- A Groq API key (free at https://console.groq.com)

### 1. Configure

```bash
cp .env.example .env
# Edit .env and add your GROQ_API_KEY
```

### 2. Build

```bash
mvn clean install -DskipTests
```

### 3. Run

Open three terminals:

**Terminal 1 — Orchestrator (port 8081):**
```bash
./start-orchestrator.sh
```

**Terminal 2 — Researcher (port 8082):**
```bash
./start-researcher.sh
```

**Terminal 3 — Web UI (port 8080) OR Demo script:**
```bash
# Option A: Web UI
./start-app.sh
# Then open http://localhost:8080

# Option B: CLI demo
source .env && mvn exec:java -pl scripts -Dexec.mainClass=mesh.scripts.DemoInteraction
```

## API

Every agent exposes:

- `POST /invoke` — `{ "task": "...", "callerEns": "..." }`
- `GET /identity` — Agent manifest
- `GET /health` — Health check

## Environment Variables

| Variable | Required | Default | Description |
|---|---|---|---|
| GROQ_API_KEY | Yes | — | Groq API key for LLM |
| ENS_NAME | Yes | — | This agent's ENS name |
| SERVER_PORT | Yes | 8082 | Port to listen on |
| AGENT_PARENT_DOMAIN | No | agentmesh-dev.eth | Parent ENS domain |
| AGENT_CAPABILITIES | No | orchestrate,delegate | Capabilities |
| SEARCH_API_KEY | No | — | Serper key for web search |
| NAMESTONE_API_KEY | No | — | For ENS registration |
| AGENT_DEV_MODE | No | true | Local fallbacks when ENS fails |
