package mesh.agent.invoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import mesh.core.ens.AgentRegistry;
import mesh.core.manifest.AgentManifest;
import mesh.core.manifest.ManifestValidator;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Delegates subtasks to peer agents discovered via ENS.
 *
 * This is the key invariant: endpoint URLs are NEVER hardcoded.
 * Every delegation goes:
 *   1. AgentRegistry.findByCapability() → queries ENS subgraph
 *   2. Resolves peer manifest → reads endpoint from text record
 *   3. POSTs task to that endpoint
 *
 * If ENS resolution fails, the delegation fails — by design.
 */
@Service
public class DelegationService {

    private static final Logger log = Logger.getLogger(DelegationService.class.getName());

    private final AgentRegistry registry;
    private final ManifestValidator validator;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public DelegationService(AgentRegistry registry, ManifestValidator validator) {
        this.registry = registry;
        this.validator = validator;
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    /**
     * Find an agent by capability via ENS and delegate a subtask to it.
     *
     * @param capability  e.g. "web-search", "summarise", "code-execution"
     * @param subtask     the task to send to that agent
     * @param callerEns   this agent's ENS name (passed as caller identity)
     * @return the result from the peer agent
     * @throws DelegationException if no agent found or call fails
     */
    public DelegationResult delegateTo(String capability, String subtask, String callerEns)
            throws DelegationException {

        log.info("[Delegate] Looking for agent with capability: " + capability);

        // Filter out self to prevent recursive delegation loops
        Optional<AgentManifest> peerOpt = registry.findByCapability(capability)
                .filter(m -> !m.ensName().equals(callerEns));

        if (peerOpt.isEmpty()) {
            throw new DelegationException("No agent found with capability: " + capability);
        }

        AgentManifest peer = peerOpt.get();
        validator.validateOrThrow(peer);  // endpoint must be valid

        log.info("[Delegate] → " + peer.ensName() + " at " + peer.endpoint());

        return callPeer(peer, subtask, callerEns);
    }

    /**
     * Delegate directly to a named agent (by ENS name, not capability).
     * Used when the orchestrator knows exactly which agent it needs.
     */
    public DelegationResult delegateToAgent(AgentManifest peer, String subtask, String callerEns)
            throws DelegationException {
        validator.validateOrThrow(peer);
        log.info("[Delegate] Direct delegation → " + peer.ensName());
        return callPeer(peer, subtask, callerEns);
    }

    private DelegationResult callPeer(AgentManifest peer, String subtask, String callerEns)
            throws DelegationException {
        try {
            String requestBody = mapper.writeValueAsString(
                    new InvokeController.InvokeRequest(subtask, callerEns)
            );

            Request request = new Request.Builder()
                    .url(peer.endpoint() + "/invoke")
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(requestBody, MediaType.get("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new DelegationException(
                            "Peer " + peer.ensName() + " returned " + response.code());
                }

                InvokeController.InvokeResponse invokeResponse =
                        mapper.readValue(response.body().string(), InvokeController.InvokeResponse.class);

                log.info("[Delegate] ✓ Response from " + peer.ensName());
                return new DelegationResult(peer.ensName(), invokeResponse.result(), invokeResponse.steps());
            }

        } catch (IOException e) {
            throw new DelegationException("Failed to call peer " + peer.ensName() + ": " + e.getMessage(), e);
        }
    }
}
