package mesh.agent.invoke;

import mesh.agent.identity.AgentIdentityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * The single HTTP endpoint exposed by every AgentMesh agent.
 *
 * POST /invoke
 *   Request:  { "task": "...", "callerEns": "orchestrator.agentmesh.eth" }
 *   Response: { "result": "...", "agentEns": "researcher.agentmesh.eth", "steps": [...] }
 *
 * GET /identity
 *   Returns the agent's resolved ENS manifest (useful for the demo + frontend)
 */
@RestController
public class InvokeController {

    private final InvokeService invokeService;
    private final AgentIdentityService identityService;

    public InvokeController(InvokeService invokeService, AgentIdentityService identityService) {
        this.invokeService = invokeService;
        this.identityService = identityService;
    }

    public record InvokeRequest(String task, String callerEns) {}

    public record InvokeResponse(
            String result,
            String agentEns,
            List<String> steps   // log of ENS resolutions + tool calls made
    ) {}

    @PostMapping("/invoke")
    public ResponseEntity<InvokeResponse> invoke(@RequestBody InvokeRequest request) {
        if (request.task() == null || request.task().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            InvokeResult result = invokeService.run(request.task(), request.callerEns());

            return ResponseEntity.ok(new InvokeResponse(
                    result.answer(),
                    identityService.getIdentity().ensName(),
                    result.steps()
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(new InvokeResponse(
                    "Error: " + e.getMessage(),
                    identityService.getIdentity().ensName(),
                    List.of("[ERROR] " + e.getClass().getSimpleName() + ": " + e.getMessage())
            ));
        }
    }

    @GetMapping("/identity")
    public ResponseEntity<?> identity() {
        return ResponseEntity.ok(identityService.getIdentity());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK — " + identityService.getIdentity().ensName());
    }
}
