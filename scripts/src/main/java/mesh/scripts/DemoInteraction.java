package mesh.scripts;

import com.fasterxml.jackson.databind.ObjectMapper;
import mesh.core.ens.AgentEnsResolver;
import mesh.core.ens.AgentRegistry;
import mesh.core.manifest.AgentManifest;
import okhttp3.*;

import java.io.IOException;
import java.util.List;

/**
 * Demo: Two agents coordinating via ENS.
 *
 * This is the judge-facing showpiece. Run it with two agents already started:
 *
 *   Terminal 1:
 *     ENS_NAME=orchestrator.agentmesh.eth \
 *     ANTHROPIC_API_KEY=sk-ant-... \
 *     SERVER_PORT=8081 \
 *     mvn spring-boot:run -pl agent-runtime
 *
 *   Terminal 2:
 *     ENS_NAME=researcher.agentmesh.eth \
 *     ANTHROPIC_API_KEY=sk-ant-... \
 *     SERVER_PORT=8082 \
 *     mvn spring-boot:run -pl agent-runtime
 *
 *   Terminal 3 (this script):
 *     mvn exec:java -pl scripts -Dexec.mainClass=mesh.scripts.DemoInteraction
 *
 * What you'll see:
 *   [ENS] Resolving orchestrator.agentmesh.eth → 0x...
 *   [ENS] Manifest: capabilities=[orchestrate], endpoint=http://localhost:8081
 *   [Demo] Sending task to orchestrator...
 *   [orchestrator.agentmesh.eth] Delegating 'web-search' via ENS...
 *   [ENS] Resolving researcher.agentmesh.eth → 0x...
 *   [ENS] Resolved → researcher.agentmesh.eth at http://localhost:8082
 *   [researcher.agentmesh.eth] Running web search...
 *   [orchestrator.agentmesh.eth] Synthesising results...
 *   [Demo] Final answer: ...
 */
public class DemoInteraction {

    private static final String ORCHESTRATOR_ENS = "orchestrator.agentmesh.eth";
    private static final String DEMO_TASK =
            "Research the latest advances in quantum computing from 2024 and write a concise summary.";

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║         AgentMesh Demo — ENS Agent Coordination      ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        AgentEnsResolver resolver = new AgentEnsResolver();
        AgentRegistry registry = new AgentRegistry(resolver, "agentmesh.eth");

        // Step 1: Show all registered agents
        System.out.println("=== Step 1: Discovering registered agents via ENS ===");
        List<AgentManifest> agents = registry.listAll();
        System.out.println("Found " + agents.size() + " registered agents:");
        for (AgentManifest agent : agents) {
            System.out.println("  • " + agent.ensName()
                    + " [" + String.join(", ", agent.capabilities()) + "]"
                    + " → " + agent.endpoint());
        }
        System.out.println();

        // Step 2: Resolve orchestrator identity
        System.out.println("=== Step 2: Resolving orchestrator identity from ENS ===");
        AgentManifest orchestrator = resolver.getManifest(ORCHESTRATOR_ENS);
        System.out.println("Orchestrator resolved:");
        System.out.println("  ENS name:  " + orchestrator.ensName());
        System.out.println("  Address:   " + orchestrator.address());
        System.out.println("  Endpoint:  " + orchestrator.endpoint());
        System.out.println("  Model:     " + orchestrator.model());
        System.out.println();

        // Step 3: Send task to orchestrator
        System.out.println("=== Step 3: Sending task to orchestrator ===");
        System.out.println("Task: " + DEMO_TASK);
        System.out.println();

        long start = System.currentTimeMillis();
        InvokeResponse response = invokeAgent(orchestrator.endpoint(), DEMO_TASK, "demo-client");
        long elapsed = System.currentTimeMillis() - start;

        // Step 4: Show the full execution trace
        System.out.println("=== Step 4: Execution trace ===");
        for (String step : response.steps()) {
            System.out.println("  " + step);
        }
        System.out.println();

        // Step 5: Final answer
        System.out.println("=== Final Answer (from " + response.agentEns() + ") ===");
        System.out.println(response.result());
        System.out.println();
        System.out.println("Completed in " + elapsed + "ms");

        resolver.shutdown();
    }

    private static InvokeResponse invokeAgent(String endpoint, String task, String callerEns)
            throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        ObjectMapper mapper = new ObjectMapper();

        String body = mapper.writeValueAsString(new InvokeRequest(task, callerEns));
        Request request = new Request.Builder()
                .url(endpoint + "/invoke")
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Agent call failed: " + response.code());
            }
            return mapper.readValue(response.body().string(), InvokeResponse.class);
        }
    }

    record InvokeRequest(String task, String callerEns) {}
    record InvokeResponse(String result, String agentEns, List<String> steps) {}
}
