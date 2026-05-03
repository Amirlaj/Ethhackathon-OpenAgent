package mesh.agent.invoke;

import java.util.List;

public record DelegationResult(String peerEns, String result, List<String> peerSteps) {}
