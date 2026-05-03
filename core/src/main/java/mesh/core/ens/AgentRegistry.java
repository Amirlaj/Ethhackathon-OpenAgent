package mesh.core.ens;

import mesh.core.manifest.AgentManifest;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class AgentRegistry {

    private static final Logger log = Logger.getLogger(AgentRegistry.class.getName());

    private final AgentEnsResolver resolver;
    private final String parentDomain;
    private final boolean devMode;

    // Known agent subnames — in production this comes from the subgraph,
    // for the demo we resolve them directly by ENS name
    private static final List<String> KNOWN_AGENTS = List.of(
            "orchestrator", "researcher"
    );

    // Default dev-mode port mapping
    private static final java.util.Map<String, Integer> DEV_PORTS = java.util.Map.of(
            "orchestrator", 8081,
            "researcher", 8082
    );

    // Default dev-mode capabilities (include common synonyms the LLM may use)
    private static final java.util.Map<String, List<String>> DEV_CAPABILITIES = java.util.Map.of(
            "orchestrator", List.of("orchestrate", "delegate"),
            "researcher", List.of("web-search", "summarise", "summarize", "summary",
                    "text-summarise", "text-summarize", "parse-text",
                    "natural-language-processing", "research")
    );

    public AgentRegistry(AgentEnsResolver resolver, String parentDomain) {
        this(resolver, parentDomain, false);
    }

    public AgentRegistry(AgentEnsResolver resolver, String parentDomain, boolean devMode) {
        this.resolver = resolver;
        this.parentDomain = parentDomain;
        this.devMode = devMode;
    }

    public List<AgentManifest> listAll() {
        return KNOWN_AGENTS.stream()
                .map(label -> resolveOrFallback(label))
                .filter(m -> m != null && m.endpoint() != null && !m.endpoint().isBlank())
                .toList();
    }

    public Optional<AgentManifest> findByCapability(String capability) {
        log.info("[Registry] Searching for agent with capability: " + capability);
        return listAll().stream()
                .filter(m -> m.hasCapability(capability))
                .findFirst()
                .map(m -> {
                    log.info("[Registry] Found: " + m.ensName() + " for capability=" + capability);
                    return m;
                });
    }

    private AgentManifest resolveOrFallback(String label) {
        String ensName = label + "." + parentDomain;
        try {
            return resolver.getManifest(ensName);
        } catch (EnsResolutionException e) {
            if (devMode) {
                log.info("[Registry] ENS resolution failed for " + ensName + ", using dev fallback");
                int port = DEV_PORTS.getOrDefault(label, 8082);
                List<String> caps = DEV_CAPABILITIES.getOrDefault(label, List.of());
                return AgentManifest.builder()
                        .ensName(ensName)
                        .address("0x0000000000000000000000000000000000000000")
                        .capabilities(caps)
                        .endpoint("http://localhost:" + port)
                        .model("llama-3.1-8b-instant")
                        .version("1.0.0")
                        .description("AgentMesh agent: " + label + " (dev fallback)")
                        .build();
            }
            log.warning("[Registry] Failed to resolve: " + ensName + ": " + e.getMessage());
            return null;
        }
    }
}
