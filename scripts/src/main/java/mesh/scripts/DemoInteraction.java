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
 * Run with two agents already started:
 *
 *   Terminal 1 (orchestrator):
 *     source .env && mvn spring-boot:run -pl agent-runtime
 *
 *   Terminal 2 (researcher):
 *     source .env.researcher && mvn spring-boot:run -pl agent-runtime
 *
 *   Terminal 3 (this script):
 *     mvn exec:java -pl scripts -Dexec.mainClass=mesh.scripts.DemoInteraction
 */
public class DemoInteraction {

    private static final String DEMO_TASK =
            "Research the latest advances in quantum computing from 2024 and write a concise summary.";

    public static void main(String[] args) throws Exception {
        String parentDomain = System.getenv("AGENT_PARENT_DOMAIN");
        if (parentDomain == null || parentDomain.isBlank()) {
            parentDomain = "agentmesh-dev.eth";
        }

        String orchestratorEns = "orchestrator." + parentDomain;

        System.out.println("======================================");
        System.out.println("   AgentMesh Demo — ENS Agent Coordination");
        System.out.println("======================================");
        System.out.println("Parent domain: " + parentDomain);
        System.out.println();

        AgentEnsResolver resolver = new AgentEnsResolver();
        AgentRegistry registry = new AgentRegistry(resolver, parentDomain);

        // Step 1: Show all registered agents
        System.out.println("=== Step 1: Discovering registered agents via ENS ===");
        List<AgentManifest> agents = registry.listAll();
        if (agents.isEmpty()) {
            System.out.println("No agents found via ENS. Trying direct localhost connection...");
            System.out.println();

            // Fallback: connect directly to localhost
            System.out.println("=== Fallback: Sending task directly to http://localhost:8081 ===");
            System.out.println("Task: " + DEMO_TASK);
            System.out.println();

            long start = System.currentTimeMillis();
            InvokeResponse response = invokeAgent("http://localhost:8081", DEMO_TASK, "demo-client");
            long elapsed = System.currentTimeMillis() - start;

            printResponse(response, elapsed);
            resolver.shutdown();
            return;
        }

        System.out.println("Found " + agents.size() + " registered agents:");
        for (AgentManifest agent : agents) {
            System.out.println("  - " + agent.ensName()
                    + " [" + String.join(", ", agent.capabilities()) + "]"
                    + " -> " + agent.endpoint());
        }
        System.out.println();

        // Step 2: Resolve orchestrator identity
        System.out.println("=== Step 2: Resolving orchestrator identity from ENS ===");
        AgentManifest orchestrator = resolver.getManifest(orchestratorEns);
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

        printResponse(response, elapsed);
        resolver.shutdown();
    }

    private static void printResponse(InvokeResponse response, long elapsed) {
        System.out.println("=== Execution trace ===");
        for (String step : response.steps()) {
            System.out.println("  " + step);
        }
        System.out.println();

        System.out.println("=== Final Answer (from " + response.agentEns() + ") ===");
        System.out.println(response.result());
        System.out.println();
        System.out.println("Completed in " + elapsed + "ms");
    }

    private static InvokeResponse invokeAgent(String endpoint, String task, String callerEns)
            throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        ObjectMapper mapper = new ObjectMapper();

        String body = mapper.writeValueAsString(new InvokeRequest(task, callerEns));
        Request request = new Request.Builder()
                .url(endpoint + "/invoke")
                .post(RequestBody.create(body, MediaType.get("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Agent call failed: " + response.code()
                        + " " + response.body().string());
            }
            return mapper.readValue(response.body().string(), InvokeResponse.class);
        }
    }

    record InvokeRequest(String task, String callerEns) {}
    record InvokeResponse(String result, String agentEns, List<String> steps) {}
}
