package mesh.scripts;

import mesh.core.ens.AgentEnsResolver;
import mesh.core.ens.TextRecordClient;
import mesh.core.manifest.AgentManifest;
import mesh.core.manifest.ManifestValidator;

import java.util.List;

/**
 * CLI: Register a new agent as an ENS subname under your parent domain.
 *
 * Usage:
 *   mvn exec:java -pl scripts -Dexec.mainClass=mesh.scripts.RegisterAgent \
 *     -Dexec.args="researcher http://localhost:8082 web-search,summarise 0xYOUR_ADDRESS"
 *
 * Required env vars:
 *   NAMESTONE_API_KEY       — from https://namestone.xyz
 *   PRIVATE_KEY             — wallet private key (for signing text record updates)
 *   AGENT_PARENT_DOMAIN     — e.g. agentmesh-dev.eth (defaults to agentmesh.eth)
 */
public class RegisterAgent {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: RegisterAgent <label> <endpoint> <capabilities-csv> <owner-address>");
            System.err.println("  e.g: researcher http://localhost:8082 web-search,summarise 0xABC...");
            System.exit(1);
        }

        String parentDomain = System.getenv("AGENT_PARENT_DOMAIN");
        if (parentDomain == null || parentDomain.isBlank()) {
            parentDomain = "agentmesh-dev.eth";
        }

        String label        = args[0];  // e.g. "researcher"
        String endpoint     = args[1];  // e.g. "http://localhost:8082"
        String capsCsv      = args[2];  // e.g. "web-search,summarise"
        String ownerAddress = args[3];  // e.g. "0xd8dA6BF..."

        String ensName = label + "." + parentDomain;
        List<String> capabilities = List.of(capsCsv.split(","));

        System.out.println("======================================");
        System.out.println("   AgentMesh — Register Agent         ");
        System.out.println("======================================");
        System.out.println("Parent domain: " + parentDomain);
        System.out.println("ENS name:      " + ensName);
        System.out.println("Endpoint:      " + endpoint);
        System.out.println("Capabilities:  " + capabilities);
        System.out.println("Owner:         " + ownerAddress);
        System.out.println();

        // Build the manifest
        AgentManifest manifest = AgentManifest.builder()
                .ensName(ensName)
                .address(ownerAddress)
                .capabilities(capabilities)
                .endpoint(endpoint)
                .model("llama-3.1-8b-instant")
                .version("1.0.0")
                .description("AgentMesh agent: " + label)
                .build();

        // Validate before touching ENS
        new ManifestValidator().validateOrThrow(manifest);
        System.out.println("OK Manifest validated");

        String namestoneKey = System.getenv("NAMESTONE_API_KEY");
        String privateKey   = System.getenv("PRIVATE_KEY");

        if (namestoneKey == null || namestoneKey.isBlank()) {
            throw new IllegalStateException("NAMESTONE_API_KEY env var not set. Get one at https://namestone.xyz");
        }

        // Step 1: Register subname via Namestone
        System.out.println("-> Registering subname via Namestone...");
        TextRecordClient textClient = new TextRecordClient(AgentEnsResolver.BASE_SEPOLIA_RPC, namestoneKey);
        textClient.registerSubname(label, parentDomain, ownerAddress);
        System.out.println("OK Subname registered: " + ensName);

        // Step 2: Set text records
        System.out.println("-> Setting ENS text records...");
        AgentEnsResolver resolver = new AgentEnsResolver();
        resolver.setManifest(ensName, manifest, privateKey);
        System.out.println("OK Text records set");

        // Step 3: Verify by reading back
        System.out.println("-> Verifying registration...");
        AgentManifest resolved = resolver.getManifest(ensName);
        System.out.println("OK Verified: " + resolved.ensName() + " -> " + resolved.endpoint());
        System.out.println();
        System.out.println("Agent registered successfully!");
        System.out.println("Start it with: ENS_NAME=" + ensName + " SERVER_PORT=8082 mvn spring-boot:run -pl agent-runtime");

        resolver.shutdown();
    }
}
