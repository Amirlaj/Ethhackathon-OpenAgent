package mesh.agent.invoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import mesh.agent.identity.AgentIdentityService;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Service
public class InvokeService {

    private static final Logger log = Logger.getLogger(InvokeService.class.getName());
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.1-8b-instant";
    private static final MediaType JSON_MEDIA = MediaType.get("application/json");

    private final String groqApiKey;
    private final String searchApiKey;
    private final DelegationService delegation;
    private final AgentIdentityService identityService;
    private final OkHttpClient http;
    private final ObjectMapper mapper;

    public InvokeService(
            @Value("${GROQ_API_KEY:}") String groqApiKey,
            @Value("${SEARCH_API_KEY:}") String searchApiKey,
            DelegationService delegation,
            AgentIdentityService identityService
    ) {
        this.groqApiKey = groqApiKey;
        this.searchApiKey = searchApiKey;
        this.delegation = delegation;
        this.identityService = identityService;
        this.http = new OkHttpClient.Builder()
                .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    public InvokeResult run(String task, String callerEns) {
        List<String> steps = new ArrayList<>();
        String myEns = identityService.getIdentity().ensName();

        steps.add("[" + myEns + "] Received task: " + task);
        if (callerEns != null) steps.add("[" + myEns + "] Called by: " + callerEns);

        // Check API key
        if (groqApiKey == null || groqApiKey.isBlank()) {
            steps.add("[ERROR] GROQ_API_KEY is not set. Cannot process task.");
            return new InvokeResult("Error: GROQ_API_KEY environment variable is not configured.", steps);
        }

        // delegate tool
        ObjectNode delegateParams = mapper.createObjectNode().put("type", "object");
        ObjectNode delegateProps = mapper.createObjectNode();
        delegateProps.set("capability", mapper.createObjectNode()
                .put("type", "string")
                .put("description", "The capability needed, e.g. web-search, summarise"));
        delegateProps.set("subtask", mapper.createObjectNode()
                .put("type", "string")
                .put("description", "The task to send to that agent"));
        delegateParams.set("properties", delegateProps);
        delegateParams.set("required", mapper.createArrayNode().add("capability").add("subtask"));

        // webSearch tool
        ObjectNode searchParams = mapper.createObjectNode().put("type", "object");
        ObjectNode searchProps = mapper.createObjectNode();
        searchProps.set("query", mapper.createObjectNode()
                .put("type", "string")
                .put("description", "Search query string"));
        searchParams.set("properties", searchProps);
        searchParams.set("required", mapper.createArrayNode().add("query"));

        ArrayNode tools = mapper.createArrayNode();
        tools.add(buildTool("delegate",
                "Delegate a subtask to another AI agent discovered via ENS. " +
                        "Use when you need a capability you don't have.", delegateParams));
        tools.add(buildTool("webSearch",
                "Search the web for current information on a topic.", searchParams));

        String systemPrompt = String.format(
                "You are %s. Your only job is to use tools — never write tool calls as text.\n" +
                        "To research anything, call the delegate tool with capability='web-search'.\n" +
                        "After you get the result back, write your final answer as plain text with no tool calls.",
                myEns
        );

        ArrayNode messages = mapper.createArrayNode();
        messages.add(mapper.createObjectNode()
                .put("role", "system")
                .put("content", systemPrompt));
        messages.add(mapper.createObjectNode()
                .put("role", "user")
                .put("content", task));

        String finalAnswer = null;

        for (int iteration = 0; iteration < 10 && finalAnswer == null; iteration++) {
            JsonNode response = callGroq(messages, tools, iteration == 0);
            JsonNode message = response.path("choices").path(0).path("message");
            String finishReason = response.path("choices").path(0).path("finish_reason").asText();

            JsonNode toolCalls = message.path("tool_calls");

            if (finishReason.equals("stop") || !toolCalls.isArray() || toolCalls.isEmpty()) {
                finalAnswer = message.path("content").asText("No response.");
                break;
            }

            messages.add(message.deepCopy());

            for (JsonNode toolCall : toolCalls) {
                String callId   = toolCall.path("id").asText();
                String funcName = toolCall.path("function").path("name").asText();
                String argsJson = toolCall.path("function").path("arguments").asText();

                JsonNode args = parseArgs(argsJson);
                String result = executeToolCall(funcName, args, myEns, steps);

                messages.add(mapper.createObjectNode()
                        .put("role", "tool")
                        .put("tool_call_id", callId)
                        .put("content", result));
            }
        }

        if (finalAnswer == null) finalAnswer = "Agent reached iteration limit.";
        steps.add("[" + myEns + "] Task complete.");
        return new InvokeResult(finalAnswer, steps);
    }

    private JsonNode callGroq(ArrayNode messages, ArrayNode tools, boolean forceTool) {
        int maxRetries = 5;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                ObjectNode body = mapper.createObjectNode()
                        .put("model", MODEL)
                        .put("max_tokens", 1024);
                body.set("messages", messages);
                body.set("tools", tools);
                body.put("tool_choice", forceTool ? "required" : "auto");
                body.put("parallel_tool_calls", false);

                Request request = new Request.Builder()
                        .url(GROQ_URL)
                        .addHeader("Authorization", "Bearer " + groqApiKey)
                        .post(RequestBody.create(mapper.writeValueAsString(body), JSON_MEDIA))
                        .build();

                try (Response response = http.newCall(request).execute()) {
                    String responseBody = response.body().string();
                    if (response.code() == 429) {
                        int waitSeconds = 10 + (attempt * 5);
                        log.warning("[Groq] Rate limited, waiting " + waitSeconds + "s (attempt " + (attempt+1) + ")");
                        Thread.sleep(waitSeconds * 1000L);
                        continue;
                    }
                    if (!response.isSuccessful()) {
                        throw new RuntimeException("Groq API error " + response.code() + ": " + responseBody);
                    }
                    return mapper.readTree(responseBody);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("Groq call failed: " + e.getMessage(), e);
            }
        }
        throw new RuntimeException("Groq rate limit exceeded after " + maxRetries + " retries");
    }

