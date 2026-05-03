package mesh.agent.identity;

import jakarta.annotation.PostConstruct;
import mesh.core.ens.AgentEnsResolver;
import mesh.core.ens.EnsResolutionException;
import mesh.core.manifest.AgentManifest;
import mesh.core.manifest.ManifestValidator;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

/**
 * Verifies this agent's ENS identity on startup.
 *
 * On PostConstruct:
 *   1. Resolves own ENS name → fetches manifest from text records
 *   2. Validates the manifest (endpoint, capabilities, model, etc.)
 *   3. Logs the resolved identity for transparency
 *
 * If the manifest is missing or invalid, the agent refuses to start.
 * This enforces the invariant: an AgentMesh agent without a valid ENS identity cannot serve requests.
 */
@Service
public class AgentIdentityService {

    private static final Logger log = Logger.getLogger(AgentIdentityService.class.getName());

    private final String ensName;
    private final AgentEnsResolver resolver;
    private final ManifestValidator validator;

    private AgentManifest identity;

    public AgentIdentityService(String ensName, AgentEnsResolver resolver, ManifestValidator validator) {
        this.ensName = ensName;
        this.resolver = resolver;
        this.validator = validator;
    }

    @PostConstruct
    public void verifyIdentity() throws EnsResolutionException {
        log.info("╔══════════════════════════════════════════╗");
        log.info("║        AgentMesh Identity Verification   ║");
        log.info("╚══════════════════════════════════════════╝");
        log.info("[Identity] ENS name: " + ensName);

        try {
            this.identity = resolver.getManifest(ensName);
            validator.validateOrThrow(identity);

            log.info("[Identity] ✓ Resolved address:    " + identity.address());
            log.info("[Identity] ✓ Capabilities:        " + identity.capabilities());
            log.info("[Identity] ✓ Endpoint:            " + identity.endpoint());
            log.info("[Identity] ✓ Model:               " + identity.model());
            log.info("[Identity] ✓ Version:             " + identity.version());
            log.info("[Identity] ✓ Description:         " + identity.description());
            log.info("[Identity] Agent identity verified. Ready to serve requests.");

        } catch (EnsResolutionException e) {
            log.severe("[Identity] ✗ Failed to resolve ENS manifest for: " + ensName);
            log.severe("[Identity] Make sure the agent is registered via: mvn exec:java -pl scripts -Dexec.mainClass=mesh.scripts.RegisterAgent");
            throw e;
        }
    }

    /**
     * Returns this agent's verified manifest.
     * Only available after @PostConstruct completes.
     */
    public AgentManifest getIdentity() {
        if (identity == null) throw new IllegalStateException("Identity not yet verified");
        return identity;
    }
}
