package mesh.agent.identity;

import jakarta.annotation.PostConstruct;
import mesh.core.ens.AgentEnsResolver;
import mesh.core.manifest.AgentManifest;
import mesh.core.manifest.ManifestValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

/**
 * Verifies this agent's ENS identity on startup.
 *
 * On PostConstruct:
 *   1. Tries to resolve own ENS name -> fetch manifest from text records
 *   2. Validates the manifest (endpoint, capabilities, model, etc.)
 *   3. If ENS fails, falls back to local manifest from env vars
 *
 * This allows the agent to start and serve requests locally even without
 * ENS registration. Register on ENS for full mesh discovery.
 */
@Service
public class AgentIdentityService {

    private static final Logger log = Logger.getLogger(AgentIdentityService.class.getName());

    private final String ensName;
    private final AgentEnsResolver resolver;
    private final ManifestValidator validator;

    @Value("${SERVER_PORT:8082}")
    private int serverPort;

    @Value("${AGENT_CAPABILITIES:orchestrate,delegate}")
    private String agentCapabilities;

    @Value("${AGENT_MODEL:llama-3.1-8b-instant}")
    private String agentModel;

    private AgentManifest identity;
    private boolean resolvedViaEns = false;

    public AgentIdentityService(String ensName, AgentEnsResolver resolver, ManifestValidator validator) {
        this.ensName = ensName;
        this.resolver = resolver;
        this.validator = validator;
    }

    @PostConstruct
    public void verifyIdentity() {
        log.info("======================================");
        log.info("   AgentMesh Identity Verification    ");
        log.info("======================================");
        log.info("[Identity] ENS name: " + ensName);

        try {
            this.identity = resolver.getManifest(ensName);
            validator.validateOrThrow(identity);
            this.resolvedViaEns = true;

            log.info("[Identity] OK Resolved address:    " + identity.address());
            log.info("[Identity] OK Capabilities:        " + identity.capabilities());
            log.info("[Identity] OK Endpoint:            " + identity.endpoint());
            log.info("[Identity] OK Model:               " + identity.model());
            log.info("[Identity] OK Version:             " + identity.version());
            log.info("[Identity] OK Description:         " + identity.description());
            log.info("[Identity] Agent identity verified via ENS. Ready to serve requests.");

        } catch (Exception e) {
            log.warning("[Identity] Could not resolve ENS manifest for: " + ensName);
            log.warning("[Identity]   Reason: " + e.getMessage());
            log.warning("[Identity]   Falling back to local manifest from env vars.");

            List<String> capabilities = List.of(agentCapabilities.split(","));
            this.identity = AgentManifest.builder()
                    .ensName(ensName)
                    .address("0x0000000000000000000000000000000000000000")
                    .capabilities(capabilities)
                    .endpoint("http://localhost:" + serverPort)
                    .model(agentModel)
                    .version("1.0.0")
                    .description("AgentMesh agent: " + ensName + " (local fallback)")
                    .build();

            log.info("[Identity] Local fallback manifest created:");
            log.info("[Identity]   Capabilities: " + identity.capabilities());
            log.info("[Identity]   Endpoint:     " + identity.endpoint());
            log.info("[Identity]   Model:        " + identity.model());
            log.info("[Identity] Agent running in LOCAL mode. Register on ENS for full mesh:");
            log.info("[Identity]   mvn exec:java -pl scripts -Dexec.mainClass=mesh.scripts.RegisterAgent");
        }
    }

    public AgentManifest getIdentity() {
        if (identity == null) throw new IllegalStateException("Identity not yet verified");
        return identity;
    }

    public boolean isResolvedViaEns() {
        return resolvedViaEns;
    }
}