    private String executeToolCall(String name, JsonNode args, String myEns, List<String> steps) {
        try {
            return switch (name) {
                case "delegate" -> {
                    String capability = args.path("capability").asText();
                    String subtask    = args.path("subtask").asText();
                    steps.add("[" + myEns + "] Delegating capability='" + capability + "' via ENS");
                    DelegationResult result = delegation.delegateTo(capability, subtask, myEns);
                    steps.add("[ENS] Resolved -> " + result.peerEns());
                    steps.addAll(result.peerSteps());
                    yield result.result();
                }
                case "webSearch" -> {
                    String query = args.path("query").asText();
                    steps.add("[" + myEns + "] Web search: " + query);
                    yield performWebSearch(query);
                }
                default -> "Unknown tool: " + name;
            };
        } catch (Exception e) {
            String err = "Tool call failed (" + name + "): " + e.getMessage();
            steps.add("[ERROR] " + err);
            return err;
        }
    }

    private JsonNode buildTool(String name, String description, ObjectNode parameters) {
        return mapper.createObjectNode()
                .put("type", "function")
                .<ObjectNode>set("function", mapper.createObjectNode()
                        .put("name", name)
                        .put("description", description)
                        .set("parameters", parameters));
    }

    private JsonNode parseArgs(String argsJson) {
        try {
            return mapper.readTree(argsJson);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private String performWebSearch(String query) {
        if (searchApiKey == null || searchApiKey.isBlank()) {
            return "Web search unavailable: SEARCH_API_KEY not configured.";
        }

        try {
            // Use ObjectMapper to safely build JSON body (no injection risk)
            ObjectNode searchBody = mapper.createObjectNode().put("q", query);

            Request request = new Request.Builder()
                    .url("https://google.serper.dev/search")
                    .addHeader("X-API-KEY", searchApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(mapper.writeValueAsString(searchBody), JSON_MEDIA))
                    .build();

            try (Response response = http.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return "Search failed: " + response.code();
                }
                JsonNode json = mapper.readTree(response.body().string());
                StringBuilder results = new StringBuilder();
                JsonNode organic = json.path("organic");
                int count = 0;
                for (JsonNode result : organic) {
                    if (count >= 3) break;
                    results.append("Title: ").append(result.path("title").asText()).append("\n");
                    results.append("Snippet: ").append(result.path("snippet").asText()).append("\n\n");
                    count++;
                }
                return results.length() > 0 ? results.toString() : "No results found for: " + query;
            }
        } catch (Exception e) {
            return "Search error: " + e.getMessage();
        }
    }
}
