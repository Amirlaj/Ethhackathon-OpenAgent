package mesh.app.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import mesh.core.ens.AgentRegistry;
import mesh.core.manifest.AgentManifest;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Web UI for the AgentMesh registry explorer and agent invoker.
 *
 * GET  /         → Homepage with registered agents + invoke form
 * POST /invoke   → Proxy a task to an agent and show the result
 * GET  /api/agents → JSON list of registered agents (for HTMX)
 */
@Controller
public class AppController {

    private static final Logger log = Logger.getLogger(AppController.class.getName());

    private final AgentRegistry registry;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;

    public AppController(AgentRegistry registry) {
        this.registry = registry;
        this.httpClient = new OkHttpClient.Builder()
                .callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    @GetMapping("/")
    public String index(Model model) {
        List<AgentManifest> agents = registry.listAll();
        model.addAttribute("agents", agents);
        return "index";
    }

    @PostMapping("/invoke")
    public String invoke(
            @RequestParam String endpoint,
            @RequestParam String task,
            Model model
    ) {
        List<AgentManifest> agents = registry.listAll();
        model.addAttribute("agents", agents);
        model.addAttribute("selectedEndpoint", endpoint);
        model.addAttribute("task", task);

        try {
            InvokeRequest req = new InvokeRequest(task, "app-ui");
            String body = mapper.writeValueAsString(req);

            Request httpReq = new Request.Builder()
                    .url(endpoint + "/invoke")
                    .post(okhttp3.RequestBody.create(body, MediaType.get("application/json")))
                    .build();

            log.info("[App] Invoking " + endpoint + " with task: " + task);

            try (Response response = httpClient.newCall(httpReq).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    model.addAttribute("error", "Agent returned HTTP " + response.code() + ": " + responseBody);
                    return "index";
                }

                InvokeResponse result = mapper.readValue(responseBody, InvokeResponse.class);
                model.addAttribute("result", result);
            }

        } catch (Exception e) {
            log.warning("[App] Invoke failed: " + e.getMessage());
            model.addAttribute("error", "Failed to reach agent: " + e.getMessage());
        }

        return "index";
    }

    @GetMapping("/api/agents")
    @ResponseBody
    public List<AgentManifest> apiAgents() {
        return registry.listAll();
    }

    public record InvokeRequest(String task, String callerEns) {}
    public record InvokeResponse(String result, String agentEns, List<String> steps) {}
}
