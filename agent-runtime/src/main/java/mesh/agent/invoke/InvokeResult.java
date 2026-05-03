package mesh.agent.invoke;

import java.util.List;

/**
 * Result of running an agent task.
 *
 * @param answer  The final text answer from the agent
 * @param steps   Log of all steps taken: ENS resolutions, tool calls, delegations
 */
public record InvokeResult(String answer, List<String> steps) {}
