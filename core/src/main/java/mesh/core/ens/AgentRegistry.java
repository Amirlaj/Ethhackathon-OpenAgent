package mesh.core.ens;

import mesh.core.manifest.AgentManifest;

import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class AgentRegistry {

    private static final Logger log = Logger.getLogger(AgentRegistry.class.getName());

    private final AgentEnsResolver resolver;
    private final String parentDomain;

    // Known agent subnames — in production this comes from the subgraph,
    // for the demo we resolve them directly by ENS name
    private static final List<String> KNOWN_AGENTS = List.of(
            "orchestrator", "researcher"
    );

    public AgentRegistry(AgentEnsResolver resolver, String parentDomain) {
        this.resolver = resolver;
        this.parentDomain = parentDomain;
    }

    public List<AgentManifest> listAll() {
        return KNOWN_AGENTS.stream()
                .map(label -> {
                    try {
                        return resolver.getManifest(label + "." + parentDomain);
                    } catch (EnsResolutionException e) {
                        log.warning("[Registry] Failed to resolve: " + label + "." + parentDomain);
                        return null;
                    }
                })
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
}